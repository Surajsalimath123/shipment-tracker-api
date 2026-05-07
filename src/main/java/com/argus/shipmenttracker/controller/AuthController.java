package com.argus.shipmenttracker.controller;

import com.argus.shipmenttracker.dto.TokenRequest;
import com.argus.shipmenttracker.dto.TokenResponse;
import com.argus.shipmenttracker.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Issue JWT access tokens for API access")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token")
    @Operation(summary = "Exchange company id + API key for a JWT access token",
        description = "Returns a Bearer token valid for 1 hour. Include in the Authorization header on every subsequent request: 'Authorization: Bearer <token>'.")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }
}
