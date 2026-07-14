package com.github.accessreport.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** External configuration used when communicating with the GitHub REST API. */
@Validated
@ConfigurationProperties(prefix = "github")
public record GithubProperties(
        @NotBlank String baseUrl,
        @NotBlank String organization,
        @NotBlank String token,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Min(1) int maxConcurrency,
        @NotNull Retry retry,
        @NotNull Duration reportCacheTtl) {
    public record Retry(@Min(1) int maxAttempts, @NotNull Duration baseDelay) { }
}
