package nabu.misc.git.types;

import java.util.Date;
import java.util.List;

import be.nabu.libs.types.api.annotation.ComplexTypeDescriptor;
import nabu.misc.git.types.MergeEntry.MergeState;

@ComplexTypeDescriptor(name = "merge", propOrder = {"entries", "started", "stopped", "state"})
public class MergeResult {
	private Date started, stopped;
	private List<MergeEntry> entries;
	private MergeState state;

	public List<MergeEntry> getEntries() {
		return entries;
	}
	public void setEntries(List<MergeEntry> entries) {
		this.entries = entries;
	}
	public Date getStarted() {
		return started;
	}
	public void setStarted(Date started) {
		this.started = started;
	}
	public Date getStopped() {
		return stopped;
	}
	public void setStopped(Date stopped) {
		this.stopped = stopped;
	}
	public MergeState getState() {
		return state;
	}
	public void setState(MergeState state) {
		this.state = state;
	}
}
