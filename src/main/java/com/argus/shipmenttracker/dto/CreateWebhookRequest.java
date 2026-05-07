package com.argus.shipmenttracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWebhookRequest(
    @NotBlank(message = "url is required")
    @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
    @Size(max = 2048)
    String url,

    List<String> eventTypes,

    @Size(max = 255, message = "description must be 255 characters or fewer")
    String description
) {
    public List<String> eventTypesOrAll() {
        return (eventTypes == null || eventTypes.isEmpty()) ? List.of("*") : eventTypes;
    }
}
