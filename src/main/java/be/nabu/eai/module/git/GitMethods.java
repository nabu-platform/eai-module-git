package be.nabu.eai.module.git;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.methods.v2.StringMethods;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.xml.XMLMethods;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.KeyValuePair;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.definition.xml.XMLDefinitionUnmarshaller;
import be.nabu.libs.types.properties.EnvironmentSpecificProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.utils.KeyValuePairImpl;
import nabu.misc.git.types.MergeEntry;
import nabu.misc.git.types.MergeParameter;
import nabu.misc.git.types.MergeResult;

//TODO: document validation: given an XML file and a structure, validate it (to detect configuration problems)
// in most cases however, the validation rules will be hidden in java code, so needs to be replicated to glue

// TODO: encryption: some fields are encrypted, need encrypt/decrypt logic
@MethodProviderClass(namespace = "git")
public class GitMethods {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
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
	
	// list all the merge parameters based on the definition (e.g. for config artifacts)
	// the lambda getter allows you to choose how you resolve them (e.g. get from actual xml, get from key/value list...)
	public List<MergeParameter> parameters(@GlueParam(name = "definition") String definition, @GlueParam(name = "getter") Lambda getter, @GlueParam(name = "forceAll") Boolean forceAll) throws IOException, ParseException {
		Structure structure = getDefinition(definition);
		List<MergeParameter> parameters = new ArrayList<MergeParameter>();
		recursiveParameters(structure, null, getter, forceAll, parameters);
		return parameters;
	}

	private Structure getDefinition(String definition) throws IOException, ParseException {
		XMLDefinitionUnmarshaller unmarshaller = new XMLDefinitionUnmarshaller();
		unmarshaller.setIgnoreUnknown(true);
		Structure structure = (Structure) unmarshaller.unmarshal(new ByteArrayInputStream(definition.getBytes(Charset.forName("UTF-8"))));
		return structure;
	}
	
	private void recursiveParameters(ComplexType current, String path, Lambda getter, boolean forceAll, List<MergeParameter> parameters) {
		for (Element<?> child : TypeUtils.getAllChildren(current)) {
			String childPath = path == null ? child.getName() : path + "/" + child.getName();
			Value<Integer> minOccurs = child.getProperty(MinOccursProperty.getInstance());
			boolean optional = minOccurs != null && minOccurs.getValue() == 0;
			// if we have a simple type, just add it
			if (child.getType() instanceof SimpleType) {
				// must be environment specific
				Value<Boolean> property = child.getProperty(EnvironmentSpecificProperty.getInstance());
				if (forceAll || (property != null && property.getValue())) {
					Object raw = GlueUtils.calculate(getter, ScriptRuntime.getRuntime(), Arrays.asList(childPath));
					if (child.getType().isList(child.getProperties()) && raw != null) {
						raw = StringMethods.join(", ", raw);
					}
					MergeParameter parameter = parameter(childPath, null, null, null, getType((SimpleType<?>) child.getType()), false, optional, raw == null ? null : raw.toString(), null, null, null, null);
					if (child.getType().isList(child.getProperties())) {
						parameter.setList(true);
					}
					parameters.add(parameter);
				}
			}
			// we only recurse non list complex types
			else if (!child.getType().isList(child.getProperties())) {
				recursiveParameters((ComplexType) child.getType(), childPath, getter, forceAll, parameters);
			}
		}
	}
	
	private String getType(SimpleType<?> type) {
		if (Long.class.isAssignableFrom(type.getInstanceClass()) || Integer.class.isAssignableFrom(type.getInstanceClass()) || Short.class.isAssignableFrom(type.getInstanceClass())) {
			return "long";
		}
		else if (Date.class.isAssignableFrom(type.getInstanceClass())) {
			return "date";
		}
		else if (Boolean.class.isAssignableFrom(type.getInstanceClass())) {
			return "boolean";
		}
		else {
			return "string";
		}
	}
	
	// we want key value pairs here, so the property should have a "key" and a "value" field
	public Object propertiesToObject(@GlueParam(name = "definition") String definition, @GlueParam(name = "properties") Object...properties) throws IOException, ParseException {
		String separator = "/";
		Structure structure = getDefinition(definition);
		ComplexContent newInstance = ((ComplexType) structure).newInstance();
		if (properties != null) {
			Iterable<?> series = GlueUtils.toSeries(properties);
			for (Object property : series) {
				if (property == null) {
					continue;
				}
				if (!(property instanceof ComplexContent)) {
					property = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(property);
				}
				ComplexContent content = (ComplexContent) property;
				if (content == null || content.get("key") == null) {
					logger.error("Could not convert property to complex content: " + property);
					continue;
				}
				String key = content.get("key").toString();
				if (separator == null || !separator.equals("/")) {
					key = key.replace(separator == null ? "." : separator, "/");
				}
				if (newInstance.getType().get(key) != null) {
					newInstance.set(key, content.get("value"));
				}
			}
		}
		return newInstance;
	}
	@SuppressWarnings("unchecked")
	public List<KeyValuePair> objectToProperties(Object object) throws IOException, SAXException, ParserConfigurationException {
		List<KeyValuePair> properties = new ArrayList<KeyValuePair>();
		if (object != null) {
			if (!(object instanceof ComplexContent)) {
				object = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(object);
			}
			toProperties((ComplexContent) object, properties, null, "/");
		}
		return properties;
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
			@GlueParam(name = "show") String show,
			@GlueParam(name = "hide") String hide,
			@GlueParam(name = "default") String defaultValue,
			// possible values
			@GlueParam(name = "enumeration") String...values) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Name is mandatory");
		}
		// empty strings are considered the same as null
		if (raw instanceof String && ((String) raw).isEmpty()) {
			raw = null;
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
				result.setIgnore(previousParameter.isIgnore());
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
		result.setDefaultValue(defaultValue);
		result.setShow(show);
		result.setHide(hide);
		return result;
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
