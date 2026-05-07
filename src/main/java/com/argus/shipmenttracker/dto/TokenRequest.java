package com.argus.shipmenttracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TokenRequest(
    @NotNull(message = "companyId is required")
    UUID companyId,

    @NotBlank(message = "apiKey is required")
    String apiKey
) {}
