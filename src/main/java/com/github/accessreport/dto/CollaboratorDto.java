package com.github.accessreport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Minimal collaborator representation returned by GitHub. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollaboratorDto(
        @JsonProperty("login") String login,
        @JsonProperty("permissions") PermissionDto permissions) {
}
