/*
* Copyright (C) 2021 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package nabu.misc.git.types;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

import be.nabu.eai.module.git.GitNode;

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
	private String entryId, log, errorLog, description, summary, comment;
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
	public String getErrorLog() {
		return errorLog;
	}
	public void setErrorLog(String errorLog) {
		this.errorLog = errorLog;
	}
	public List<String> getTags() {
		return tags;
	}
	public void setTags(List<String> tags) {
		this.tags = tags;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public Date getModified() {
		return modified;
	}
	public void setModified(Date modified) {
		this.modified = modified;
	}
}
