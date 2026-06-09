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
import be.nabu.eai.server.rest.MCPToolCallContext;
import be.nabu.eai.server.rest.MCPToolDefinition;
import be.nabu.eai.server.rest.MCPToolDefinitionContext;
import be.nabu.eai.server.rest.MCPToolProvider;
import be.nabu.eai.server.rest.MCPToolResult;
import be.nabu.libs.authentication.api.Token;

import nabu.misc.git.Services;

public class NabuVersioningReleaseToolProvider implements MCPToolProvider<NabuVersioningReleaseInput> {

	private static final String TOOL_NAME = "nabu_versioning_release";

	@Override
	public MCPToolDefinition getToolDefinition(MCPToolDefinitionContext context) {
		Map<String, Object> annotations = new LinkedHashMap<String, Object>();
		annotations.put("scopes", Arrays.asList("write:nabu:project"));
		annotations.put("intentTemplate", "Create version release for projects {projectIds}");

		Map<String, Object> inputSchema = new LinkedHashMap<String, Object>();
		inputSchema.put("type", "object");
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		Map<String, Object> projectIds = propertySchema("array", "Project ids to release.");
		projectIds.put("items", schema("string"));
		properties.put("projectIds", projectIds);
		properties.put("message", propertySchema("string", "Release message to store as release metadata."));
		inputSchema.put("properties", properties);
		inputSchema.put("required", Arrays.asList("projectIds", "message"));

		Map<String, Object> outputSchema = new LinkedHashMap<String, Object>();
		outputSchema.put("type", "object");
		Map<String, Object> outputProperties = new LinkedHashMap<String, Object>();
		outputProperties.put("projects", schema("array"));
		outputProperties.put("count", schema("integer"));
		outputProperties.put("failedCount", schema("integer"));
		outputSchema.put("properties", outputProperties);

		return new MCPToolDefinition(
			TOOL_NAME,
			"Release nabu version",
			"Create a version release for each requested project.",
			false,
			annotations,
			inputSchema,
			outputSchema,
			null
		);
	}

	@Override
	public Class<NabuVersioningReleaseInput> getInputType() {
		return NabuVersioningReleaseInput.class;
	}

	@Override
	public MCPToolResult invoke(NabuVersioningReleaseInput input, MCPToolCallContext context) throws Exception {
		if (input == null) {
			throw new IllegalArgumentException("Missing input.");
		}
		if (input.getMessage() == null || input.getMessage().trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument 'message'.");
		}
		Set<String> projectIds = normalizeProjectIds(input.getProjectIds());
		if (projectIds.isEmpty()) {
			throw new IllegalArgumentException("Provide at least one project id.");
		}

		Services services = new Services();
		List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
		int failedCount = 0;
		for (String projectId : projectIds) {
			Map<String, Object> project = new LinkedHashMap<String, Object>();
			project.put("projectId", projectId);
			try {
				services.releaseInternal(projectId, input.getMessage(), null, null, false, resolveToken(context));
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
		String summary = "Created version release for " + (projects.size() - failedCount) + " project(s).";
		if (failedCount > 0) {
			summary += " Failed for " + failedCount + " project(s).";
		}
		return new MCPToolResult(structuredContent, summary, null, failedCount > 0, failedCount > 0 ? summary : null);
	}

	private Set<String> normalizeProjectIds(List<String> ids) {
		Set<String> projectIds = new LinkedHashSet<String>();
		if (ids == null) {
			return projectIds;
		}
		EAIResourceRepository repository = EAIResourceRepository.getInstance();
		for (String id : ids) {
			if (id == null || id.trim().isEmpty()) {
				continue;
			}
			Entry entry = repository.getEntry(id.trim());
			if (entry == null || !EAIRepositoryUtils.isProject(entry)) {
				throw new IllegalArgumentException("Not a valid project id: " + id);
			}
			projectIds.add(entry.getId());
		}
		return projectIds;
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
