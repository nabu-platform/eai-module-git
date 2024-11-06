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

package be.nabu.eai.module.git;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlTransient;

public class GitRelease extends GitReference {
	private int version;
	private TreeSet<GitPatch> patchVersions;
	private String branch;
	
	public GitRelease() {
		// auto construct
	}
	
	public GitRelease(int version) {
		this.version = version;
	}
	public int getVersion() {
		return version;
	}
	public void setVersion(int version) {
		this.version = version;
	}
	
	@XmlTransient
	public GitPatch getLastPatch() {
		SortedSet<GitPatch> patchVersions = getPatchVersions();
		return !patchVersions.isEmpty() ? patchVersions.last() : null;
	}
	
	public TreeSet<GitPatch> getPatchVersions() {
		if (patchVersions == null) {
			synchronized(this) {
				if (patchVersions == null) {		
					patchVersions = new TreeSet<GitPatch>(new Comparator<GitPatch>() {
						@Override
						public int compare(GitPatch o1, GitPatch o2) {
							return o1.getPatch() - o2.getPatch();
						}
					});
				}
			}
		}
		return patchVersions;
	}
	public void setPatchVersions(TreeSet<GitPatch> patchVersions) {
		this.patchVersions = patchVersions;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("r" + version).append(" {");
		if (patchVersions != null && !patchVersions.isEmpty()) {
			for (GitPatch patch : patchVersions) {
				builder.append("\n\t").append(patch.toString().replaceAll("(?m)^", "\t").trim());
			}
			builder.append("\n");
		}
		builder.append("}");
		return builder.toString();
	}
	
	public String getBranch() {
		return branch == null ? "r" + version : branch;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
}
