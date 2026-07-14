package com.github.accessreport.exception;

/** A network or timeout failure prevented communication with GitHub. */
public final class GithubCommunicationException extends GithubApiException {
    public GithubCommunicationException(String message, Throwable cause) {
        super("GITHUB_COMMUNICATION_ERROR", message, null, cause);
    }
}
