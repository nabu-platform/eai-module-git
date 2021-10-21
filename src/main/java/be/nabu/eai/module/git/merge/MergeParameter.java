package be.nabu.eai.module.git.merge;

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
	// whether or not the parameter is optional and/or (potentially) encrypted
	private boolean optional, encrypted;

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
}