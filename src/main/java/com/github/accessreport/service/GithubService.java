package com.github.accessreport.service;

import com.github.accessreport.client.GithubClient;
import com.github.accessreport.config.GithubProperties;
import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.dto.CollaboratorDto;
import com.github.accessreport.dto.RepositoryAccess;
import com.github.accessreport.dto.RepositoryDto;
import com.github.accessreport.exception.GithubNotFoundException;
import com.github.accessreport.model.AccessPermission;
import com.github.accessreport.model.RepositoryCollaborators;
import com.github.accessreport.util.PermissionResolver;
import com.github.benmanes.caffeine.cache.AsyncCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Builds and caches the normalized organization access report. */
@Service
public class GithubService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubService.class);
    private static final Comparator<String> CASE_INSENSITIVE_ORDER = String.CASE_INSENSITIVE_ORDER;

    private final GithubClient githubClient;
    private final GithubProperties properties;
    private final AsyncCache<String, List<AccessReportResponse>> accessReportCache;

    public GithubService(
            GithubClient githubClient,
            GithubProperties properties,
            AsyncCache<String, List<AccessReportResponse>> accessReportCache) {
        this.githubClient = githubClient;
        this.properties = properties;
        this.accessReportCache = accessReportCache;
    }

    /** Returns the cached report or asynchronously produces a new report when the cache has expired. */
    public Mono<List<AccessReportResponse>> getAccessReport() {
        return Mono.defer(() -> Mono.fromFuture(accessReportCache.get(
                properties.organization(), (organization, executor) -> generateReport().toFuture())));
    }

    private Mono<List<AccessReportResponse>> generateReport() {
        long startedAt = System.nanoTime();
        return githubClient.fetchRepositories()
                .collectList()
                .flatMap(repositories -> processRepositories(repositories)
                        .collectList()
                        .map(this::aggregate)
                        .doOnSuccess(report -> logCompletion(report, repositories.size(), startedAt)))
                .doOnError(error -> LOGGER.error("Failed to generate GitHub access report: {}", error.getMessage()));
    }

    private Flux<RepositoryCollaborators> processRepositories(List<RepositoryDto> repositories) {
        LOGGER.info("Processing {} repositories for GitHub organization {}", repositories.size(), properties.organization());
        return Flux.fromIterable(repositories)
                .flatMap(repository -> githubClient.fetchCollaborators(repository.name())
                                .collectList()
                                .map(collaborators -> new RepositoryCollaborators(repository.name(), collaborators))
                                .onErrorResume(GithubNotFoundException.class, error -> {
                                    LOGGER.info("Skipping repository {} because it no longer exists", repository.name());
                                    return Mono.empty();
                                }),
                        properties.maxConcurrency());
    }

    private List<AccessReportResponse> aggregate(List<RepositoryCollaborators> repositoryCollaborators) {
        Map<String, Map<String, AccessPermission>> userAccess = new HashMap<>();
        for (RepositoryCollaborators repository : repositoryCollaborators) {
            for (CollaboratorDto collaborator : repository.collaborators()) {
                if (collaborator.login() == null || collaborator.login().isBlank()) {
                    continue;
                }
                AccessPermission permission = PermissionResolver.resolve(collaborator.permissions());
                userAccess.computeIfAbsent(collaborator.login(), ignored -> new HashMap<>())
                        .merge(repository.repository(), permission, AccessPermission::strongest);
            }
        }

        return userAccess.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CASE_INSENSITIVE_ORDER))
                .map(entry -> new AccessReportResponse(entry.getKey(), repositoryAccess(entry.getValue())))
                .toList();
    }

    private List<RepositoryAccess> repositoryAccess(Map<String, AccessPermission> repositories) {
        List<RepositoryAccess> access = new ArrayList<>();
        repositories.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CASE_INSENSITIVE_ORDER))
                .forEach(entry -> access.add(new RepositoryAccess(entry.getKey(), entry.getValue())));
        return List.copyOf(access);
    }

    private void logCompletion(List<AccessReportResponse> report, int repositoryCount, long startedAt) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        LOGGER.info("Processed {} repositories and generated a GitHub access report for {} users in {} ms",
                repositoryCount, report.size(), elapsed.toMillis());
    }
}
