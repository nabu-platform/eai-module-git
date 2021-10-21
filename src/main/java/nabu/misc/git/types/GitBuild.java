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
