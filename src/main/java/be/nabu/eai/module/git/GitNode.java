package be.nabu.eai.module.git;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "node")
public class GitNode {
	private long version;
	private Date lastModified, created, deprecated;
	private String environmentId;
	private boolean hidden;
	private String name, description, comment, summary, artifactManager;
	private List<String> tags;
	private String mergeScript;
	@XmlAttribute
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	@XmlAttribute
	public Date getLastModified() {
		return lastModified;
	}
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	@XmlAttribute
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public Date getDeprecated() {
		return deprecated;
	}
	public void setDeprecated(Date deprecated) {
		this.deprecated = deprecated;
	}
	public String getEnvironmentId() {
		return environmentId;
	}
	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}
	public boolean isHidden() {
		return hidden;
	}
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
	public List<String> getTags() {
		return tags;
	}
	public void setTags(List<String> tags) {
		this.tags = tags;
	}
	public String getMergeScript() {
		return mergeScript;
	}
	public void setMergeScript(String mergeScript) {
		this.mergeScript = mergeScript;
	}
	@XmlAttribute
	public String getArtifactManager() {
		return artifactManager;
	}
	public void setArtifactManager(String artifactManager) {
		this.artifactManager = artifactManager;
	}
}
