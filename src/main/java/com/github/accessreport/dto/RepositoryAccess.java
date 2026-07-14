package com.github.accessreport.dto;

import com.github.accessreport.model.AccessPermission;

/** A repository and the normalized access level held by one user. */
public record RepositoryAccess(String repository, AccessPermission permission) {
}
