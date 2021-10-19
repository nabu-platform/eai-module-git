package be.nabu.eai.module.git;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class GitEnvironment extends GitReference {
	private String name;
	private SortedSet<GitReleaseCandidate> releaseCandidates;
	private GitPatch patch;
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
	public GitPatch getPatch() {
		return patch;
	}
	public void setPatch(GitPatch patch) {
		this.patch = patch;
	}
	
	public String getBranch() {
		return patch.getBranch() + "-" + name;
	}
}
