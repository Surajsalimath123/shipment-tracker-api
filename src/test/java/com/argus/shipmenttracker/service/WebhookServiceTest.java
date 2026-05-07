package com.argus.shipmenttracker.service;

import com.argus.shipmenttracker.domain.Webhook;
import com.argus.shipmenttracker.dto.CreateWebhookRequest;
import com.argus.shipmenttracker.dto.WebhookResponse;
import com.argus.shipmenttracker.exception.WebhookNotFoundException;
import com.argus.shipmenttracker.repository.WebhookRepository;
import com.argus.shipmenttracker.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock WebhookRepository webhookRepository;
    @InjectMocks WebhookService webhookService;

    UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        TenantContext.setCompanyId(companyId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("register creates a webhook with a fresh secret")
    void register() {
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookResponse response = webhookService.register(
            new CreateWebhookRequest("https://example.com/hook", List.of("DELIVERED"), "test"));

        assertThat(response.url()).isEqualTo("https://example.com/hook");
        assertThat(response.eventTypes()).containsExactly("DELIVERED");
        assertThat(response.secret()).startsWith("whsec_").hasSizeGreaterThan(40);
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("register defaults to wildcard when eventTypes is empty")
    void registerDefaultsToWildcard() {
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookResponse response = webhookService.register(
            new CreateWebhookRequest("https://example.com/hook", List.of(), null));

        assertThat(response.eventTypes()).containsExactly("*");
    }

    @Test
    @DisplayName("register rejects unknown event types")
    void rejectsBadEventType() {
        assertThatThrownBy(() ->
            webhookService.register(new CreateWebhookRequest(
                "https://example.com/hook", List.of("BOGUS"), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown event type: BOGUS");
    }

    @Test
    @DisplayName("unregister deletes the webhook for the right tenant")
    void unregister() {
        UUID webhookId = UUID.randomUUID();
        Webhook webhook = Webhook.builder()
            .webhookId(webhookId).companyId(companyId).build();
        when(webhookRepository.findByWebhookIdAndCompanyId(webhookId, companyId))
            .thenReturn(Optional.of(webhook));

        webhookService.unregister(webhookId);

        verify(webhookRepository).delete(webhook);
    }

    @Test
    @DisplayName("unregister throws when webhook does not belong to this tenant")
    void unregisterMissing() {
        UUID webhookId = UUID.randomUUID();
        when(webhookRepository.findByWebhookIdAndCompanyId(webhookId, companyId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.unregister(webhookId))
            .isInstanceOf(WebhookNotFoundException.class);
    }
}
