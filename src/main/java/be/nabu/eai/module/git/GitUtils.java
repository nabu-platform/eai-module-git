package be.nabu.eai.module.git;

import java.util.Date;

import org.eclipse.jgit.revwalk.RevCommit;

public class GitUtils {
	
	public static Date getCommitDate(RevCommit commit) {
		return commit.getAuthorIdent() != null && commit.getAuthorIdent().getWhen() != null
				? commit.getAuthorIdent().getWhen()
				: new Date(1000L * commit.getCommitTime());
	}
}
