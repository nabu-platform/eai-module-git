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

public class GitReleaseCandidate extends GitReference {
	
	private int candidate;
	
	public GitReleaseCandidate() {
		// auto construct
	}
	
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
