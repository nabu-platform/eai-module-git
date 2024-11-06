/*
* Copyright (C) 2021 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
