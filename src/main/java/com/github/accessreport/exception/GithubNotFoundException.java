package com.github.accessreport.exception;

/** A GitHub resource requested by the application does not exist or is hidden. */
public final class GithubNotFoundException extends GithubApiException {
    public GithubNotFoundException(String message) {
        super("GITHUB_NOT_FOUND", message);
    }
}
