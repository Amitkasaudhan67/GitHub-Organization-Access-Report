package com.github.accessreport.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.accessreport.config.GithubProperties;
import com.github.accessreport.dto.RepositoryDto;
import com.github.accessreport.exception.GithubRateLimitException;
import com.github.accessreport.exception.GithubUnauthorizedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GithubClientTest {

    @Test
    void fetchesRepositoryPagesUntilGitHubReturnsAnEmptyPage() {
        List<String> requestedUris = new ArrayList<>();
        List<String> authorizationHeaders = new ArrayList<>();
        GithubClient client = client(request -> {
            requestedUris.add(request.url().toString());
            authorizationHeaders.add(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            String body = requestedUris.size() == 1 ? "[{\"name\":\"ATM\"}]" : "[]";
            return Mono.just(jsonResponse(HttpStatus.OK, body));
        });

        StepVerifier.create(client.fetchRepositories())
                .expectNext(new RepositoryDto("ATM"))
                .verifyComplete();

        assertThat(requestedUris).containsExactly(
                "https://api.github.test/orgs/example-org/repos?per_page=100&page=1&type=all",
                "https://api.github.test/orgs/example-org/repos?per_page=100&page=2&type=all");
        assertThat(authorizationHeaders).containsOnly("Bearer token");
    }

    @Test
    void mapsUnauthorizedResponseToTypedException() {
        GithubClient client = client(request -> Mono.just(jsonResponse(HttpStatus.UNAUTHORIZED, "{}")));

        StepVerifier.create(client.fetchRepositories())
                .expectError(GithubUnauthorizedException.class)
                .verify();
    }

    @Test
    void mapsRateLimitResponseAndExposesRetryDelay() {
        GithubClient client = client(request -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{}").build()), 1);

        StepVerifier.create(client.fetchRepositories())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(GithubRateLimitException.class);
                    assertThat(((GithubRateLimitException) error).retryAfter()).isEqualTo(Duration.ofSeconds(30));
                })
                .verify();
    }

    @Test
    void retriesTransientServerFailure() {
        AtomicInteger attempts = new AtomicInteger();
        GithubClient client = client(request -> {
            if (attempts.incrementAndGet() == 1) {
                return Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));
            }
            return Mono.just(jsonResponse(HttpStatus.OK, "[]"));
        });

        StepVerifier.create(client.fetchRepositories()).verifyComplete();
        assertThat(attempts.get()).isEqualTo(2);
    }

    private static GithubClient client(ExchangeFunction exchangeFunction) {
        return client(exchangeFunction, 2);
    }

    private static GithubClient client(ExchangeFunction exchangeFunction, int maxAttempts) {
        WebClient webClient = WebClient.builder().baseUrl("https://api.github.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer token")
                .exchangeFunction(exchangeFunction).build();
        return new GithubClient(webClient, properties(maxAttempts));
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    private static GithubProperties properties() {
        return properties(2);
    }

    private static GithubProperties properties(int maxAttempts) {
        return new GithubProperties("https://api.github.test", "example-org", "token", Duration.ofSeconds(1),
                Duration.ofSeconds(5), 2, new GithubProperties.Retry(maxAttempts, Duration.ZERO), Duration.ofMinutes(5));
    }
}
