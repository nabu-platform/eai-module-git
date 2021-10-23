package be.nabu.eai.module.git;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.git.merge.BuildInformation;
import be.nabu.eai.module.git.merge.MergeEntry;
import be.nabu.eai.module.git.merge.MergeParameter;
import be.nabu.eai.module.git.merge.MergeEntry.MergeState;
import be.nabu.eai.module.git.merge.MergeResult;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;
import be.nabu.glue.core.repositories.DynamicScript;
import be.nabu.glue.core.repositories.DynamicScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.services.ServiceMethodProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.file.FileDirectory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.security.EncryptionXmlAdapter;

/**
 * GOALS:
 * - this should work on any server, either a development server or a dedicated build server
 * - all necessary metadata should be available in the git repo, no additional external source of information should be necessary (so no database to keep track of stuff)
 * - ideally we should be able to recover from a failed state (e.G. A server crash halfway a build)
 * - ideally we want to build without needing the java code to perform merges etc, a pure textual build. this mostly impacts environment-specific properties
 * 
 * CONSIDERATIONS:
 * - we can't work directly on the git repo available in the repository, as this needs to stay in its development branch, we can't go switching branches
 * - we can't count on being able to check the repository for valid git repositories, as this would make it impossible to run it on a separate server
 * - we don't want to do squash commits because all the branches are "live", we don't want to kill them off. at some point, dev branch can start working with offshoots that are squashed if necessary
 * - we can't rebase, as this could incur merge conflicts when environment-specific changes are made and/or hotfixes applied
 * 
 * STEPS:
 * - we merge changes from dev to master, this triggers a new cycle
 * - we take the last "rx" version where x is the version number, increment it with one and start a branch at the latest commit of the master, e.g. r0, r1, r2,...
 * - we immediately sub-branch the rx with rx.0, for example r2.0. This is the patch level as hot fixes can be applied directly to rx which will result in an increment in patch number (rx.1, rx.2 etc)
 * - we look at the last branch and create new subbranches for each environment present there, new environments can be added simply be creating a new branch
 * 		- BONUS: when a new environment is added, we can "start" from an existing environment, which means all the initial configuration will come from there?
 * - for each environment branch (rx.0-environment) we check the previous version and automatically merge any environment specific parameters
 * - then we automatically tag the current state as a release candidate rx.0-environment-RC1
 * - we offer a web view with a report (required merge vs optional merge) and a way to fill in the correct data
 * - this is merged into the rx.0-environment branch which is (after commit) again tagged with an increment RC number, e.g. r2.0-qlty-RC2
 * 
 * 
 * - we can deduce the state of the building process by looking at the state of the git repo? if no RC tag 
 * 
 * STEPS:
 * - from dev to master -> squash commit, combined with pull request? post squash is branch niet herbruikbaar, zijbranches van dev?
 * - we can manually clone a repository containing a project into a folder
 * - whatever branch it is on, will be the branch that this tool will consider the "main" branch, the default is "master"
 * 		- might need to explicitly add configuration to use a different branch, otherwise if it is left in a wrong state, it is hard to validate
 * - if we receive a poll action, we will do a pull from that branch, check if there are any updates
 * - if there are, we either:
 * 		- rebase the last release if it is not yet finalized
 * 		- rebasing the last release might make it hard to correctly merge new environment specific parameters
 * 			-> although if we assume you don't delete and re-add stuff (at which point the correct parameters are in the previous version)
 * 			-> we can diff the environment specific against the latest RC (always?)
 * 			-> this might allow for rebasing
 * 		- we start a new release if the last release was finalized
 * - for each environment known in the previous release, we start a new subbranch of the release branch (if not yet available)
 * - for each environment we create a new release candidate (RC1, RC2,...) (tag!)
 * 		- note that when rebasing, we can't find branches in the rebased branch, we must check tags to figure out existing RC
 * 		- further processes must be started (e.g. to create a runnable nabu environment)
 * - once the user validates a release candidate, we create a new tag v1-STABLE-bebat-qlty. a new release needs to be created (FINAL)
 * 		- the final release is made for all systems, a new version is automatically started
 *  
 *  
 *  - you can create a new "release" (named or custom versioning) which targets a particular release of a package
 *  -> e.g. for bebat, we can deploy v24 of the bebat folder (which might be up to v28 looking at the master) and LATEST from the telemetrics package
 *  -> by default there is always a build for "LATEST"
 *  
 * 
 * Note: to use this publically, we need to add some safeguards, the build system is intentionally powerful, but this also allows for bad actors.
 * Two general directions:
 * - we sandbox the glue and chroot the filesystem, this can however be a wackamole situation
 * - we only allow scripts by URL (unless you are a corporate user) and the URL must live on our cloud and be a validated script
 * 
 * Merge scripts need to be validated by us.
 *  
 * @author alex
 *
 */
// 
// 
// -
// can't work on the actual git repo in the repository, as i need to constantly switch branches etc
// this would not work in a development scenario
// we want to be able to work

/**
 * # SSH
 * 
 * I looked into using SSH, in the past jgit used jsch for ssh access but this has been deprecated and support for it will be removed at some point.
 * They have switched to apache mina ssh, you need to include maven dependency for org.eclipse.jgit.ssh.apache to get it.
 * The problem that I saw with this approach is that the apache library seems to (from the examples online and the methods available in SshdSessionFactoryBuilder) to only support file-based ssh keys.
 * This means I could not use ssh keys that are stored in an encrypted keystore but would be forced to write them plain text to the file system.
 * Yes, you can set an additional textual key on the actual key, but again this needs to be saved somewhere.
 * At that point, from a security perspective this is hardly any better than saving an access token (password) somewhere.
 * There might be a hack when constructing a SshdSessionFactory to give it a special key cache that knows the repository. However, even that spec still uses Path items to locate the key.
 * 
 */

public class GitRepository {
	private String branch = "master";
	// the name of the remote, e.g. "origin"
	private String remote = "origin";
	private Git git;
	private TreeSet<GitRelease> versions;
	// how many versions we check
	private int secondaryDepth = 0;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private File folder;
	private String projectName;
	private String username, password;
	
	public static void main(String...args) throws IOException, NoHeadException, GitAPIException {
		GitRepository gitRepository = new GitRepository(new File("/home/alex/.nabu/repositories/local-test/test"));
		System.out.println(gitRepository.getVersions());
//		for (RevCommit commit : gitRepository.getCommitsOn("refs/heads/r1.0")) {
//			System.out.println("commit on branch: " + commit.getId().getName() + " / " + commit.getAuthorIdent().getWhen());
//		}
//		gitRepository.addEnvironment("dev", null);
		gitRepository.checkForAnyPrimaryUpdates();
		gitRepository.checkForSecondaryUpdates();
	}
	
	public GitRepository(File folder) {
		this(folder, folder.getName());
	}
	
	// you get a file where a git repository should be
	// and a branch which you should be listening to / branching off from
	// the git repository is traditionally _in_ the project, so the root folder is not part of the git repository
	// however, we do need to know the project name to create valid paths
	// if left empty, we assume the folder name is the project name
	public GitRepository(File folder, String projectName) {
		this.folder = folder;
		this.projectName = projectName == null ? folder.getName() : projectName;
		if (!folder.getName().equals(".git")) {
			folder = new File(folder, ".git");
		}
		if (!folder.exists()) {
			throw new IllegalArgumentException("Git folder does not exist: " + folder);
		}
		try {
			git = Git.open(folder);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void addEnvironment(String name, String copyFromOther) {
		GitPatch lastPatch = getLastVersion().getLastPatch();
		GitEnvironment originalEnvironment = null;
		if (copyFromOther != null) {
			List<GitEnvironment> environments = lastPatch.getEnvironments();
			for (GitEnvironment environment : environments) {
				if (copyFromOther.equals(environment.getName())) {
					originalEnvironment = environment;
					break;
				}
			}
		}
		createEnvironmentBranch(lastPatch.getRelease(), lastPatch, name, originalEnvironment);
	}
	
	private <T extends TransportCommand<?, ?>> T authenticate(T command) {
		if (username != null) {
			command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
		}
		return command;
	}
	
	// secondary updates are on separate branches, we check the last commits to see if we have for example applied a hotfix, or changed the settings of a particular RC candidate
	synchronized public void checkForSecondaryUpdates() {
		// originally the plan was to walk over the last x commits and check which branches they applied to, assuming this would be the fastest way
		// but git does not keep track of which branch a commit was originally committed on as a commit can be applied to multiple branches, branches can be renamed etc while a commit is immutable
		// we _can_ ask if a commit was applied to a particular branch, but that would mean looping over commits and for each commit looping over the branches to see if it was committed
//		try {
//			RevWalk walk = new RevWalk(git.getRepository());
//			for (RevCommit commit : git.log().call()) {
//				for (Ref branch : getBranches()) {
////					walk.isMergedInto(base, tip);
//				}
//			}
//		}
//		catch (Exception e) {
//			throw new RuntimeException(e);
//		}
		// so instead we will check all (or only the last x?) releases to see if they saw an update.
		int counter = 0;
		for (GitRelease version : getVersions().descendingSet()) {
			try {
				// we can have a new commit on a version, this would result in a new patch version
				// we check this by comparing the commit date of the last patch with the last commit on this branch
				RevCommit lastCommitOn = getLastCommitOn(version.getBranch());
				GitPatch lastPatch = version.getLastPatch();

				if (lastCommitOn != null) {
					// the last patch should not be null unless you are doing manual shenanigans
					if (lastPatch == null || GitUtils.getCommitDate(lastCommitOn).after(lastPatch.getDate())) {
						int patchVersion = lastPatch == null ? 0 : lastPatch.getPatch() + 1;
						logger.info("Hotfix found for r" + version.getVersion() + ", creating new patch version r" + version.getVersion() + "." + patchVersion);
						createPatch(version, patchVersion);
					}
					
					counter++;
					if (secondaryDepth > 0 && counter > secondaryDepth) {
						break;
					}
				}
				
				// we should not have commits on a patch version itself, but we can have additional commits on an environment branch
				// in theory there should only be changes to the last patch version, for performance reasons we'll assume that for now
				// in the future we could scan all patch versions
				if (lastPatch != null) {
					for (GitEnvironment environment : lastPatch.getEnvironments()) {
						lastCommitOn = getLastCommitOn(environment.getBranch());
						if (lastCommitOn != null) {
							// if we have a commit after the last release candidate, we must perform a new merge
							GitReleaseCandidate lastReleaseCandidate = environment.getLastReleaseCandidate();
							if (lastReleaseCandidate == null || GitUtils.getCommitDate(lastCommitOn).after(lastReleaseCandidate.getDate())) {
								logger.info("Detected change to environment: " + environment.getBranch());
								// get the last environment to check against
								GitEnvironment last = null;
								GitPatch previousPatch = null;
								// we can check the previous patch
								if (lastPatch.getPatch() > 0) {
									previousPatch = version.getPatchVersions().lower(lastPatch);
								}
								// otherwise, we have to look at the previous version
								else {
									GitRelease previousVersion = getVersions().lower(version);
									if (previousVersion != null) {
										previousPatch = previousVersion.getLastPatch();
									}
								}
								if (previousPatch != null) {
									for (GitEnvironment potential : previousPatch.getEnvironments()) {
										if (potential.getName().equals(environment.getName())) {
											last = potential;
											break;
										}
									}
								}
								merge(environment, last);
							}
						}
					}
				}
			}
			catch (Exception e) {
				logger.error("Could not scan version r" + version.getVersion(), e);
			}
		}
	}
	
	// here we check specifically for version tags in the form of "v1", "v2" etc
	// there is no concept of a minor version at this point
	// the upside is, we only build releases on specific versions, which makes it easy to link the release back to the manual action of "releasing" it
	// the downside is, you need to explicitly tag to kickstart the process
	synchronized public void checkForVersionUpdates() {
		try {
			// switch to the correct branch
			git.checkout().setName(branch).call();
			// if you have not configured a remote, we can't pull
			// you might be committing straight to the branch
			if (remote != null) {
				// pull the latest data, including the tags (we are looking for version tags)
				logger.info("Pulling last data for branch '" + branch + "' from '" + remote + "'");
				PullResult call = authenticate(git.pull()).setTagOpt(TagOpt.FETCH_TAGS).setRemote(remote).call();
				if (call.getMergeResult().getConflicts() != null && !call.getMergeResult().getConflicts().isEmpty()) {
					throw new RuntimeException("Merge conflicts detected: " + call);
				}
			}
			// get the last version
			GitRelease lastVersion = getLastVersion();
			// get the tags
			for (Ref tag : getTags()) {
				String name = tag.getName().replaceAll("^.*/([^/]+$)", "$1");
				// if you push multiple versions and one fails, we don't want to block the other ones
				// we don't particularly care which order the versions are processed in, we use the correct starting point for the branch
				// because they are created at the same time, they will all look at the latest data to merge, so it doesn't make a difference in which order
				try {
					if (name.matches("^v[0-9]+$")) {
						int tagVersion = Integer.parseInt(name.substring(1));
						RevCommit commit = getCommit(tag);
						// if this version tag is more recent than the last version we have, we start a new branch
						if (lastVersion == null || lastVersion.getDate().before(GitUtils.getCommitDate(commit))) {
							GitRelease newVersion = new GitRelease(tagVersion);
							String newBranchName = "r" + tagVersion;
							logger.info("Found a new version on branch '" + branch + "', creating release '" + newBranchName + "'");
							getVersions().add(newVersion);
							// we create the new branch
							git.branchCreate().setStartPoint(commit).setName(newBranchName).call();
							newVersion.setRevCommit(commit);
							createPatch(newVersion, 0);
						}
					}
				}
				catch (Exception e) {
					logger.error("Could not create release branch for: " + name, e);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	// a primary update is any commit on the "master" branch
	// so basically, once you run this, it will check if there is a commit more recent than the last release and use that to build a new release
	// this means we could be skipping hundreds of commits, depending on when it is pushed to the master and when this is run
	// the potential downside is that it might become hard to validate exactly which commit is being used
	// the upside is that you don't need to do anything special, just push to the master branch and you are set
	synchronized public void checkForAnyPrimaryUpdates() {
		try {
			// switch to the correct branch
			git.checkout().setName(branch).call();
			
			// if you have not configured a remote, we can't pull
			// you might be committing straight to the branch
			if (remote != null) {
				// pull the latest data
				logger.info("Pulling last data for branch '" + branch + "' from '" + remote + "'");
				PullResult call = authenticate(git.pull()).setRemote(remote).call();
				if (call.getMergeResult().getConflicts() != null && !call.getMergeResult().getConflicts().isEmpty()) {
					throw new RuntimeException("Merge conflicts detected: " + call);
				}
			}
			
			RevCommit lastCommitOn = getLastCommitOn(branch);
			// need at least 1 commit
			if (lastCommitOn != null) {
				// check it against the last version
				GitRelease lastVersion = getLastVersion();
				// if no version or the last commit is beyond that version, we need a new version
				if (lastVersion == null || lastVersion.getDate().before(GitUtils.getCommitDate(lastCommitOn))) {
					GitRelease newVersion = new GitRelease(lastVersion == null ? 1 : lastVersion.getVersion() + 1);
					String newBranchName = "r" + newVersion.getVersion();
					logger.info("Found a new commit on branch '" + branch + "', creating release '" + newBranchName + "'");
					
					getVersions().add(newVersion);
					// we create the new branch
					Ref call = git.branchCreate().setName(newBranchName).call();
					newVersion.setRevCommit(getCommit(call));
					createPatch(newVersion, 0);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	synchronized private GitPatch createPatch(GitRelease newVersion, int patchVersion) throws GitAPIException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException {
		git.checkout().setName(newVersion.getBranch()).call();
		GitPatch patch = new GitPatch(newVersion, patchVersion);
		// immediately create a fix version 0 branch
		Ref call = git.branchCreate().setName(patch.getBranch()).call();
		patch.setRevCommit(getCommit(call));
		
		logger.info("Created patch version version '" + patch.getBranch() + "'");
		
		// if we started a new patch version, we have to look at the previous release to find the environments
		if (patchVersion == 0) {
			GitRelease previousVersion = getVersions().lower(newVersion);
			// if we have a previous version, go with that
			if (previousVersion != null) {
				GitPatch lastPatch = previousVersion.getLastPatch();
				if (lastPatch != null) {
					for (GitEnvironment environment : lastPatch.getEnvironments()) {
						createEnvironmentBranch(newVersion, patch, environment.getName(), environment);
					}
				}
			}
		}
		// otherwise, we get the last patch from the current version
		else {
			GitPatch lastPatch = newVersion.getLastPatch();
			if (lastPatch != null) {
				for (GitEnvironment environment : lastPatch.getEnvironments()) {
					createEnvironmentBranch(newVersion, patch, environment.getName(), environment);
				}
			}
		}
		newVersion.getPatchVersions().add(patch);
		// back to the original one
		git.checkout().setName(branch).call();
		return patch;
	}
	
	// we create an environment branch with a given name
	// if there is an original, we use that to merge data
	synchronized private void createEnvironmentBranch(GitRelease release, GitPatch patch, String name, GitEnvironment original) {
		String branchName = "r" + release.getVersion() + "." + patch.getPatch();
		try {
			// go to the correct branch
			git.checkout().setName(branchName).call();
			// create the branch for the environment
			String environmentBranch = branchName + "-" + name;
			// create a new branch for the environment
			Ref call = git.branchCreate().setName(environmentBranch).call();
			
			logger.info("Created new environment '" + environmentBranch + "'");
			
			GitEnvironment environment = new GitEnvironment(patch, name);
			environment.setRevCommit(getCommit(call));
			patch.getEnvironments().add(environment);
			
			merge(environment, original);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public MergeResult getMergeResult(String branch) {
		RevCommit lastCommitOn = getLastCommitOn(branch);
		
		try {
			if (lastCommitOn != null) {
				byte[] read = read("merge-result.xml", lastCommitOn);
				if (read != null) {
					XMLBinding binding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(MergeResult.class), Charset.forName("UTF-8"));
					return decrypt(TypeUtils.getAsBean(binding.unmarshal(new ByteArrayInputStream(read), new Window[0]), MergeResult.class));
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
	
	private MergeResult decrypt(MergeResult result) throws Exception {
		if (result != null && result.getEntries() != null) {
			for (MergeEntry entry : result.getEntries()) {
				for (MergeParameter parameter : entry.getParameters()) {
					if (parameter.isEncrypted()) {
						EncryptionXmlAdapter encryptionXmlAdapter = new EncryptionXmlAdapter();
						if (parameter.getRaw() != null) {
							parameter.setRaw(encryptionXmlAdapter.unmarshal(parameter.getRaw()));
						}
						if (parameter.getCurrent() != null) {
							parameter.setCurrent(encryptionXmlAdapter.unmarshal(parameter.getCurrent()));
						}
						if (parameter.getPrevious() != null) {
							parameter.setPrevious(encryptionXmlAdapter.unmarshal(parameter.getPrevious()));
						}
					}
					// replace with null, don't trim in case you want explicit whitespace
					if (parameter.getCurrent() != null && parameter.getCurrent().isEmpty()) {
						parameter.setCurrent(null);
					}
				}
			}
		}
		return result;
	}
	
	private MergeResult encrypt(MergeResult result) throws Exception {
		if (result != null && result.getEntries() != null) {
			for (MergeEntry entry : result.getEntries()) {
				for (MergeParameter parameter : entry.getParameters()) {
					if (parameter.isEncrypted()) {
						EncryptionXmlAdapter encryptionXmlAdapter = new EncryptionXmlAdapter();
						if (parameter.getRaw() != null) {
							parameter.setRaw(encryptionXmlAdapter.marshal(parameter.getRaw()));
						}
						if (parameter.getCurrent() != null) {
							parameter.setCurrent(encryptionXmlAdapter.marshal(parameter.getCurrent()));
						}
						if (parameter.getPrevious() != null) {
							parameter.setPrevious(encryptionXmlAdapter.marshal(parameter.getPrevious()));
						}
					}
					// replace with null, don't trim in case you want explicit whitespace
					if (parameter.getCurrent() != null && parameter.getCurrent().isEmpty()) {
						parameter.setCurrent(null);
					}
				}
			}
		}
		return result;
	}
	
	synchronized public void setMergeResult(String branch, MergeResult result) {
		try {
			try {
				git.checkout().setName(branch).call();
				File file = new File(folder, "merge-result.xml");
				if (result == null) {
					if (file.exists()) {
						file.delete();
					}
				}
				else {
					XMLBinding binding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(MergeResult.class), Charset.forName("UTF-8"));
					binding.setPrettyPrint(true);
					try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
						binding.marshal(output, new BeanInstance<MergeResult>(encrypt(result)));
					}
				}
				// we need to immediately merge this result, otherwise we can't checkout other branches
				GitEnvironment environment = getEnvironment(getVersions(), branch);
				merge(environment);
			}
			finally {
				git.checkout().setName(this.branch).call();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	synchronized private void merge(GitEnvironment current) {
		GitEnvironment previous = null;
		if (current.getPatch().getPatch() == 0) {
			GitRelease release = current.getPatch().getRelease();
			GitRelease lower = getVersions().lower(release);
			if (lower != null) {
				GitPatch lastPatch = lower.getLastPatch();
				if (lastPatch != null) {
					for (GitEnvironment environment : lastPatch.getEnvironments()) {
						if (environment.getName().equals(current.getName())) {
							previous = environment;
							break;
						}
					}
				}
			}
		}
		else {
			GitPatch lower = current.getPatch().getRelease().getPatchVersions().lower(current.getPatch());
			if (lower != null) {
				for (GitEnvironment environment : lower.getEnvironments()) {
					if (environment.getName().equals(current.getName())) {
						previous = environment;
						break;
					}
				}
			}
		}
		merge(current, previous);
	}
	
	synchronized public byte[] getAsZip(String branch) {
		try {
			try {
				git.checkout().setName(branch).call();
				ByteBuffer newByteBuffer = IOUtils.newByteBuffer();
				try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(IOUtils.toOutputStream(newByteBuffer)))) {
					ResourceUtils.zip(new FileDirectory(null, folder, false), zipOutputStream, true, new Predicate<Resource>() {
						@Override
						public boolean test(Resource t) {
							if (t.getName().equals("merge-result.xml")) {
								return false;
							}
							return true;
						}
					});
				}
				return IOUtils.toBytes(newByteBuffer);
			}
			finally {
				git.checkout().setName(this.branch).call();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	// every method that performs a checkout, needs a lock to do so
	synchronized private void merge(GitEnvironment current, GitEnvironment previous) {
		try {
			if (previous == null) {
				logger.info("Merging environment '" + current.getBranch() + "'");
			}
			else {
				logger.info("Merging environment '" + current.getBranch() + "' from '" + previous.getBranch() + "'");
			}
			
			// switch to that branch
			git.checkout().setName(current.getBranch()).call();
			
			MergeResult result = null;
			// we check if there is a parameter file
			File file = new File(folder, "merge-result.xml");
			XMLBinding binding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(MergeResult.class), Charset.forName("UTF-8"));
			binding.setPrettyPrint(true);
			
			// if we have a file, load it
			if (file.exists()) {
				try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
					result = TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), MergeResult.class);
				}
			}
			// if we don't have a merge result available, create a new one
			if (result == null) {
				result = new MergeResult();
			}
			result.setStarted(new Date());
			
			RevCommit previousCommit = previous == null ? null : getLastCommitOn(previous.getBranch());
			
			MergeResult previousResult = null;
			if (previousCommit != null) {
				byte[] read = read("merge-result.xml", previousCommit);
				if (read != null) {
					previousResult = TypeUtils.getAsBean(binding.unmarshal(new ByteArrayInputStream(read), new Window[0]), MergeResult.class);
				}
			}
			
			GitMethods methods = new GitMethods(this, result, previousResult, previousCommit);
			
			EAIResourceRepository instance = EAIResourceRepository.getInstance();
			GlueParserProvider parserProvider = new GlueParserProvider(new ServiceMethodProvider(instance, instance), new StaticJavaMethodProvider(methods));
			
			DynamicScriptRepository repository = new DynamicScriptRepository(parserProvider);
			
			SimpleExecutionEnvironment environment = new SimpleExecutionEnvironment("default");
			Map<URI, String> resolved = new HashMap<URI, String>();
			List<GitNode> nodes = new ArrayList<GitNode>();
			merge(folder, projectName, repository, methods, resolved, environment, nodes);
			
			MergeState state = MergeState.SUCCEEDED;
			if (result.getEntries() != null) {
				for (MergeEntry entry : result.getEntries()) {
					if (!MergeState.SUCCEEDED.equals(entry.getState())) {
						// we don't do "ERROR" here, anything that is not finished (either due to error or pending) is considered pending
						state = MergeState.PENDING;
						break;
					}
				}
			}
			result.setState(state);
			result.setStopped(new Date());
			// write the merge result
			try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
				binding.marshal(output, new BeanInstance<MergeResult>(result));
			}
			
			GitReleaseCandidate previousRc = current.getLastReleaseCandidate();
			int candidateVersion = previousRc == null ? 1 : previousRc.getCandidate() + 1;
			String fullName = current.getBranch() + "-RC" + candidateVersion;
			
			// the merge result is necessary for the merging but is not exposed once we download the end result
			// the build information is a rather static file, specifically aimed at informing the end-user of which version the resulting zip is
			// we can also include other data like required references
			File buildFile = new File(folder, "build.xml");
			XMLBinding buildBinding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(BuildInformation.class), Charset.forName("UTF-8"));
			buildBinding.setPrettyPrint(true);
			BuildInformation build = null;
			// if we have a file, load it
			if (buildFile.exists()) {
				try (InputStream input = new BufferedInputStream(new FileInputStream(buildFile))) {
					build = TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), BuildInformation.class);
				}
			}
			// if we don't have a merge result available, create a new one
			if (build == null) {
				build = new BuildInformation();
			}
			build.setBuilt(new Date());
			build.setTag(fullName);
			build.setRelease(current.getPatch().getRelease().getVersion());
			build.setPatch(current.getPatch().getPatch());
			build.setEnvironment(current.getName());
			build.setRc(candidateVersion);
			build.setDependencies(calculateDependencies(nodes));
			// write the merge result
			try (OutputStream output = new BufferedOutputStream(new FileOutputStream(buildFile))) {
				binding.marshal(output, new BeanInstance<BuildInformation>(build));
			}
			
			// commit the resulting branch
			git.add().addFilepattern(".").call();
			git.commit().setAll(true).setMessage("Merged for RC" + candidateVersion).call();
			Ref call = git.tag().setName(fullName).call();
			GitReleaseCandidate rc = new GitReleaseCandidate(call.getName(), candidateVersion);
			rc.setRevCommit(getCommit(call));
			current.getReleaseCandidates().add(rc);
			logger.info("Created release candidate '" + fullName + "'");
			// switch back to the main branch
			git.checkout().setName(branch).call();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private List<String> calculateDependencies(List<GitNode> nodes) {
		List<String> inBuild = new ArrayList<String>();
		for (GitNode node : nodes) {
			inBuild.add(node.getId());
		}
		List<String> dependencies = new ArrayList<String>();
		for (GitNode node : nodes) {
			if (node.getReferences() != null) {
				for (String reference : node.getReferences()) {
					if (!dependencies.contains(reference) && !inBuild.contains(reference)) {
						dependencies.add(reference);
					}
				}
			}
		}
		return dependencies;
	}
	
	private JAXBContext nodeContext;
	
	private JAXBContext getNodeContext() {
		if (nodeContext == null) {
			try {
				nodeContext = JAXBContext.newInstance(GitNode.class);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return nodeContext;
	}
	
	// the path is the "." separated entry id path
	private void merge(File folder, String path, DynamicScriptRepository repository, GitMethods methods, Map<URI, String> resolved, ExecutionEnvironment environment, List<GitNode> nodes) {
		// check if we have a node
		File nodeFile = new File(folder, "node.xml");
		if (nodeFile.exists()) {
			try {
				GitNode node = (GitNode) getNodeContext().createUnmarshaller().unmarshal(nodeFile);
				node.setId(path);
				nodes.add(node);
				String mergeScript = node.getMergeScript();
				
				if (mergeScript == null) {
					mergeScript = getStandardMergeScript(node, resolved);
				}
				
				// if we have a merge script, get cracking
				if (mergeScript != null && !mergeScript.trim().isEmpty()) {
					logger.info("Merging entry '" + path + "'");
					
					// we set the current path as the entry id
					methods.setCurrentEntryId(path);
					// get the merge entry
					MergeEntry merged = methods.merged();
					// always set to pending before we begin
					merged.setState(MergeState.PENDING);
					// set the latest metadata from the node
					merged.setNode(node);
					
					StringBuilder scriptLogger = new StringBuilder();
					try {
						// we initially assume it was not changed
						merged.setChanged(false);
						
						// get the previous version of the node.xml
						byte[] previous = methods.previousContent("node.xml");
						// compare the node properties to see if it was changed
						if (previous != null) {
							GitNode previousNode = (GitNode) getNodeContext().createUnmarshaller().unmarshal(new ByteArrayInputStream(previous));
							if (!previousNode.getLastModified().equals(node.getLastModified()) || !previousNode.getEnvironmentId().equals(node.getEnvironmentId()) || previousNode.getVersion() != node.getVersion()) {
								merged.setChanged(true);
							}
						}
						// if it did not exist before, it is definitely a change
						else {
							merged.setChanged(true);
						}
						
						Script script;
						// if we detect a URL, we load it in remotely
						if (mergeScript.trim().matches("^[\\w]+://.*$")) {
							script = getScript(repository, new URI(URIUtils.encodeURI(mergeScript)), resolved);
						}
						else {
							script = new DynamicScript(
								path.indexOf('.') > 0 ? path.replaceAll("^(.*)\\.[^.]+$", "$1") : null,
								path.indexOf('.') > 0 ? path.replaceAll("^.*\\.([^.]+)$", "$1") : path,
								repository,
								Charset.forName("UTF-8"),
								null
							);
							((DynamicScript) script).setContent(mergeScript);
						}
						
						ScriptRuntime runtime = new ScriptRuntime(
							script, 
							environment,
							false, 
							null
						);
						// make sure the execution directory is correct
						runtime.getContext().put(SystemMethodProvider.CLI_DIRECTORY, folder.getAbsolutePath());
						runtime.setFormatter(new OutputFormatter() {
							@Override
							public void validated(GlueValidation... validations) {
								// nothing
							}
							@Override
							public void start(Script script) {
								// nothing								
							}
							@Override
							public boolean shouldExecute(Executor executor) {
								return true;
							}
							@Override
							public void print(Object... messages) {
								if (messages != null) {
									for (Object message : messages) {
										if (message != null) {
											scriptLogger.append(message).append("\n");
										}
									}
								}
							}
							@Override
							public void end(Script script, Date started, Date stopped, Exception exception) {
								// nothing								
							}
							@Override
							public void before(Executor executor) {
								// nothing								
							}
							@Override
							public void after(Executor executor) {
								// nothing
							}
						});
						
						// run the merge script
						runtime.run();
						
						merged.setState(MergeState.SUCCEEDED);
					}
					catch (Exception e) {
						StringWriter stringWriter = new StringWriter();
						PrintWriter printer = new PrintWriter(stringWriter);
						e.printStackTrace(printer);
						printer.flush();
						merged.setErrorLog(stringWriter.toString());
						merged.setState(MergeState.FAILED);
						logger.error("Could not merge: " + path, e);
					}
					// always set the script log, it might help in debugging
					finally {
						merged.setLog(scriptLogger.toString());
					}
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		// otherwise, we recurse to other directories, as long as they are not internal
		else {
			for (File child : folder.listFiles()) {
				if (EAIResourceRepository.isValidName(child.getName()) && child.isDirectory()) {
					String childPath = (path == null ? "" : path + ".") + child.getName();
					merge(child, childPath, repository, methods, resolved, environment, nodes);
				}
			}
		}
	}
	
	private static String standardEndpoint = System.getProperty("git.merge.endpoint", "https://my.nabu.be/resources/merge");
	
	private String getStandardMergeScript(GitNode node, Map<URI, String> resolved) {
		if (node.getArtifactManager() != null) {
			try {
				URI uri = new URI(URIUtils.encodeURI(standardEndpoint + "/" + node.getArtifactManager() + ".glue"));
				if (!resolved.containsKey(uri)) {
					// we first put an empty value, if loading it fails, we assume it does not exist
					resolved.put(uri, null);
					try (InputStream stream = new BufferedInputStream(uri.toURL().openStream())) {
						resolved.put(uri, new String(IOUtils.toBytes(IOUtils.wrap(stream)), Charset.forName("UTF-8")));
					}
				}
				return resolved.get(uri);
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	private Script getScript(DynamicScriptRepository repository, URI uri, Map<URI, String> resolved) {
		try {
			if (!resolved.containsKey(uri)) {
				String name = "merge" + resolved.size();
				try (InputStream input = new BufferedInputStream(uri.toURL().openStream())) {
					DynamicScript script = new DynamicScript(
						null,
						name,
						repository,
						Charset.forName("UTF-8"),
						null
					);
					byte[] bytes = IOUtils.toBytes(IOUtils.wrap(input));
					script.setContent(new String(bytes, Charset.forName("UTF-8")));
					repository.add(script);
					resolved.put(uri, name);
				}
			}
			return repository.getScript(resolved.get(uri));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static byte[] read(File file) {
		try {
			try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
				return IOUtils.toBytes(IOUtils.wrap(input));
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public byte[] read(String path, RevCommit commit) {
		try {
			try (TreeWalk treeWalk = TreeWalk.forPath(git.getRepository(), path, commit.getTree())) {
				if (treeWalk != null) {
					ObjectId blobId = treeWalk.getObjectId(0);
					try (ObjectReader objectReader = git.getRepository().newObjectReader()) {
						ObjectLoader objectLoader = objectReader.open(blobId);
						byte[] bytes = objectLoader.getBytes();
						return bytes;
					}
				}
				return null;
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * A version is an offshoot from the master branch, triggered as new commits are pushed to the master.
	 * As each commit triggers a new version, this will go up rapidly, not all of them will make it into another environment.
	 * Very likely you want to additionally tag "major" points, e.g. a production release.
	 * So "r152" might actually amount to "v1.1" if that is the point you decide to deploy to prd (which can be considered the end of a cycle)
	 * Because the "v" syntax is often used for manual tagging and we want to avoid warnings from git: warning: refname 'v1.0-qlty' is ambiguous.
	 * We use the "r" syntax
	 * 
	 * That means a version on the master branch is r1, r2, r3...
	 * 
	 * You can create a patch version, e.g. r1.1. This is optional and done as a straight branch on r1 in this case.
	 */
	public TreeSet<GitRelease> getVersions() {
		if (versions == null) {
			synchronized(this) {
				if (versions == null) {
					versions = new TreeSet<GitRelease>(new Comparator<GitRelease>() {
						@Override
						public int compare(GitRelease releaseA, GitRelease releaseB) {
							return releaseA.getVersion() - releaseB.getVersion();
						}
					});
					// by default this only gets local branches, not remote
					// to include remote:
					// new Git(repository).branchList().setListMode(ListMode.ALL).call();
					for (Ref ref : getBranches()) {
						System.out.println("found branch ref: " + ref.getName());
						String name = ref.getName();
						// e.g. refs/heads/master
						name = name.replaceAll("^.*/", "");
						// an actual release version
						if (name.matches("^r[0-9]+$")) {
							GitRelease version = getVersion(versions, name);
							version.setReference(ref.getName());
							version.setRevCommit(getCommit(ref));
						}
						else if (name.matches("^r[0-9]+\\.[0-9]+$")) {
							GitPatch patchVersion = getPatchVersion(versions, name);
							patchVersion.setReference(ref.getName());
							patchVersion.setRevCommit(getCommit(ref));
						}
						else if (name.matches("^r[0-9]+\\.[0-9]+-.+$")) {
							GitEnvironment environment = getEnvironment(versions, name);
							environment.setReference(ref.getName());
							environment.setRevCommit(getCommit(ref));
						}
					}
					for (Ref ref : getTags()) {
						String name = ref.getName();
						System.out.println("tag: " +name + " :: " + ref.getObjectId() + " :: " + ref.getLeaf());
						name = name.replaceAll("^.*/", "");
						if (name.matches("^r[0-9]+\\.[0-9]+-.+-RC[0-9]+$")) {
							GitEnvironment environment = getEnvironment(versions, name);
							String rc = ref.getName().replaceAll(".*-RC([0-9]+)$", "$1");
							GitReleaseCandidate releaseCandidate = new GitReleaseCandidate(ref.getName(), Integer.parseInt(rc));
							releaseCandidate.setRevCommit(getCommit(ref));
							environment.getReleaseCandidates().add(releaseCandidate);
						}
					}
				}
			}
		}
		return versions;
	}
	
	private GitRelease getLastVersion() {
		SortedSet<GitRelease> versions = getVersions();
		return versions.isEmpty() ? null : versions.last();
	}
	
	// can use the full name like refs/heads/master or just "master"
	private Iterable<RevCommit> getCommitsOn(String name) {
		try {
			return git.log().add(git.getRepository().resolve(name)).call();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public RevCommit getLastCommitOn(String name) {
		Iterable<RevCommit> commitsOn = getCommitsOn(name);
		Iterator<RevCommit> iterator = commitsOn.iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}

	// full name:
	// r1.0-qlty
	private static GitRelease getVersion(Collection<GitRelease> versions, String name) {
		if (!name.matches("^r[0-9]+.*")) {
			throw new IllegalArgumentException("Not a version: " + name);
		}
		name = name.replaceAll("^(r[0-9]+).*", "$1");
		int version = Integer.parseInt(name.substring(1));
		GitRelease existing = null;
		for (GitRelease possible : versions) {
			if (possible.getVersion() == version) {
				existing = possible;
				break;
			}
		}
		if (existing == null) {
			existing = new GitRelease(version);
			versions.add(existing);
		}
		return existing;
	}
	
	private static GitPatch getPatchVersion(Collection<GitRelease> versions, String name) {
		if (!name.matches("^r[0-9]+\\.[0-9]+.*")) {
			throw new IllegalArgumentException("Not a patch version: " + name);
		}
		name = name.replaceAll("^(r[0-9]+\\.[0-9]+).*", "$1");
		String[] parts = name.split("\\.");
		GitRelease release = getVersion(versions, name);
		int patch = Integer.parseInt(parts[1]);
		GitPatch existing = null;
		for (GitPatch possible : release.getPatchVersions()) {
			if (possible.getPatch() == patch) {
				existing = possible;
				break;
			}
		}
		if (existing == null) {
			existing = new GitPatch(release, patch);
			release.getPatchVersions().add(existing);
		}
		return existing;
	}
	
	private static GitEnvironment getEnvironment(Collection<GitRelease> versions, String name) {
		if (!name.matches("^r[0-9]+\\.[0-9]+.*-.+")) {
			throw new IllegalArgumentException("Not an environment version: " + name);
		}
		// remove any mention of RC or final from the name
		name = name.replaceAll("^(.*?)-(RC[0-9]+|FINAL)$", "$1");
		GitPatch patchVersion = getPatchVersion(versions, name);
		// anything after the first '-' is the name of the environment
		String environment = name.substring(name.indexOf('-') + 1);
		GitEnvironment existing = null;
		for (GitEnvironment potential : patchVersion.getEnvironments()) {
			if (potential.getName().equals(environment)) {
				existing = potential;
				break;
			}
		}
		if (existing == null) {
			existing = new GitEnvironment(patchVersion, environment);
			patchVersion.getEnvironments().add(existing);
		}
		return existing;
	}
	
	private RevCommit getCommit(Ref ref) {
		try (RevWalk revWalk = new RevWalk(git.getRepository())) {
			return revWalk.parseCommit(ref.getObjectId());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private List<Ref> getBranches() {
		try {
			return git.branchList().call();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private List<Ref> getTags() {
		try {
			return git.tagList().call();
//			return git.getRepository().getRefDatabase().getRefsByPrefix(Constants.R_TAGS);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
