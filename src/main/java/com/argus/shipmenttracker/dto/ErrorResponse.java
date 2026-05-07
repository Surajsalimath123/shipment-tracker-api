package com.argus.shipmenttracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 Problem Details — used for every error response from the API.
 * <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    Instant timestamp,
    List<FieldError> errors,
    Map<String, Object> extensions
) {
    public static ErrorResponse of(int status, String title, String detail, String path) {
        return new ErrorResponse(
            "about:blank",
            title,
            status,
            detail,
            path,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse withFieldErrors(int status, String title, String detail,
                                                String path, List<FieldError> errors) {
        return new ErrorResponse(
            "about:blank",
            title,
            status,
            detail,
            path,
            Instant.now(),
            errors,
            null
        );
    }

    public record FieldError(String field, String message, Object rejectedValue) {}
}
