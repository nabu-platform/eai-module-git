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

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "git")
public class GitInformations {
	private List<GitInformation> repositories;

	public List<GitInformation> getRepositories() {
		if (repositories == null) {
			synchronized(this) {
				if (repositories == null) {
					repositories = new ArrayList<GitInformation>();
				}
			}
		}
		return repositories;
	}

	public void setRepositories(List<GitInformation> repositories) {
		this.repositories = repositories;
	}

}
