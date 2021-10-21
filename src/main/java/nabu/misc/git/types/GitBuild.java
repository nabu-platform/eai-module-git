package nabu.misc.git.types;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import be.nabu.eai.module.git.GitRelease;

@XmlRootElement
public class GitBuild {
	private List<GitRelease> releases;

	public List<GitRelease> getReleases() {
		return releases;
	}

	public void setReleases(List<GitRelease> releases) {
		this.releases = releases;
	}
}
