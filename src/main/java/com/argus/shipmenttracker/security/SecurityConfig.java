package com.argus.shipmenttracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.argus.shipmenttracker.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-ui",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(unauthorizedEntryPoint())
                .accessDeniedHandler(forbiddenHandler()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) ->
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication required",
                "A valid Bearer token is required to access this resource",
                request.getRequestURI());
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, ex) ->
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                "Access denied",
                "The token does not grant access to this resource",
                request.getRequestURI());
    }

    private void writeError(HttpServletResponse response, int status,
                            String title, String detail, String path) throws java.io.IOException {
        ErrorResponse body = ErrorResponse.of(status, title, detail, path);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
