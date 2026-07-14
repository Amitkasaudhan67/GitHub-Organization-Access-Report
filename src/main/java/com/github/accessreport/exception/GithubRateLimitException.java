package com.github.accessreport.exception;

import java.time.Duration;

/** GitHub has rate limited the caller. */
public final class GithubRateLimitException extends GithubApiException {
    public GithubRateLimitException(String message, Duration retryAfter) {
        super("GITHUB_RATE_LIMITED", message, retryAfter, null);
    }
}
