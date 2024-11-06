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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

public class GitPatch extends GitReference {
	private int patch;
	private List<GitEnvironment> environments;
	private GitRelease release;
	private String branch;
	public GitPatch() {
		// auto construct
	}
	public GitPatch(GitRelease release, int patch) {
		this.release = release;
		this.patch = patch;
	}
	public int getPatch() {
		return patch;
	}
	public void setPatch(int patch) {
		this.patch = patch;
	}
	public List<GitEnvironment> getEnvironments() {
		if (environments == null) {
			synchronized(this) {
				if (environments == null) {
					environments = new ArrayList<GitEnvironment>();
				}
			}
		}
		return environments;
	}
	public void setEnvironments(List<GitEnvironment> environments) {
		this.environments = environments;
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("p" + patch).append(" {");
		if (environments != null && !environments.isEmpty()) {
			for (GitEnvironment environment : environments) {
				builder.append("\n\t").append(environment.toString().trim());
			}
			builder.append("\n");
		}
		builder.append("}");
		return builder.toString();
	}
	@XmlTransient
	public GitRelease getRelease() {
		return release;
	}
	public void setRelease(GitRelease release) {
		this.release = release;
	}
	
	public String getBranch() {
		return branch == null ? release.getBranch() + "." + patch : branch;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
}
