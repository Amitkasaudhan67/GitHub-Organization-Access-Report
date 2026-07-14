package com.github.accessreport.exception;

/** The configured token is authenticated but lacks required GitHub access. */
public final class GithubForbiddenException extends GithubApiException {
    public GithubForbiddenException(String message) {
        super("GITHUB_FORBIDDEN", message);
    }
}
