package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.Company;
import com.argus.shipmenttracker.dto.TokenRequest;
import com.argus.shipmenttracker.dto.TokenResponse;
import com.argus.shipmenttracker.exception.InvalidCredentialsException;
import com.argus.shipmenttracker.repository.CompanyRepository;
import com.argus.shipmenttracker.security.JwtProperties;
import com.argus.shipmenttracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CompanyRepository companyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public TokenResponse authenticate(TokenRequest request) {
        Company company = companyRepository
            .findByCompanyIdAndActiveTrue(request.companyId())
            .orElseThrow(() -> {
                log.info("Token request for unknown or inactive company: {}", request.companyId());
                return new InvalidCredentialsException();
            });

        if (!passwordEncoder.matches(request.apiKey(), company.getApiKeyHash())) {
            log.info("Invalid API key for company: {}", company.getCompanyId());
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issueToken(company.getCompanyId());
        long expiresInSeconds = jwtProperties.getExpirationMs() / 1000;
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);

        log.info("Issued JWT for company: {}", company.getCompanyId());
        return TokenResponse.bearer(token, expiresInSeconds, expiresAt, company.getCompanyId());
    }
}
