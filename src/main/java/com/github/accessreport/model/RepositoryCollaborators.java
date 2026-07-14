package com.github.accessreport.model;

import com.github.accessreport.dto.CollaboratorDto;
import java.util.List;

/** Internal value joining one repository to the collaborators fetched for it. */
public record RepositoryCollaborators(String repository, List<CollaboratorDto> collaborators) {

    public RepositoryCollaborators {
        collaborators = List.copyOf(collaborators);
    }
}
