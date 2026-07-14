package com.github.accessreport.client;

import com.github.accessreport.config.GithubProperties;
import com.github.accessreport.dto.CollaboratorDto;
import com.github.accessreport.dto.RepositoryDto;
import com.github.accessreport.exception.GithubApiException;
import com.github.accessreport.exception.GithubCommunicationException;
import com.github.accessreport.exception.GithubForbiddenException;
import com.github.accessreport.exception.GithubNotFoundException;
import com.github.accessreport.exception.GithubRateLimitException;
import com.github.accessreport.exception.GithubServerException;
import com.github.accessreport.exception.GithubUnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/** Contains only paginated GitHub REST API communication. */
@Component
public class GithubClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubClient.class);
    private static final int PAGE_SIZE = 100;

    private final WebClient githubWebClient;
    private final GithubProperties properties;

    public GithubClient(WebClient githubWebClient, GithubProperties properties) {
        this.githubWebClient = githubWebClient;
        this.properties = properties;
    }

    /** Retrieves every repository visible to the configured organization token. */
    public Flux<RepositoryDto> fetchRepositories() {
        return fetchAllPages("/orgs/" + properties.organization() + "/repos", RepositoryDto.class, true);
    }

    /** Retrieves every collaborator for a repository, including their permission flags. */
    public Flux<CollaboratorDto> fetchCollaborators(String repository) {
        return fetchAllPages(
                "/repos/" + properties.organization() + "/" + repository + "/collaborators",
                CollaboratorDto.class,
                false);
    }

    private <T> Flux<T> fetchAllPages(String path, Class<T> responseType, boolean includeAllRepositoryTypes) {
        return fetchPage(path, responseType, includeAllRepositoryTypes, 1)
                .expand(page -> page.items().isEmpty()
                        ? Mono.empty()
                        : fetchPage(path, responseType, includeAllRepositoryTypes, page.number() + 1))
                .takeWhile(page -> !page.items().isEmpty())
                .flatMapIterable(GithubPage::items);
    }

    private <T> Mono<GithubPage<T>> fetchPage(
            String path, Class<T> responseType, boolean includeAllRepositoryTypes, int page) {
        return githubWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path)
                            .queryParam("per_page", PAGE_SIZE)
                            .queryParam("page", page);
                    if (includeAllRepositoryTypes) {
                        builder.queryParam("type", "all");
                    }
                    return builder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toException(response).flatMap(Mono::error))
                .bodyToFlux(responseType)
                .collectList()
                .map(items -> new GithubPage<>(page, items))
                .onErrorMap(error -> !(error instanceof GithubApiException),
                        error -> new GithubCommunicationException("Unable to communicate with GitHub.", error))
                .retryWhen(Retry.from(signals -> signals.concatMap(this::nextRetry)))
                .doOnError(error -> LOGGER.warn("GitHub API request failed for {} page {}: {}", path, page,
                        error.getMessage()));
    }

    private Mono<GithubApiException> toException(ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(ignored -> mapException(response.statusCode(), headers));
    }

    private GithubApiException mapException(HttpStatusCode status, HttpHeaders headers) {
        int value = status.value();
        if (value == 401) {
            return new GithubUnauthorizedException("GitHub rejected the configured token.");
        }
        if (value == 403 && !isRateLimited(headers)) {
            return new GithubForbiddenException("The configured token is not allowed to access this GitHub resource.");
        }
        if (value == 404) {
            return new GithubNotFoundException("The requested GitHub organization or repository was not found.");
        }
        if (value == 429 || isRateLimited(headers)) {
            return new GithubRateLimitException("GitHub API rate limit has been exceeded.", retryAfter(headers));
        }
        if (status.is5xxServerError()) {
            return new GithubServerException("GitHub is temporarily unavailable.");
        }
        return new GithubApiException("GITHUB_API_ERROR", "GitHub API request failed with HTTP status " + value + ".");
    }

    private Publisher<Long> nextRetry(Retry.RetrySignal signal) {
        Throwable failure = signal.failure();
        long retryNumber = signal.totalRetries() + 1;
        if (!isRetryable(failure) || retryNumber >= properties.retry().maxAttempts()) {
            return Mono.error(failure);
        }

        Duration delay = exponentialBackoff(retryNumber);
        if (failure instanceof GithubRateLimitException rateLimit && rateLimit.retryAfter() != null) {
            delay = delay.compareTo(rateLimit.retryAfter()) < 0 ? rateLimit.retryAfter() : delay;
        }
        LOGGER.warn("Retrying GitHub request after {} ms (attempt {}/{})", delay.toMillis(), retryNumber + 1,
                properties.retry().maxAttempts());
        return Mono.delay(delay);
    }

    private boolean isRetryable(Throwable failure) {
        return failure instanceof GithubRateLimitException
                || failure instanceof GithubServerException
                || failure instanceof GithubCommunicationException;
    }

    private Duration exponentialBackoff(long retryNumber) {
        long multiplier = 1L << Math.min(retryNumber - 1, 20);
        return properties.retry().baseDelay().multipliedBy(multiplier);
    }

    private boolean isRateLimited(HttpHeaders headers) {
        return "0".equals(headers.getFirst("X-RateLimit-Remaining"));
    }

    private Duration retryAfter(HttpHeaders headers) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(retryAfter));
            } catch (NumberFormatException ignored) {
                // GitHub can omit this header or use a non-numeric value; use its reset header next.
            }
        }
        String resetEpoch = headers.getFirst("X-RateLimit-Reset");
        if (resetEpoch != null) {
            try {
                return Duration.ofSeconds(Math.max(0, Long.parseLong(resetEpoch) - Instant.now().getEpochSecond()));
            } catch (NumberFormatException ignored) {
                // The normal exponential backoff remains in effect.
            }
        }
        return null;
    }

    private record GithubPage<T>(int number, List<T> items) {
    }
}
