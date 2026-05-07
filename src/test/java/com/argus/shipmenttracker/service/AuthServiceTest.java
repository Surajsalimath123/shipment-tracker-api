package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.Company;
import com.argus.shipmenttracker.dto.TokenRequest;
import com.argus.shipmenttracker.dto.TokenResponse;
import com.argus.shipmenttracker.exception.InvalidCredentialsException;
import com.argus.shipmenttracker.repository.CompanyRepository;
import com.argus.shipmenttracker.security.JwtProperties;
import com.argus.shipmenttracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock JwtProperties jwtProperties;

    @InjectMocks AuthService authService;

    UUID companyId;
    Company company;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        company = Company.builder()
            .companyId(companyId)
            .name("Test Co")
            .apiKeyHash("$2a$10$fakeHashValue")
            .active(true)
            .build();
    }

    @Test
    @DisplayName("valid credentials return a Bearer token")
    void validCredentials() {
        when(companyRepository.findByCompanyIdAndActiveTrue(companyId))
            .thenReturn(Optional.of(company));
        when(passwordEncoder.matches("right-key", company.getApiKeyHash())).thenReturn(true);
        when(jwtService.issueToken(companyId)).thenReturn("fake.jwt.token");
        when(jwtProperties.getExpirationMs()).thenReturn(3_600_000L);

        TokenResponse response = authService.authenticate(new TokenRequest(companyId, "right-key"));

        assertThat(response.accessToken()).isEqualTo("fake.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.companyId()).isEqualTo(companyId);
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("unknown company throws InvalidCredentialsException")
    void unknownCompany() {
        when(companyRepository.findByCompanyIdAndActiveTrue(companyId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate(new TokenRequest(companyId, "any")))
            .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issueToken(any());
    }

    @Test
    @DisplayName("wrong api key throws InvalidCredentialsException")
    void wrongApiKey() {
        when(companyRepository.findByCompanyIdAndActiveTrue(companyId))
            .thenReturn(Optional.of(company));
        when(passwordEncoder.matches("wrong-key", company.getApiKeyHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticate(new TokenRequest(companyId, "wrong-key")))
            .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).issueToken(any());
    }
}
