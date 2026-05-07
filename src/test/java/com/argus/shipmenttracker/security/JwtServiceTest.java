package com.argus.shipmenttracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtProperties props;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("dGVzdC1zZWNyZXQtZm9yLWp3dC1zaWduaW5nLW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHM=");
        props.setExpirationMs(60_000);
        props.setIssuer("test-issuer");
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("issued token contains the company_id claim and verifies on parse")
    void issueAndParse() {
        UUID companyId = UUID.randomUUID();

        String token = jwtService.issueToken(companyId);
        Jws<Claims> jws = jwtService.parse(token);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractCompanyId(jws)).isEqualTo(companyId);
        assertThat(jws.getPayload().getIssuer()).isEqualTo("test-issuer");
        assertThat(jws.getPayload().getSubject()).isEqualTo(companyId.toString());
    }

    @Test
    @DisplayName("token signed with a different secret fails to parse")
    void rejectsForeignSignature() {
        UUID companyId = UUID.randomUUID();
        SecretKey foreign = Keys.hmacShaKeyFor(
            Decoders.BASE64.decode("YW5vdGhlci1zZWNyZXQtZm9yLWp3dC1zaWduaW5nLW11c3QtYmUtMjU2LWJpdHM="));
        String foreignToken = Jwts.builder()
            .issuer("test-issuer")
            .subject(companyId.toString())
            .claim(JwtService.CLAIM_COMPANY_ID, companyId.toString())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(foreign)
            .compact();

        assertThatThrownBy(() -> jwtService.parse(foreignToken))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("expired token fails to parse")
    void rejectsExpiredToken() {
        props.setExpirationMs(-1_000);
        String token = jwtService.issueToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.parse(token))
            .isInstanceOf(JwtException.class);
    }
}
