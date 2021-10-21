package nabu.misc.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.ServiceUnavailableException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.git.GitRepository;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.file.FileDirectory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceException;

// if we are releasing on the same server, we clone the directory of the project itself
// we can't really cache the git repositories because the nabu server might be clustered (though they should use a shared drive then)
// in the future, if recreating the git repositories proves to be too much overhead, we _can_ cache them and simply update them using server-to-server communication

/**
 * Ideally we don't want to put too many restrictions on the development side. Do you want to work with a "develop" branch and periodically merge to master?
 * Do you want to work with separate development branches over time that are squash committed to the master?
 * Do you want to work with feature branching (if possible)?
 * 
 * Additionally, we can't switch branches too often, cause this has to happen in the actual directory. For example we have to checkout the master to merge the develop branch
 * However, during that time when the server is looking at the master branch without having merged the development branch, weird errors might exist (files not found etc)
 * 
 * @author alex
 *
 */
@WebService
public class Services {
	
	private static String masterBranch = System.getProperty("git.master", "master");
	private static String devBranch = System.getProperty("git.dev", "develop");
	private static String remote = System.getProperty("git.remote", "origin");

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	// you can standardize a particular project
	// or you can standardize the whole server (all projects)
	public void standardize(String projectId) {
		EAIResourceRepository repository = EAIResourceRepository.getInstance();
		RepositoryEntry root = repository.getRoot();
		for (Entry child : root) {
			try {
				// it has to be resource-backed
				if (child instanceof ResourceEntry && EAIRepositoryUtils.isProject(child)) {
					// and more specifically file system
					ResourceContainer<?> container = ((ResourceEntry) child).getContainer();
					if (container instanceof FileDirectory) {
						File file = ((FileDirectory) container).getFile();
						// if there is no git folder yet, initiate one
						if (!new File(file, ".git").exists()) {
							logger.info("Found project without git repository, creating repository for: " + file.getName());
							Git.init().setDirectory(file).call();
						}
						Git git = Git.open(file);
						// if nothing has been committed yet, we add a marker file and commit only that to make sure the master branch exists
						if (!git.log().call().iterator().hasNext()) {
							new FileOutputStream(new File(file, ".git-marker")).close();
						}
					}
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	// commit an entire project or a particular folder
	// we use the metadata of the calling user
	// we commit on the current branch (whichever that is) and push it to the origin (if any)
	// this could be the master branch
	// any deployment actions that reside within the id are run before committing
	public void commit(@WebParam(name = "id") String id, @WebParam(name = "message") String message) throws IllegalStateException, GitAPIException, FileNotFoundException, IOException, ServiceException, InterruptedException, ExecutionException {
		commitInternal(id, message, true);
	}

	private Git commitInternal(String id, String message, boolean push) throws GitAPIException, IOException, FileNotFoundException, NoFilepatternException, AbortedByHookException, ConcurrentRefUpdateException, NoHeadException, NoMessageException, ServiceUnavailableException, UnmergedPathsException, WrongRepositoryStateException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, ServiceException, InterruptedException, ExecutionException, InvalidRemoteException, TransportException {
		Token token = ServiceRuntime.getRuntime().getExecutionContext().getSecurityContext().getToken();
		
		EAIResourceRepository repository = EAIResourceRepository.getInstance();
		Entry entry = repository.getEntry(id);
		if (!(entry instanceof ResourceEntry)) {
			throw new IllegalArgumentException("Can not commit '" + id + "', it is not resource-based");
		}
		ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
		if (!(container instanceof FileDirectory)) {
			throw new IllegalArgumentException("Can not commit '" + id + "', it is not file-based");
		}
		// we need to figure out the project it belongs to
		Entry project = entry;
		while (project != null && !EAIRepositoryUtils.isProject(project)) {
			project = project.getParent();
		}
		if (project == null) {
			throw new IllegalArgumentException("Could not find the project for the entry: " + entry.getId());
		}
		if (!(project instanceof ResourceEntry)) {
			throw new IllegalArgumentException("Can not commit '" + id + "', the project is not resource-based");
		}
		container = ((ResourceEntry) project).getContainer();
		if (!(container instanceof FileDirectory)) {
			throw new IllegalArgumentException("Can not commit '" + id + "', the project is not file-based");
		}
		Git git;
		File file = ((FileDirectory) container).getFile();
		if (!new File(file, ".git").exists()) {
			logger.info("Found project without git repository, creating repository for: " + file.getName());
			Git.init().setDirectory(file).call();
			
			git = Git.open(new File(file, ".git"));
			// we commit an empty marker file to make sure a master branch can exist
			new FileOutputStream(new File(file, ".git-marker")).close();
			// add the marker file for commit
			git.add().addFilepattern(".git-marker").call();
			// create the initial commit, this should create a master branch
			git.commit().setMessage("Initial").call();
			// branch this into a develop branch
			git.branchCreate().setName("develop").call();
			// and check it out
			git.checkout().setName("develop").call();
		}
		else {
			git = Git.open(new File(file, ".git"));
		}
		// run deployment actions within the entry
		for (DeploymentAction action : repository.getArtifacts(DeploymentAction.class)) {
			if (action.getId().startsWith(entry.getId() + ".") || action.getId().equals(entry.getId())) {
				action.runSource();
			}
		}
		
		String path = entry.getId().equals(project.getId())
			? "."
			// it must reside in the project, so we substract the name of the project (and the .)
			: entry.getId().substring(project.getId().length() + 1).replace(".", "/");

		Status status = git.status().addPath(path).call();
		// only commit if relevant
		if (status != null && status.hasUncommittedChanges()) {
			AddCommand add = git.add();
			add.addFilepattern(path);
			add.call();
			
			// commit it
			git.commit()
				.setAll(true)
				.setCommitter(new PersonIdent(token == null ? "anonymous" : token.getName(), token == null ? "$anonymous" : token.getName()))
				.setMessage(message == null ? "No message" : message)
				.call();
			
			if (push) {
				// push it remotely if possible
				List<RemoteConfig> call = git.remoteList().call();
				for (RemoteConfig config : call) {
					if (remote.equals(config.getName())) {
						git.push().setRemote(remote).call();
					}
				}
			}
		}
		
		return git;
	}
	
	// we release an entire project
	// we first run the deployment actions (source)
	// then do a final commit (see above)
	// then merge the dev branch into the master
	// then push the master to the origin (if available)
	// this can start a remote build
	public void release(@WebParam(name = "projectId") String projectId, @WebParam(name = "message") String message) throws IllegalStateException, FileNotFoundException, GitAPIException, IOException, ServiceException, InterruptedException, ExecutionException {
		Token token = ServiceRuntime.getRuntime().getExecutionContext().getSecurityContext().getToken();
		
		Entry entry = EAIResourceRepository.getInstance().getEntry(projectId);
		if (!EAIRepositoryUtils.isProject(entry)) {
			throw new IllegalArgumentException("Not a valid project id: " + projectId);
		}
		// first we run a commit cycle, we don't push yet, we first want to tag
		Git git = commitInternal(projectId, "Commit for release", false);
		
		// then we tag it
		// first we check what the highest version was that we tagged before
		List<Ref> tags = git.tagList().call();
		int highestVersion = 0;
		for (Ref tag : tags) {
			String name = tag.getName().replaceAll("^.*/([^/]+$)", "$1");
			if (name.matches("^v[0-9]+$")) {
				int tagVersion = Integer.parseInt(name.substring(1));
				if (tagVersion > highestVersion) {
					highestVersion = tagVersion;
				}
			}
		}
		// create the tag
		git.tag()
			.setName("v" + (highestVersion + 1))
			.setMessage(message)
			.setTagger(new PersonIdent(token == null ? "anonymous" : token.getName(), token == null ? "$anonymous" : token.getName()))
			.call();
		
		// push it remotely if possible
		List<RemoteConfig> call = git.remoteList().call();
		for (RemoteConfig config : call) {
			if (remote.equals(config.getName())) {
				// we also push ze tags!
				git.push().setPushTags().setRemote(remote).call();
			}
		}
	}
	
	// we can "build" a project
	// this will check for a folder in a certain directory
	// if it exists, it will work from there
	// if it doesn't exist yet, it will check the repo for a project by that name and start from there.
	// otherwise, it will throw an exception
	public void build(String id) {
		
	}
	
}
