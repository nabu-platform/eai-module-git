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
