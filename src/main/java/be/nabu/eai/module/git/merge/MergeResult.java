package be.nabu.eai.module.git.merge;

import java.util.List;

import be.nabu.libs.types.api.annotation.ComplexTypeDescriptor;

@ComplexTypeDescriptor(name = "merge")
public class MergeResult {
	private List<MergeEntry> entries;

	public List<MergeEntry> getEntries() {
		return entries;
	}
	public void setEntries(List<MergeEntry> entries) {
		this.entries = entries;
	}
}
