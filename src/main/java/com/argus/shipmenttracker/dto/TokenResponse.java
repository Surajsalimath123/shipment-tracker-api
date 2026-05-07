package com.argus.shipmenttracker.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    UUID companyId
) {
    public static TokenResponse bearer(String token, long expiresInSeconds, Instant expiresAt, UUID companyId) {
        return new TokenResponse(token, "Bearer", expiresInSeconds, expiresAt, companyId);
    }
}
