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

package be.nabu.eai.module.git.developer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.Future;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.authentication.impl.BasicPrincipalImpl;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.base.ValueImpl;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

public class GitContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		Menu menu = new Menu("Versioning");
		menu.setGraphic(MainController.getInstance().loadFixedSizeGraphic("git/git.png"));
		if (entry instanceof ResourceEntry) {
			MenuItem commit = new MenuItem("Commit");
			commit.setGraphic(MainController.getInstance().loadFixedSizeGraphic("git/git-commit.png"));
			
			final Token token = MainController.getInstance().getProfile().getUsername() == null 
				? SystemPrincipal.ROOT
				: new BasicPrincipalImpl(MainController.getInstance().getProfile().getUsername(), MainController.getInstance().getProfile().getPassword(), "developer");
			
			commit.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					SimpleProperty<String> message = new SimpleProperty<String>("Message", String.class, false);
					message.setLarge(true);
					SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new HashSet(Arrays.asList(message)));
					EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Commit", new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							try {
								String message = updater.getValue("Message");
								Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.misc.git.Services.commit");
								if (service != null) {
									ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
									input.set("id", entry.getId());
									input.set("message", message);
									Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(token), input);
									ServiceResult serviceResult = run.get();
									if (serviceResult.getException() != null) {
										MainController.getInstance().notify(serviceResult.getException());
									}
									else {
										Confirm.confirm(ConfirmType.INFORMATION, "Committed", "Successfully commited: " + entry.getId(), null);
									}
								}
							}
							catch (Exception e) {
								MainController.getInstance().notify(e);
							}
						}
					}, false);
				}
			});
			
			menu.getItems().add(commit);
			if (EAIRepositoryUtils.isProject(entry)) {
				MenuItem release = new MenuItem("Release");
				release.setGraphic(MainController.getInstance().loadFixedSizeGraphic("git/git-release.png"));
				release.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						SimpleProperty<String> message = new SimpleProperty<String>("Message", String.class, false);
						message.setLarge(true);
						SimpleProperty<Boolean> commitAll = new SimpleProperty<Boolean>("Commit All", Boolean.class, false);
						SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new HashSet(Arrays.asList(message, commitAll)), new ValueImpl<Boolean>(commitAll, false));
						EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Release", new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {
								try {
									String message = updater.getValue("Message");
									Boolean commit = updater.getValue("Commit All");
									Service service = (Service) EAIResourceRepository.getInstance().resolve("nabu.misc.git.Services.release");
									if (service != null) {
										ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
										input.set("id", entry.getId());
										input.set("message", message);
										input.set("commitAll", commit);
										Future<ServiceResult> run = EAIResourceRepository.getInstance().getServiceRunner().run(service, EAIResourceRepository.getInstance().newExecutionContext(token), input);
										ServiceResult serviceResult = run.get();
										if (serviceResult.getException() != null) {
											MainController.getInstance().notify(serviceResult.getException());
										}
										else {
											Confirm.confirm(ConfirmType.INFORMATION, "Releases", "Successfully released: " + entry.getId(), null);
										}
									}
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
						}, false);
					}
				});
				menu.getItems().add(release);
			}
		}
		
		return menu.getItems().isEmpty() ? null : menu;
	}

}
