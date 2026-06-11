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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.server.rest.MCPToolCallContext;
import be.nabu.eai.server.rest.MCPToolDefinition;
import be.nabu.eai.server.rest.MCPToolDefinitionContext;
import be.nabu.eai.server.rest.MCPToolProvider;
import be.nabu.eai.server.rest.MCPToolResult;
import be.nabu.libs.authentication.api.Token;

import nabu.misc.git.Services;

public class NabuVersioningCommitToolProvider implements MCPToolProvider<NabuVersioningCommitInput> {

	private static final String TOOL_NAME = "nabu_versioning_commit";

	@Override
	public MCPToolDefinition getToolDefinition(MCPToolDefinitionContext context) {
		Map<String, Object> annotations = new LinkedHashMap<String, Object>();
		annotations.put("scopes", Arrays.asList("write:nabu:artifact"));
		annotations.put("intentTemplate", "Record versioned changes [for artifacts {artifactIds}] [in namespaces {namespaces}]");

		Map<String, Object> inputSchema = new LinkedHashMap<String, Object>();
		inputSchema.put("type", "object");
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		Map<String, Object> artifactIds = propertySchema("array", "Artifact ids to include. Each id is resolved to its owning project.");
		artifactIds.put("items", schema("string"));
		properties.put("artifactIds", artifactIds);
		Map<String, Object> namespaces = propertySchema("array", "Namespaces to include. Each namespace is resolved to its owning project.");
		namespaces.put("items", schema("string"));
		properties.put("namespaces", namespaces);
		properties.put("message", propertySchema("string", "Versioning message to record."));
		properties.put("authorName", propertySchema("string", "Name to store as the author of the recorded changes."));
		properties.put("authorEmail", propertySchema("string", "Email to store as the author of the recorded changes."));
		inputSchema.put("properties", properties);
		inputSchema.put("required", Arrays.asList("message", "authorName", "authorEmail"));

		Map<String, Object> outputSchema = new LinkedHashMap<String, Object>();
		outputSchema.put("type", "object");
		Map<String, Object> outputProperties = new LinkedHashMap<String, Object>();
		outputProperties.put("projects", schema("array"));
		outputProperties.put("count", schema("integer"));
		outputProperties.put("failedCount", schema("integer"));
		outputSchema.put("properties", outputProperties);

		return new MCPToolDefinition(
			TOOL_NAME,
			"Record nabu version",
			"Record versioned changes for the requested artifact ids and/or namespaces. Requests are grouped by project and only the requested entries are included.",
			false,
			annotations,
			inputSchema,
			outputSchema,
			null
		);
	}

	@Override
	public Class<NabuVersioningCommitInput> getInputType() {
		return NabuVersioningCommitInput.class;
	}

	@Override
	public MCPToolResult invoke(NabuVersioningCommitInput input, MCPToolCallContext context) throws Exception {
		if (input == null) {
			throw new IllegalArgumentException("Missing input.");
		}
		String message = input.getMessage();
		if (message == null || message.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument 'message'.");
		}
		if (input.getAuthorName() == null || input.getAuthorName().trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument 'authorName'.");
		}
		if (input.getAuthorEmail() == null || input.getAuthorEmail().trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument 'authorEmail'.");
		}
		Map<String, Set<String>> grouped = groupByProject(input);
		if (grouped.isEmpty()) {
			throw new IllegalArgumentException("Provide at least one artifact id or namespace.");
		}

		Services services = new Services();
		List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
		int failedCount = 0;
		for (Map.Entry<String, Set<String>> entry : grouped.entrySet()) {
			Map<String, Object> project = new LinkedHashMap<String, Object>();
			project.put("projectId", entry.getKey());
			project.put("included", new ArrayList<String>(entry.getValue()));
			try {
				services.commitInternal(entry.getKey(), entry.getValue(), message, true, null, null, resolveToken(context), input.getAuthorName(), input.getAuthorEmail()).close();
				project.put("success", true);
			}
			catch (Exception e) {
				failedCount++;
				project.put("success", false);
				project.put("message", e.getMessage());
			}
			projects.add(project);
		}

		Map<String, Object> structuredContent = new LinkedHashMap<String, Object>();
		structuredContent.put("projects", projects);
		structuredContent.put("count", projects.size());
		structuredContent.put("failedCount", failedCount);
		StringBuilder summary = new StringBuilder();
		summary.append("Recorded versioned changes for ").append(projects.size() - failedCount).append(" project(s).");
		if (failedCount > 0) {
			summary.append(" Failed for ").append(failedCount).append(" project(s).");
			for (Map<String, Object> project : projects) {
				if (Boolean.FALSE.equals(project.get("success")) && project.get("message") != null) {
					summary.append("\n- ").append(project.get("projectId")).append(": ").append(project.get("message"));
				}
			}
		}
		String summaryText = summary.toString();
		return new MCPToolResult(structuredContent, summaryText, null, failedCount > 0, failedCount > 0 ? summaryText : null);
	}

	private Map<String, Set<String>> groupByProject(NabuVersioningCommitInput input) {
		Map<String, Set<String>> grouped = new LinkedHashMap<String, Set<String>>();
		addIds(grouped, input.getArtifactIds());
		addIds(grouped, input.getNamespaces());
		return grouped;
	}

	private void addIds(Map<String, Set<String>> grouped, List<String> ids) {
		if (ids == null) {
			return;
		}
		EAIResourceRepository repository = EAIResourceRepository.getInstance();
		for (String id : ids) {
			if (id == null || id.trim().isEmpty()) {
				continue;
			}
			Entry entry = repository.getEntry(id.trim());
			if (entry == null) {
				throw new IllegalArgumentException("Unknown artifact id or namespace: " + id);
			}
			Entry project = findProject(entry);
			Set<String> included = grouped.get(project.getId());
			if (included == null) {
				included = new LinkedHashSet<String>();
				grouped.put(project.getId(), included);
			}
			included.add(entry.getId());
		}
	}

	private Entry findProject(Entry entry) {
		Entry project = entry;
		while (project != null && !EAIRepositoryUtils.isProject(project)) {
			project = project.getParent();
		}
		if (project == null) {
			throw new IllegalArgumentException("Could not find owning project for: " + entry.getId());
		}
		if (!(project instanceof ResourceEntry)) {
			throw new IllegalArgumentException("Owning project is not resource-based: " + project.getId());
		}
		return project;
	}

	private Token resolveToken(MCPToolCallContext context) {
		return context == null ? null : context.getToken();
	}

	private Map<String, Object> schema(String type) {
		Map<String, Object> schema = new LinkedHashMap<String, Object>();
		schema.put("type", type);
		return schema;
	}

	private Map<String, Object> propertySchema(String type, String description) {
		Map<String, Object> schema = schema(type);
		schema.put("description", description);
		return schema;
	}
}
