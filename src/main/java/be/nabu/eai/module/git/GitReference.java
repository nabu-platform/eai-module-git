package be.nabu.eai.module.git;

import java.util.Date;

import javax.xml.bind.annotation.XmlTransient;

import org.eclipse.jgit.revwalk.RevCommit;

public class GitReference {
	// the reference name, e.g. refs/heads/master
	private String reference;
	private String commit;
	private Date date;
	private String author, email;
	private RevCommit revCommit;
	
	public GitReference() {
		// auto
	}
	public GitReference(String reference) {
		this.reference = reference;
	}

	public String getReference() {
		return reference;
	}
	public void setReference(String reference) {
		this.reference = reference;
	}
	
	@Override
	public String toString() {
		return reference;
	}
	
	public String getCommit() {
		return commit;
	}
	public void setCommit(String commit) {
		this.commit = commit;
	}
	
	
	@XmlTransient
	public RevCommit getRevCommit() {
		return revCommit;
	}
	public void setRevCommit(RevCommit commit) {
		date = GitUtils.getCommitDate(commit);
		
		author = commit.getAuthorIdent().getName();
		email = commit.getAuthorIdent().getEmailAddress();
		this.commit = commit.getId().getName();
		this.revCommit = commit;
		
		System.out.println("set: " + date + " :: " + author + " :: " + this.commit);
	}
	
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
}
