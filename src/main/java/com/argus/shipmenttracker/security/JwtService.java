package com.argus.shipmenttracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and parses JSON Web Tokens. Uses HS256 (HMAC-SHA-256) with the
 * shared secret from {@link JwtProperties}. The token's subject and
 * {@code company_id} claim both carry the tenant identifier — services
 * read it via {@link TenantContext} after the filter has run.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_COMPANY_ID = "company_id";

    private final JwtProperties properties;

    public String issueToken(UUID companyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.getExpirationMs());

        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(companyId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(CLAIM_COMPANY_ID, companyId.toString())
            .signWith(signingKey())
            .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .requireIssuer(properties.getIssuer())
            .build()
            .parseSignedClaims(token);
    }

    public UUID extractCompanyId(Jws<Claims> jws) {
        String value = jws.getPayload().get(CLAIM_COMPANY_ID, String.class);
        if (value == null) {
            throw new IllegalArgumentException("Token missing company_id claim");
        }
        return UUID.fromString(value);
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
