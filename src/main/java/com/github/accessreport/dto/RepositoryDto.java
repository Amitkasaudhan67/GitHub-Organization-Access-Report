package com.github.accessreport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Minimal repository representation returned by GitHub. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositoryDto(@JsonProperty("name") String name) {
}
