package com.github.accessreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.accessreport.client.GithubClient;
import com.github.accessreport.config.GithubProperties;
import com.github.accessreport.dto.CollaboratorDto;
import com.github.accessreport.dto.PermissionDto;
import com.github.accessreport.dto.RepositoryDto;
import com.github.accessreport.exception.GithubForbiddenException;
import com.github.accessreport.exception.GithubNotFoundException;
import com.github.accessreport.model.AccessPermission;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    @Mock
    private GithubClient githubClient;

    private GithubService service;

    @BeforeEach
    void setUp() {
        AsyncCache<String, List<com.github.accessreport.dto.AccessReportResponse>> cache = Caffeine.newBuilder().buildAsync();
        service = new GithubService(githubClient, properties(), cache);
    }

    @Test
    void aggregatesPermissionsAndSortsUsersAndRepositories() {
        when(githubClient.fetchRepositories()).thenReturn(Flux.just(new RepositoryDto("Zoo"), new RepositoryDto("Alpha")));
        when(githubClient.fetchCollaborators("Zoo")).thenReturn(Flux.just(
                collaborator("zoe", false, true, false, false), collaborator("amy", false, false, false, true)));
        when(githubClient.fetchCollaborators("Alpha")).thenReturn(Flux.just(
                collaborator("amy", false, false, true, false), collaborator("zoe", true, false, false, false)));

        StepVerifier.create(service.getAccessReport())
                .assertNext(report -> {
                    assertThat(report).hasSize(2);
                    assertThat(report.get(0).username()).isEqualTo("amy");
                    assertThat(report.get(0).repositories()).extracting(access -> access.repository())
                            .containsExactly("Alpha", "Zoo");
                    assertThat(report.get(0).repositories().get(0).permission()).isEqualTo(AccessPermission.ADMIN);
                    assertThat(report.get(0).repositories().get(1).permission()).isEqualTo(AccessPermission.WRITE);
                    assertThat(report.get(1).username()).isEqualTo("zoe");
                    assertThat(report.get(1).repositories().get(0).permission()).isEqualTo(AccessPermission.READ);
                })
                .verifyComplete();
    }

    @Test
    void reusesSuccessfulCachedReport() {
        when(githubClient.fetchRepositories()).thenReturn(Flux.just(new RepositoryDto("Atlas")));
        when(githubClient.fetchCollaborators("Atlas")).thenReturn(Flux.just(collaborator("amit", true, false, false, false)));

        StepVerifier.create(service.getAccessReport()).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.getAccessReport()).expectNextCount(1).verifyComplete();

        verify(githubClient, times(1)).fetchRepositories();
        verify(githubClient, times(1)).fetchCollaborators("Atlas");
    }

    @Test
    void skipsRepositoryThatDisappearsAfterEnumeration() {
        when(githubClient.fetchRepositories()).thenReturn(Flux.just(new RepositoryDto("Gone"), new RepositoryDto("Live")));
        when(githubClient.fetchCollaborators("Gone")).thenReturn(Flux.error(new GithubNotFoundException("missing")));
        when(githubClient.fetchCollaborators("Live")).thenReturn(Flux.just(collaborator("amit", true, false, false, false)));

        StepVerifier.create(service.getAccessReport())
                .assertNext(report -> assertThat(report.get(0).repositories()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void propagatesNonNotFoundRepositoryFailures() {
        when(githubClient.fetchRepositories()).thenReturn(Flux.just(new RepositoryDto("Private")));
        when(githubClient.fetchCollaborators("Private")).thenReturn(Flux.error(new GithubForbiddenException("denied")));

        StepVerifier.create(service.getAccessReport())
                .expectError(GithubForbiddenException.class)
                .verify();
    }

    private static CollaboratorDto collaborator(String login, boolean pull, boolean push, boolean admin, boolean maintain) {
        return new CollaboratorDto(login, new PermissionDto(pull, push, admin, maintain, false));
    }

    private static GithubProperties properties() {
        return new GithubProperties("https://api.github.test", "example-org", "token", Duration.ofSeconds(1),
                Duration.ofSeconds(5), 2, new GithubProperties.Retry(2, Duration.ZERO), Duration.ofMinutes(5));
    }
}
