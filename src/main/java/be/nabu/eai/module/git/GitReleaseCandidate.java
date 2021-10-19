package be.nabu.eai.module.git;

public class GitReleaseCandidate extends GitReference {
	
	private int candidate;
	
	public GitReleaseCandidate(String reference, int candidate) {
		super(reference);
		this.candidate = candidate;
	}

	@Override
	public String toString() {
		return getReference().replaceAll(".*-(RC[0-9]+)$", "$1");
	}

	public int getCandidate() {
		return candidate;
	}

	public void setCandidate(int candidate) {
		this.candidate = candidate;
	}
}
