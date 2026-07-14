package com.github.accessreport.exception;

/** GitHub returned a transient server-side failure. */
public final class GithubServerException extends GithubApiException {
    public GithubServerException(String message) {
        super("GITHUB_SERVER_ERROR", message);
    }
}
