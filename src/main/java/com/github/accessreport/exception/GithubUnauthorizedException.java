package com.github.accessreport.exception;

/** GitHub rejected the configured personal access token. */
public final class GithubUnauthorizedException extends GithubApiException {
    public GithubUnauthorizedException(String message) {
        super("GITHUB_UNAUTHORIZED", message);
    }
}
