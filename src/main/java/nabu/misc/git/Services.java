package nabu.misc.git;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.git.GitRelease;
import be.nabu.eai.module.git.GitRepository;
import be.nabu.eai.module.git.merge.MergeResult;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.file.FileDirectory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceException;
import nabu.misc.git.types.GitBuild;

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
	
	// commit an entire project or a particular folder
	// we use the metadata of the calling user
	// we commit on the current branch (whichever that is) and push it to the origin (if any)
	// this could be the master branch
	// any deployment actions that reside within the id are run before committing
	public void commit(@NotNull @WebParam(name = "id") String id, @WebParam(name = "message") String message, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws IllegalStateException, GitAPIException, FileNotFoundException, IOException, ServiceException, InterruptedException, ExecutionException {
		commitInternal(id, message, true, username, password);
	}

	private Git commitInternal(String id, String message, boolean push, String username, String password) throws GitAPIException, IOException, FileNotFoundException, NoFilepatternException, AbortedByHookException, ConcurrentRefUpdateException, NoHeadException, NoMessageException, ServiceUnavailableException, UnmergedPathsException, WrongRepositoryStateException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, ServiceException, InterruptedException, ExecutionException, InvalidRemoteException, TransportException {
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
			// we've stepped away from the "mandatory" develop branch, leaving more flexibility
//			// we commit an empty marker file to make sure a master branch can exist
//			new FileOutputStream(new File(file, ".git-marker")).close();
//			// add the marker file for commit
//			git.add().addFilepattern(".git-marker").call();
//			// create the initial commit, this should create a master branch
//			git.commit().setMessage("Initial").call();
//			// branch this into a develop branch
//			git.branchCreate().setName("develop").call();
//			// and check it out
//			git.checkout().setName("develop").call();
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

		AddCommand add = git.add();
		add.addFilepattern(path);
		add.call();
		
		// does not seem to work...?
//		Status status = git.status().addPath(path).call();
//		// only commit if relevant
//		if (status != null && status.hasUncommittedChanges()) {
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
					authenticate(git.push(), username, password).setRemote(remote).call();
				}
			}
		}
		
		return git;
	}
	
	private <T extends TransportCommand<?, ?>> T authenticate(T command, String username, String password) {
		if (username != null) {
			command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
		}
		return command;
	}
	
	// we release an entire project
	// we first run the deployment actions (source)
	// then do a final commit (see above)
	// then merge the dev branch into the master
	// then push the master to the origin (if available)
	// this can start a remote build
	public void release(@WebParam(name = "id") String id, @WebParam(name = "message") String message, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws IllegalStateException, FileNotFoundException, GitAPIException, IOException, ServiceException, InterruptedException, ExecutionException {
		Token token = ServiceRuntime.getRuntime().getExecutionContext().getSecurityContext().getToken();
		
		Entry entry = EAIResourceRepository.getInstance().getEntry(id);
		if (!EAIRepositoryUtils.isProject(entry)) {
			throw new IllegalArgumentException("Not a valid project id: " + id);
		}
		// first we run a commit cycle, we don't push yet, we first want to tag
		Git git = commitInternal(id, "Commit for release", false, username, password);
		
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
				authenticate(git.push(), username, password).setPushTags().setRemote(remote).call();
			}
		}
	}
	
	@WebResult(name = "build")
	public GitBuild buildInformation(@NotNull @WebParam(name = "name") String name) {
		GitRepository repository = getRepository(name);
		GitBuild build = new GitBuild();
		build.setReleases(new ArrayList<GitRelease>(repository.getVersions()));
		return build;
	}
	
	@WebResult(name = "result")
	public MergeResult getMergeResult(@NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch) throws IOException, ParseException {
		GitRepository repository = getRepository(name);
		return repository.getMergeResult(branch);
	}
	
	public void setMergeResult(@NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch, @WebParam(name = "result") MergeResult result) {
		GitRepository repository = getRepository(name);
		repository.setMergeResult(branch, result);
	}

	private GitRepository getRepository(String name) {
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getBuildsFolder();
		File file = new File(builds, name);
		File git = new File(file, ".git");
		if (!git.exists()) {
			throw new IllegalArgumentException("There is no build with the name: " + name);
		}
		GitRepository repository = new GitRepository(file, name);
		return repository;
	}
	
	public void addEnvironment(@NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "environment") String environment, @WebParam(name = "copyEnvironment") String copyFromOther) {
		GitRepository repository = getRepository(name);
		repository.addEnvironment(environment, copyFromOther);
	}
	
	@WebResult(name = "zip")
	public byte [] zip(@NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch) {
		GitRepository repository = getRepository(name);
		return repository.getAsZip(branch);
	}
	
	// we can "build" a project
	// this will check for a folder in a certain directory
	// if it exists, it will work from there
	// if it doesn't exist yet, it will check the repo for a project by that name and start from there.
	// otherwise, it will throw an exception
	public void build(@WebParam(name = "name") String name, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws InvalidRemoteException, TransportException, GitAPIException {
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getBuildsFolder();
		File file = new File(builds, name);
		// if we don't find the file, let's check if we can clone it from the current repository
		if (!file.exists()) {
			Entry entry = EAIResourceRepository.getInstance().getEntry(name);
			if (entry instanceof ResourceEntry) {
				ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
				if (container instanceof FileDirectory) {
					File project = ((FileDirectory) container).getFile();
					clone(name, project.toURI(), username, password);
				}
			}
		}
		File git = new File(file, ".git");
		if (!git.exists()) {
			throw new IllegalArgumentException("There is no build with the name: " + name);
		}
		GitRepository repository = new GitRepository(file, name);
		// we'll first check for new version tags
		repository.checkForVersionUpdates();
		// then we'll check for secondary updates
		repository.checkForSecondaryUpdates();
	}
	
	public void clone(@NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "endpoint") URI uri, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws InvalidRemoteException, TransportException, GitAPIException {
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getBuildsFolder();
		authenticate(Git.cloneRepository().setURI(uri.toASCIIString()), username, password).setDirectory(new File(builds, name)).call();
	}

	private File getBuildsFolder() {
		File directory = new File(System.getProperty("git.build", System.getProperty("user.home")));
		File nabu = new File(directory, ".nabu");
		File builds = new File(nabu, "builds");
		if (!builds.exists()) {
			builds.mkdirs();
		}
		return builds;
	}
}
