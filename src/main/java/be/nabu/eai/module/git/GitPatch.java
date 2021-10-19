package be.nabu.eai.module.git;

import java.util.ArrayList;
import java.util.List;

public class GitPatch extends GitReference {
	private int patch;
	private List<GitEnvironment> environments;
	private GitRelease release;
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
	
	public GitRelease getRelease() {
		return release;
	}
	public void setRelease(GitRelease release) {
		this.release = release;
	}
	
	public String getBranch() {
		return release.getBranch() + "." + patch;
	}
}
