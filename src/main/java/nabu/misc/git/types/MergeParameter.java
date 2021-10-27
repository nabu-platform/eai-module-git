package nabu.misc.git.types;

import java.util.List;

public class MergeParameter {
	// the category of the parameter, this allows the scripts to categorize its own parameters without resorting to creative naming
	// the name of the parameter
	// a user-friendly title
	// the type, so we can offer the user a specific form interface
	// its current value (in the current branch)
	// and the previous value (in the previous branch of this environment, if any)
	// and the raw value from the master (probably the dev value)
	private String category, name, title, type, current, previous, raw;
	// the enumeration of possible values
	private List<String> enumeration;
	// a description for the user
	private String description;
	// the priority of this parameter
	// if there are a lot of parameters, the priority can allow a user to pinpoint the important bits
	// higher priority means it will be listed more towards the top
	private int priority;
	// whether or not the parameter is optional and/or (potentially) encrypted
	private boolean optional, encrypted;
	// this keeps track of whether the _raw_ value was changed
	// usually when you change something in an environment specific field, it needs a similar change in other environments
	// we already flag the entire entry if its been changed, but we can also flag the particular fields
	private boolean changed;
	// you can choose that for a particular build, a particular parameter is not relevant
	private boolean ignore;
	// this can contain queries to either show or hide the field, depending on other state
	// note that this should be glue syntax but translateable to javascript as that is the most likely scenario for display
	private String show, hide;
	// you can illustrate the default value
	private String defaultValue;
	// whether or not it is a list
	private boolean list;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCurrent() {
		return current;
	}
	public void setCurrent(String current) {
		this.current = current;
	}
	public String getPrevious() {
		return previous;
	}
	public void setPrevious(String previous) {
		this.previous = previous;
	}
	public String getRaw() {
		return raw;
	}
	public void setRaw(String raw) {
		this.raw = raw;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public boolean isOptional() {
		return optional;
	}
	public void setOptional(boolean optional) {
		this.optional = optional;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public boolean isEncrypted() {
		return encrypted;
	}
	public void setEncrypted(boolean encrypted) {
		this.encrypted = encrypted;
	}
	public List<String> getEnumeration() {
		return enumeration;
	}
	public void setEnumeration(List<String> enumeration) {
		this.enumeration = enumeration;
	}
	public boolean isChanged() {
		return changed;
	}
	public void setChanged(boolean changed) {
		this.changed = changed;
	}
	public int getPriority() {
		return priority;
	}
	public void setPriority(int priority) {
		this.priority = priority;
	}
	public boolean isIgnore() {
		return ignore;
	}
	public void setIgnore(boolean ignore) {
		this.ignore = ignore;
	}
	public String getShow() {
		return show;
	}
	public void setShow(String show) {
		this.show = show;
	}
	public String getHide() {
		return hide;
	}
	public void setHide(String hide) {
		this.hide = hide;
	}
	public String getDefaultValue() {
		return defaultValue;
	}
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}
	public boolean isList() {
		return list;
	}
	public void setList(boolean list) {
		this.list = list;
	}
}