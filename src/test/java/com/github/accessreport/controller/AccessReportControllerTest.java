package com.github.accessreport.controller;

import static org.mockito.Mockito.when;

import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.dto.RepositoryAccess;
import com.github.accessreport.exception.GithubRateLimitException;
import com.github.accessreport.exception.GlobalExceptionHandler;
import com.github.accessreport.model.AccessPermission;
import com.github.accessreport.service.GithubService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(AccessReportController.class)
@Import(GlobalExceptionHandler.class)
class AccessReportControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GithubService githubService;

    @Test
    void returnsAccessReportJson() {
        when(githubService.getAccessReport()).thenReturn(Mono.just(List.of(
                new AccessReportResponse("amit", List.of(new RepositoryAccess("ATM", AccessPermission.WRITE))))));

        webTestClient.get().uri("/api/report").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].username").isEqualTo("amit")
                .jsonPath("$[0].repositories[0].repository").isEqualTo("ATM")
                .jsonPath("$[0].repositories[0].permission").isEqualTo("WRITE");
    }

    @Test
    void mapsRateLimitToStructuredJsonError() {
        when(githubService.getAccessReport()).thenReturn(Mono.error(
                new GithubRateLimitException("GitHub API rate limit has been exceeded.", Duration.ofSeconds(20))));

        webTestClient.get().uri("/api/report").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "20")
                .expectBody()
                .jsonPath("$.code").isEqualTo("GITHUB_RATE_LIMITED")
                .jsonPath("$.retryAfterSeconds").isEqualTo(20);
    }
}
