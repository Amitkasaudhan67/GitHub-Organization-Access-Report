package com.github.accessreport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Permission flags attached to a collaborator in GitHub's API response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionDto(Boolean pull, Boolean push, Boolean admin, Boolean maintain, Boolean triage) {
}
