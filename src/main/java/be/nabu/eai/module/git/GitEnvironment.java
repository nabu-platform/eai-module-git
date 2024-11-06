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

public class GitEnvironment extends GitReference {
	private String name;
	private SortedSet<GitReleaseCandidate> releaseCandidates;
	private GitPatch patch;
	private String branch;
	public GitEnvironment() {
		// auto construct
	}
	public GitEnvironment(GitPatch patch, String name) {
		this.patch = patch;
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	@XmlTransient
	public GitReleaseCandidate getLastReleaseCandidate() {
		SortedSet<GitReleaseCandidate> releaseCandidates = getReleaseCandidates();
		return releaseCandidates.isEmpty() ? null : releaseCandidates.last();
	}
	public SortedSet<GitReleaseCandidate> getReleaseCandidates() {
		if (releaseCandidates == null) {
			synchronized(this) {
				if (releaseCandidates == null) {
					releaseCandidates = new TreeSet<GitReleaseCandidate>(new Comparator<GitReleaseCandidate>() {
						@Override
						public int compare(GitReleaseCandidate refA, GitReleaseCandidate refB) {
							int rcA = Integer.parseInt(refA.getReference().replaceAll(".*-RC([0-9]+)$", "$1"));
							int rcB = Integer.parseInt(refB.getReference().replaceAll(".*-RC([0-9]+)$", "$1"));
							return rcA - rcB;
						}
					});
				}
			}
		}
		return releaseCandidates;
	}
	public void setReleaseCandidates(SortedSet<GitReleaseCandidate> releaseCandidates) {
		this.releaseCandidates = releaseCandidates;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(name);
		if (releaseCandidates != null && !releaseCandidates.isEmpty()) {
			builder.append(": ").append(releaseCandidates);
		}
		return builder.toString();
	}
	@XmlTransient
	public GitPatch getPatch() {
		return patch;
	}
	public void setPatch(GitPatch patch) {
		this.patch = patch;
	}
	public String getBranch() {
		return branch == null ? patch.getBranch() + "-" + name : branch;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
	
}
