package nabu.misc.git;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
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
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.git.GitInformation;
import be.nabu.eai.module.git.GitInformations;
import be.nabu.eai.module.git.GitPatch;
import be.nabu.eai.module.git.GitRelease;
import be.nabu.eai.module.git.GitRepository;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.authentication.impl.BasicPrincipalImpl;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.file.FileDirectory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanResolver;
import nabu.misc.git.types.GitBuild;
import nabu.misc.git.types.MergeResult;

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
 * Should probably save the credentials in an encrypted settings file...
 * 
 * @author alex
 *
 */
@WebService
public class Services {
	
	private static String masterBranch = System.getProperty("git.master", "master");
	private static String devBranch = System.getProperty("git.dev", "develop");
	private static String remote = System.getProperty("git.remote", "origin");
	private static Boolean prezip = Boolean.parseBoolean(System.getProperty("git.prezip", "true"));
	
	private static String DEFAULT_WORKSPACE = "default";

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	// commit an entire project or a particular folder
	// we use the metadata of the calling user
	// we commit on the current branch (whichever that is) and push it to the origin (if any)
	// this could be the master branch
	// any deployment actions that reside within the id are run before committing
	public void commit(@NotNull @WebParam(name = "id") String id, @WebParam(name = "message") String message, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws IllegalStateException, GitAPIException, FileNotFoundException, IOException, ServiceException, InterruptedException, ExecutionException, ParseException {
		commitInternal(id, message, true, username, password);
	}

	private Git commitInternal(String id, String message, boolean push, String username, String password) throws GitAPIException, IOException, FileNotFoundException, NoFilepatternException, AbortedByHookException, ConcurrentRefUpdateException, NoHeadException, NoMessageException, ServiceUnavailableException, UnmergedPathsException, WrongRepositoryStateException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, ServiceException, InterruptedException, ExecutionException, InvalidRemoteException, TransportException, ParseException {
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
		
		// some are resolved twice, but in the parent commit() service we don't know the project yet
		BasicPrincipal credentials = getCredentials(project.getId(), username, password);
		
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
			.setMessage(message == null ? "[AUTO] No message" : message)
			.call();
		
		if (push) {
			// push it remotely if possible
			List<RemoteConfig> call = git.remoteList().call();
			for (RemoteConfig config : call) {
				if (remote.equals(config.getName())) {
					authenticate(git.push(), credentials.getName(), credentials.getPassword()).setRemote(remote).call();
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
	public void release(@WebParam(name = "id") String id, @WebParam(name = "message") String message, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws IllegalStateException, FileNotFoundException, GitAPIException, IOException, ServiceException, InterruptedException, ExecutionException, ParseException {
		Token token = ServiceRuntime.getRuntime().getExecutionContext().getSecurityContext().getToken();
		
		Entry entry = EAIResourceRepository.getInstance().getEntry(id);
		if (!EAIRepositoryUtils.isProject(entry)) {
			throw new IllegalArgumentException("Not a valid project id: " + id);
		}
		BasicPrincipal credentials = getCredentials(id, username, password);
		
		// first we run a commit cycle, we don't push yet, we first want to tag
		Git git = commitInternal(id, "[AUTO] Commit for release", false, credentials.getName(), credentials.getPassword());
		
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
				authenticate(git.push(), credentials.getName(), credentials.getPassword()).setPushTags().setRemote(remote).call();
			}
		}
	}
	
	@WebResult(name = "build")
	public GitBuild buildInformation(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			GitBuild build = new GitBuild();
			build.setName(name);
			build.setReleases(new ArrayList<GitRelease>(repository.getVersions()));
			return build;
		}
		finally {
			repository.close();
		}
	}
	
	@WebResult(name = "builds")
	public List<GitInformation> builds() throws FileNotFoundException, IOException, ParseException {
		GitInformations buildFile = getBuildFile();
		File buildsFolder = getBuildsFolder();
		List<GitInformation> builds = new ArrayList<GitInformation>();
		if (buildsFolder.exists()) {
			// workspaces!
			for (File child : buildsFolder.listFiles()) {
				if (child.isDirectory()) {
					List<String> names = new ArrayList<String>();
					for (File project : child.listFiles()) {
						if (project.isDirectory()) {
							names.add(project.getName());
						}
					}
					boolean changed = false;
					for (GitInformation single : buildFile.getRepositories()) {
						// if the workspace is empty, for backwards compatibility (before workspaces)
						if (names.contains(single.getName()) && (single.getWorkspace() == null || single.getWorkspace().equals(child.getName()))) {
							builds.add(single);
							// just in case it wasn't filled in yet...
							if (single.getWorkspace() == null || !single.getWorkspace().equals(child.getName())) {
								single.setWorkspace(child.getName());
								changed = true;
							}
							names.remove(single.getName());
						}
					}
					if (!names.isEmpty()) {
						for (String missing : names) {
							GitInformation information = new GitInformation();
							information.setName(missing);
							information.setWorkspace(child.getName());
							buildFile.getRepositories().add(information);
							builds.add(information);
							changed = true;
						}
					}
					if (changed) {
						saveBuildFile(buildFile);
					}
				}
			}
		}
		return builds;
	}
	
	public void createWorkspace(@NotNull @WebParam(name = "name") String name) {
		getWorkspaceFolder(name);
	}
	
	@WebResult(name = "workspaces")
	public List<String> workspaces() {
		List<String> workspaces = new ArrayList<String>();
		File buildsFolder = getBuildsFolder();
		for (File child : buildsFolder.listFiles()) {
			if (child.isDirectory()) {
				workspaces.add(child.getName());
			}
		}
		return workspaces;
	}
	
	@WebResult(name = "projects")
	public List<GitInformation> projects() throws FileNotFoundException, IOException, ParseException {
		RepositoryEntry root = EAIResourceRepository.getInstance().getRoot();
		List<String> names = new ArrayList<String>();
		for (Entry child : root) {
			if (EAIRepositoryUtils.isProject(child) && child instanceof ResourceEntry) {
				ResourceContainer<?> container = ((ResourceEntry) child).getContainer();
				if (container instanceof FileDirectory) {
					names.add(child.getName());
				}
			}
		}
		GitInformations buildFile = getBuildFile();
		List<GitInformation> builds = new ArrayList<GitInformation>();
		for (GitInformation single : buildFile.getRepositories()) {
			if (names.contains(single.getName())) {
				builds.add(single);
				names.remove(single.getName());
			}
		}
		if (!names.isEmpty()) {
			for (String missing : names) {
				GitInformation information = new GitInformation();
				information.setName(missing);
				buildFile.getRepositories().add(information);
				builds.add(information);
			}
			saveBuildFile(buildFile);
		}
		return builds;
	}
	
	@WebResult(name = "result")
	public MergeResult getMergeResult(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			return repository.getMergeResult(branch);
		}
		finally {
			repository.close();
		}
	}
	
	public void setMergeResult(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch, @WebParam(name = "result") MergeResult result) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			BasicPrincipal credentials = getCredentials(name, null, null);
			repository.setUsername(credentials.getName());
			repository.setPassword(credentials.getPassword());
			repository.setMergeResult(branch, result);
		}
		finally {
			repository.close();
		}
	}

	private GitRepository getRepository(String workspace, String name) {
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getWorkspaceFolder(workspace);
		File file = new File(builds, name);
		File git = new File(file, ".git");
		if (!git.exists()) {
			throw new IllegalArgumentException("There is no build with the name: " + name);
		}
		GitRepository repository = new GitRepository(file, name);
		if (prezip) {
			repository.setZipFolder(getZipFolder(workspace, name));
		}
		return repository;
	}
	
	private GitInformations getBuildFile() throws FileNotFoundException, IOException, ParseException {
		File buildsFolder = getBuildsFolder();
		if (!buildsFolder.exists()) {
			buildsFolder.mkdirs();
		}
		GitInformations informations = null;
		File file = new File(buildsFolder, "builds.xml");
		if (!file.exists()) {
			informations = new GitInformations();
		}
		else {
			XMLBinding binding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(GitInformations.class), Charset.forName("UTF-8"));
			try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
				informations = TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), GitInformations.class);
			}
		}
		return informations;
	}
	private void saveBuildFile(GitInformations build) throws IOException {
		File buildsFolder = getBuildsFolder();
		File file = new File(buildsFolder, "builds.xml");
		try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
			XMLBinding binding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(GitInformations.class), Charset.forName("UTF-8"));
			binding.setPrettyPrint(true);
			binding.marshal(output, new BeanInstance<GitInformations>(build));
		}
	}
	
	public void configure(@NotNull @WebParam(name = "configuration") GitInformation configuration) throws FileNotFoundException, IOException, ParseException {
		GitInformations buildFile = getBuildFile();
		if (configuration == null || configuration.getName() == null) {
			return;
		}
		GitInformation information = null;
		for (GitInformation possible : buildFile.getRepositories()) {
			if (possible.getName().equals(configuration.getName())) {
				information = possible;
				break;
			}
		}
		if (information == null) {
			information = new GitInformation();
			String name = configuration.getName();
			name = name.replaceAll("[^\\w]+", "_");
			// can not be updated after the fact
			information.setName(name);
			String workspace = configuration.getWorkspace() == null ? DEFAULT_WORKSPACE : configuration.getWorkspace();
			workspace = workspace.replaceAll("[^\\w]+", "_");
			information.setWorkspace(workspace);
			buildFile.getRepositories().add(information);
		}
		information.setUsername(configuration.getUsername());
		information.setPassword(configuration.getPassword());
		information.setDescription(configuration.getDescription());
		saveBuildFile(buildFile);
	}
	
	@WebResult(name = "environments")
	public List<String> environments(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @WebParam(name = "release") Integer release, @WebParam(name = "patch") Integer patch) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			if (release != null && patch != null) {
				return repository.getEnvironments(release, patch);
			}
			else {
				return repository.getCurrentEnvironments();
			}
		}
		finally {
			repository.close();
		}
	}
	
	public void addEnvironment(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "environment") String environment, @WebParam(name = "copyEnvironment") String copyFromOther) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			repository.addEnvironment(environment, copyFromOther);
		}
		finally {
			repository.close();
		}
	}
	
	@WebResult(name = "zip")
	public byte [] zip(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "branch") String branch, @WebParam(name = "releaseCandidate") Integer releaseCandidate, @WebParam(name = "includeRoot") Boolean includeRoot) throws Exception {
		GitRepository repository = getRepository(workspace, name);
		try {
			return repository.getAsZip(branch, releaseCandidate, includeRoot != null && includeRoot);
		}
		finally {
			repository.close();
		}
	}
	
	// we can "build" a project
	// this will check for a folder in a certain directory
	// if it exists, it will work from there
	// if it doesn't exist yet, it will check the repo for a project by that name and start from there.
	// otherwise, it will throw an exception
	@WebResult(name = "newReleases")
	public List<GitRelease> build(@WebParam(name = "workspace") String workspace, @WebParam(name = "name") String name, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws Exception {
		BasicPrincipal credentials = getCredentials(name, username, password);
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getWorkspaceFolder(workspace);
		File file = new File(builds, name);
		// if we don't find the file, let's check if we can clone it from the current repository
		if (!file.exists()) {
			Entry entry = EAIResourceRepository.getInstance().getEntry(name);
			if (entry instanceof ResourceEntry) {
				ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
				if (container instanceof FileDirectory) {
					File project = ((FileDirectory) container).getFile();
					// if the project is not yet version controlled, we need to add that
					if (!new File(project, ".git").exists()) {
						release(name, "Release for first build", username, password);
					}
					clone(workspace, name, project.toURI(), credentials.getName(), credentials.getPassword());
				}
			}
		}
		File git = new File(file, ".git");
		if (!git.exists()) {
			throw new IllegalArgumentException("There is no build with the name: " + name);
		}
		GitRepository repository = new GitRepository(file, name);
		try {
			repository.setUsername(credentials.getName());
			repository.setPassword(credentials.getPassword());
			// we'll first check for new version tags
			return repository.checkForVersionUpdates();
			// then we'll check for secondary updates
			// takes a long time and we almost never use it
//			repository.checkForSecondaryUpdates();
		}
		finally {
			repository.close();
		}
	}
	
	@WebResult(name = "newReleases")
	public List<GitPatch> hotfix(@WebParam(name = "workspace") String workspace, @WebParam(name = "name") String name, @WebParam(name = "username") String username, @WebParam(name = "password") String password, @WebParam(name = "version") Integer version) throws Exception {
		BasicPrincipal credentials = getCredentials(name, username, password);
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getWorkspaceFolder(workspace);
		File file = new File(builds, name);
		// if we don't find the file, let's check if we can clone it from the current repository
		if (!file.exists()) {
			throw new IllegalStateException("Could not find project: " + name);
		}
		File git = new File(file, ".git");
		if (!git.exists()) {
			throw new IllegalArgumentException("There is no build with the name: " + name);
		}
		GitRepository repository = new GitRepository(file, name);
		try {
			repository.setUsername(credentials.getName());
			repository.setPassword(credentials.getPassword());
			// then we'll check for secondary updates
			return repository.checkForSecondaryUpdates(version);
		}
		finally {
			repository.close();
		}
	}
	
	private BasicPrincipal getCredentials(String name, String username, String password) throws FileNotFoundException, IOException, ParseException {
		// if you send in a username, we use that, we don't store it
		if (username != null) {
			return new BasicPrincipalImpl(username, password);
		}
		GitInformations buildFile = getBuildFile();
		for (GitInformation repository : buildFile.getRepositories()) {
			if (repository.getName().equals(name)) {
				if (repository.getUsername() != null) {
					return new BasicPrincipalImpl(repository.getUsername(), repository.getPassword());
				}
			}
		}
		// no NPE... ugly but internal and it works for now
		return new BasicPrincipalImpl();
	}
	
	public void clone(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "endpoint") URI uri, @WebParam(name = "username") String username, @WebParam(name = "password") String password) throws InvalidRemoteException, TransportException, GitAPIException {
		name = name.replaceAll("[^\\w]+", "_");
		File builds = getWorkspaceFolder(workspace);
		authenticate(Git.cloneRepository().setURI(uri.toASCIIString()), username, password).setDirectory(new File(builds, name)).call();
	}

	private File getWorkspaceFolder(String workspace) {
		File builds = getBuildsFolder();
		if (workspace == null) {
			workspace = DEFAULT_WORKSPACE;
		}
		workspace = workspace.replaceAll("[^\\w]+", "_");
		builds = new File(builds, workspace);
		if (!builds.exists()) {
			builds.mkdirs();
		}
		return builds;
	}
	
	private File getBuildsFolder() {
		String property = System.getProperty("git.build");
		File builds;
		// if you don't set an explicit property, we use the home folder
		if (property == null) {
			File directory = new File(System.getProperty("user.home"));
			File nabu = new File(directory, ".nabu");
			builds = new File(nabu, "builds");
		}
		else {
			builds = new File(property);
			// we want a subfolder because the images are likely right next to this
			builds = new File(builds, "builds");
		}
		if (!builds.exists()) {
			builds.mkdirs();
		}
		return builds;
	}
	
	private File getZipFolder(String workspace, String project) {
		String property = System.getProperty("git.build");
		File builds;
		// if you don't set an explicit property, we use the home folder
		if (property == null) {
			File directory = new File(System.getProperty("user.home"));
			File nabu = new File(directory, ".nabu");
			builds = new File(nabu, "zips");
		}
		else {
			builds = new File(property);
			// we want a subfolder because the images are likely right next to this
			builds = new File(builds, "zips");
		}
		builds = new File(builds, workspace == null ? DEFAULT_WORKSPACE : workspace);
		builds = new File(builds, project);
		if (!builds.exists()) {
			builds.mkdirs();
		}
		return builds;
	}
	
	public List<String> getReleaseNotes(@WebParam(name = "workspace") String workspace, @NotNull @WebParam(name = "name") String name, @NotNull @WebParam(name = "version") Integer version) throws RevisionSyntaxException, AmbiguousObjectException, IncorrectObjectTypeException, NoHeadException, IOException, GitAPIException {
		return version == null ? null : getRepository(workspace, name).getReleaseNotes(version, 0);
	}
}
