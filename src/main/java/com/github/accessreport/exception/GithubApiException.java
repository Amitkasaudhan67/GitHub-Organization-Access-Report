package com.github.accessreport.exception;

import java.time.Duration;

/** Base exception for a GitHub API failure that has a meaningful client response. */
public class GithubApiException extends RuntimeException {

    private final String code;
    private final Duration retryAfter;

    public GithubApiException(String code, String message) {
        this(code, message, null, null);
    }

    public GithubApiException(String code, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
