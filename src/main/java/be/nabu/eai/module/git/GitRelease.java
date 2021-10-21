package be.nabu.eai.module.git;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class GitRelease extends GitReference {
	private int version;
	private TreeSet<GitPatch> patchVersions;
	private String branch;
	
	public GitRelease(int version) {
		this.version = version;
	}
	public int getVersion() {
		return version;
	}
	public void setVersion(int version) {
		this.version = version;
	}
	
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
