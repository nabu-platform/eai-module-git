package be.nabu.eai.module.git.merge;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

import be.nabu.eai.module.git.GitNode;

// TODO: a state? the result of the merge
public class MergeEntry {
	
	public enum MergeState {
		// the merge was succesfull
		SUCCEEDED,
		// the merge failed, this is due to an exception from the merge script
		FAILED,
		// more information is required to finalize the merge
		PENDING
	}
	
	private List<String> tags;
	// the id of the entry (only ever nodes?)
	// we also copy any description to the merge state, this can aid the user in determining what it is about
	private String entryId, log, description, summary, comment;
	// the parameters that the user can play with if needed
	private List<MergeParameter> parameters;
	// whether or not the user should really check this
	// this is based on whether or not the node was modified since the last release (like the original deployer)
	private boolean changed;
	private MergeState state;
	private Date created, modified;
	
	public String getEntryId() {
		return entryId;
	}
	public void setEntryId(String entryId) {
		this.entryId = entryId;
	}
	public List<MergeParameter> getParameters() {
		if (parameters == null) {
			parameters = new ArrayList<MergeParameter>();
		}
		return parameters;
	}
	public void setParameters(List<MergeParameter> parameters) {
		this.parameters = parameters;
	}
	public String getLog() {
		return log;
	}
	public void setLog(String log) {
		this.log = log;
	}
	public boolean isChanged() {
		return changed;
	}
	public void setChanged(boolean changed) {
		this.changed = changed;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public MergeState getState() {
		return state;
	}
	public void setState(MergeState state) {
		this.state = state;
	}
	@XmlTransient
	public void setNode(GitNode node) {
		comment = node.getComment();
		description = node.getDescription();
		summary = node.getSummary();
		created = node.getCreated();
		modified = node.getLastModified();
		tags = node.getTags();
	}
}
