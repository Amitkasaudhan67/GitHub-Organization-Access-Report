package com.github.accessreport.dto;

import java.util.List;

/** One user's complete repository access in the configured organization. */
public record AccessReportResponse(String username, List<RepositoryAccess> repositories) {

    public AccessReportResponse {
        repositories = List.copyOf(repositories);
    }
}
