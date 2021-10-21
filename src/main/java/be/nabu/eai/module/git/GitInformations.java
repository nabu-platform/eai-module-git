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
