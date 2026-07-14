package com.github.accessreport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Stable JSON error contract returned by the global exception handler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Long retryAfterSeconds) {
}
