package be.nabu.eai.module.git.developer;

import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.resources.api.ResourceContainer;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class GitContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		Menu menu = new Menu("Versioning");
		if (entry instanceof ResourceEntry) {
			MenuItem commit = new MenuItem("Commit");
			
			if (EAIRepositoryUtils.isProject(entry)) {
				MenuItem release = new MenuItem("Release");
				
				MenuItem build = new MenuItem("Build");
			}
		}
		
		return menu.getItems().isEmpty() ? null : menu;
	}

}
