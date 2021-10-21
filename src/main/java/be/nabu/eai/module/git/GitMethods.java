package be.nabu.eai.module.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.xml.sax.SAXException;

import be.nabu.eai.module.git.merge.MergeEntry;
import be.nabu.eai.module.git.merge.MergeResult;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.xml.XMLMethods;
import be.nabu.eai.module.git.merge.MergeParameter;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.utils.KeyValuePairImpl;

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
			@GlueParam(name = "raw") String raw,
			@GlueParam(name = "priority") Integer priority,
			// possible values
			@GlueParam(name = "enumeration") String...values) {
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
				result.setCurrent(previousParameter.getCurrent());
				result.setChanged((raw == null && previousParameter.getRaw() != null) || (raw != null && !raw.equals(previousParameter.getRaw())));
			}
			else {
				// inherit from dev
				result.setCurrent(raw);
				// there is no previous parameter, from the merging perspective, its been changed
				// it could also be that you added a new field that did not exist before, it is again "new"
				result.setChanged(true);
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
		// on the second pass, you are no longer seeing the actual raw value
		if (raw != null && result.getRaw() == null) {
			result.setRaw(raw);
		}
		if (values != null && values.length > 0) {
			result.setEnumeration(Arrays.asList(values));
		}
		if (priority != null) {
			result.setPriority(priority);
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
	
	// apply tags to the entry (for filtering etc in merge view)
	public void tag(String...tag) {
		MergeEntry merged = merged();
		merged.setTags(tag == null || tag.length == 0 ? null : new ArrayList<String>(Arrays.asList(tag)));
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
	
	public List<KeyValuePair> toKeyValue(Object object) throws IOException, SAXException, ParserConfigurationException {
		List<KeyValuePair> properties = new ArrayList<KeyValuePair>();
		if (object != null) {
			ComplexContent content = (ComplexContent) XMLMethods.objectify(object);
			toProperties(content, properties, null, "/");
		}
		return properties;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void toProperties(ComplexContent content, List<KeyValuePair> properties, String path, String separator) {
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			String childPath = path == null ? child.getName() : path + separator + child.getName();
			java.lang.Object value = content.get(child.getName());
			if (value != null) {
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
				if (collectionHandler != null) {
					for (java.lang.Object index : collectionHandler.getIndexes(value)) {
						java.lang.Object singleValue = collectionHandler.get(value, index);
						if (singleValue != null) {
							String singlePath = childPath;
							if (index instanceof Number) {
								singlePath += "[" + index + "]";
							}
							else {
								singlePath += "[\"" + index + "\"]";
							}
							singleToProperties(child, singleValue, properties, singlePath, separator);
						}
					}
				}
				else {
					singleToProperties(child, value, properties, childPath, separator);
				}
			}
		}
	}
	
	private void singleToProperties(Element<?> child, java.lang.Object value, List<KeyValuePair> properties, String childPath, String separator) {
		if (value instanceof ComplexContent) {
			toProperties((ComplexContent) value, properties, childPath, separator);
		}
		else {
			properties.add(new KeyValuePairImpl(childPath, value instanceof String 
				? (String) value
				: (java.lang.String) TypeConverterFactory.getInstance().getConverter().convert(value, child, new BaseTypeInstance(new be.nabu.libs.types.simple.String()))));
		}
	}
}
