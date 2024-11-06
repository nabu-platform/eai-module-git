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

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import be.nabu.eai.module.git.GitRelease;

@XmlRootElement
public class GitBuild {
	private String name;
	private List<GitRelease> releases;

	public List<GitRelease> getReleases() {
		return releases;
	}

	public void setReleases(List<GitRelease> releases) {
		this.releases = releases;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
