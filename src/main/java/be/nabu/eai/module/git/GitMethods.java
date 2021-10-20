package be.nabu.eai.module.git;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

import be.nabu.eai.module.git.merge.MergeEntry;
import be.nabu.eai.module.git.merge.MergeResult;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.eai.module.git.merge.MergeParameter;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

//TODO: document validation: given an XML file and a structure, validate it (to detect configuration problems)
// in most cases however, the validation rules will be hidden in java code, so needs to be replicated to glue

// TODO: encryption: some fields are encrypted, need encrypt/decrypt logic
@MethodProviderClass(namespace = "git")
public class GitMethods {
	
	private GitRepository repository;
	private RevCommit previousCommit;
	private MergeResult result, previousResult;
	private String currentEntryId;

	public GitMethods(GitRepository repository, MergeResult result, MergeResult previousResult, RevCommit previousCommit) {
		this.repository = repository;
		this.result = result;
		this.previousResult = previousResult;
		this.previousCommit = previousCommit;
	}
	
	public byte [] previousContent(String path) {
		// if we are in a certain context, get it from the correct context
		if (currentEntryId != null) {
			// we must remove the leading project name because it is _not_ part of the git repo!
			path = currentEntryId.replace(".", "/").replaceAll("^[^/]+/", "") + "/" + path.replaceAll("^[/]+", "");
		}
		return previousCommit == null ? null : repository.read(path, previousCommit);
	}
	
	public MergeParameter previousParameter(String name) {
		MergeEntry entry = previousEntry();
		if (entry != null) {
			for (MergeParameter parameter : entry.getParameters()) {
				if (parameter.getName().equals(name)) {
					return parameter;
				}
			}
		}
		return null;
	}
	
	public MergeParameter parameter(@GlueParam(name = "name") String name,
			@GlueParam(name = "category") String category,
			@GlueParam(name = "title") String title,
			@GlueParam(name = "description") String description,
			@GlueParam(name = "type") String type,
			@GlueParam(name = "encrypted") Boolean encrypted,
			@GlueParam(name = "optional") Boolean optional,
			@GlueParam(name = "raw") String raw) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Name is mandatory");
		}
		MergeEntry merged = merged();
		MergeParameter result = null;
		for (MergeParameter parameter : merged.getParameters()) {
			if (parameter.getName().equals(name)) {
				result = parameter;
				break;
			}
		}
		if (result == null) {
			result = new MergeParameter();
			result.setName(name);
			merged.getParameters().add(result);
			MergeParameter previousParameter = previousParameter(name);
			if (previousParameter != null) {
				result.setCategory(previousParameter.getCategory());
				result.setPrevious(previousParameter.getCurrent());
				result.setDescription(previousParameter.getDescription());
				result.setTitle(previousParameter.getTitle());
				result.setEncrypted(previousParameter.isEncrypted());
				result.setOptional(previousParameter.isOptional());
				result.setType(previousParameter.getType());
			}
		}
		if (description != null) {
			result.setDescription(description);
		}
		if (title != null) {
			result.setTitle(title);
		}
		if (category != null) {
			result.setCategory(category);
		}
		if (type != null) {
			result.setType(type);
		}
		if (optional != null) {
			result.setOptional(optional);
		}
		if (encrypted != null) {
			result.setEncrypted(encrypted);
		}
		if (raw != null) {
			result.setRaw(raw);
		}
		return result;
	}
	
	public List<MergeParameter> parameters() {
		return merged().getParameters();
	}
	
	protected MergeEntry previousEntry() {
		if (previousResult != null && previousResult.getEntries() != null && currentEntryId != null) {
			for (MergeEntry mergeEntry : previousResult.getEntries()) {
				if (currentEntryId.equals(mergeEntry.getEntryId())) {
					return mergeEntry;
				}
			}
		}
		return null;
	}
	
	protected MergeEntry merged() {
		MergeEntry entry = null;
		if (result != null && result.getEntries() != null && currentEntryId != null) {
			for (MergeEntry mergeEntry : result.getEntries()) {
				if (currentEntryId.equals(mergeEntry.getEntryId())) {
					entry = mergeEntry;
					break;
				}
			}
		}
		if (entry == null && currentEntryId != null) {
			entry = new MergeEntry();
			entry.setEntryId(currentEntryId);
			if (result.getEntries() == null) {
				result.setEntries(new ArrayList<MergeEntry>());
			}
			result.getEntries().add(entry);
		}
		return entry;
	}

	protected String getCurrentEntryId() {
		return currentEntryId;
	}
	protected void setCurrentEntryId(String currentEntryId) {
		this.currentEntryId = currentEntryId;
	}
}
