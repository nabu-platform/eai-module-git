package be.nabu.eai.module.git.merge;

import java.util.Date;
import java.util.List;

public class BuildInformation {
	private int release, patch, rc;
	private String environment, tag;
	private Date built;
	// the project could have dependencies that are not included in this deployment, they are listed here
	private List<String> dependencies;
	public int getRelease() {
		return release;
	}
	public void setRelease(int release) {
		this.release = release;
	}
	public int getPatch() {
		return patch;
	}
	public void setPatch(int patch) {
		this.patch = patch;
	}
	public int getRc() {
		return rc;
	}
	public void setRc(int rc) {
		this.rc = rc;
	}
	public String getEnvironment() {
		return environment;
	}
	public void setEnvironment(String environment) {
		this.environment = environment;
	}
	public String getTag() {
		return tag;
	}
	public void setTag(String tag) {
		this.tag = tag;
	}
	public Date getBuilt() {
		return built;
	}
	public void setBuilt(Date built) {
		this.built = built;
	}
	public List<String> getDependencies() {
		return dependencies;
	}
	public void setDependencies(List<String> dependencies) {
		this.dependencies = dependencies;
	}
}
