package be.nabu.eai.module.git;

public class GitReleaseCandidate extends GitReference {
	
	public GitReleaseCandidate(String reference) {
		super(reference);
	}

	@Override
	public String toString() {
		return getReference().replaceAll(".*-(RC[0-9]+)$", "$1");
	}
}
