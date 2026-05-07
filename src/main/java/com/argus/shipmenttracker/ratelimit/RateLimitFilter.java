package com.argus.shipmenttracker.ratelimit;

import com.argus.shipmenttracker.dto.ErrorResponse;
import com.argus.shipmenttracker.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Per-tenant rate limit filter. Runs after JwtAuthenticationFilter so the
 * tenant context is populated. Only enforced for authenticated requests
 * — auth and docs endpoints are unprotected.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitRegistry registry;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) return true;
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/actuator/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        UUID companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = registry.bucketFor(companyId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit",
            String.valueOf(properties.getAuthenticated().getBurstCapacity()));
        response.setHeader("X-RateLimit-Remaining",
            String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.info("Rate limit exceeded for company={} retryAfter={}s", companyId, retryAfterSeconds);

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfterSeconds));

        ErrorResponse body = ErrorResponse.of(429, "Rate limit exceeded",
            "Tenant has exhausted its rate-limit window. Retry after " + retryAfterSeconds + "s.",
            request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
