package com.github.accessreport.exception;

import com.github.accessreport.dto.ApiError;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/** Converts internal and GitHub failures to the public JSON error contract. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GithubApiException.class)
    public ResponseEntity<ApiError> handleGithubFailure(GithubApiException exception, ServerWebExchange exchange) {
        HttpStatus status = statusFor(exception);
        Long retryAfterSeconds = retryAfterSeconds(exception.retryAfter());
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.code(),
                exception.getMessage(),
                exchange.getRequest().getPath().value(),
                retryAfterSeconds);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (retryAfterSeconds != null) {
            response.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
        }
        return response.body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedFailure(Exception exception, ServerWebExchange exchange) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_ERROR",
                "An unexpected error occurred while generating the access report.",
                exchange.getRequest().getPath().value(),
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private HttpStatus statusFor(GithubApiException exception) {
        if (exception instanceof GithubUnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (exception instanceof GithubForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception instanceof GithubNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof GithubRateLimitException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (exception instanceof GithubCommunicationException) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (exception instanceof GithubServerException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private Long retryAfterSeconds(Duration retryAfter) {
        return retryAfter == null ? null : Math.max(1, retryAfter.toSeconds());
    }
}
