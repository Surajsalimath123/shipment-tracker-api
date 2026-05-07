package com.argus.shipmenttracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookTest {

    @Test
    @DisplayName("wildcard accepts every event type")
    void wildcard() {
        Webhook hook = new Webhook();
        hook.setEventTypes(new String[] { "*" });

        for (EventType type : EventType.values()) {
            assertThat(hook.acceptsEventType(type)).isTrue();
        }
    }

    @Test
    @DisplayName("explicit list accepts only listed types")
    void explicitList() {
        Webhook hook = new Webhook();
        hook.setEventTypes(new String[] { "DELIVERED", "EXCEPTION" });

        assertThat(hook.acceptsEventType(EventType.DELIVERED)).isTrue();
        assertThat(hook.acceptsEventType(EventType.EXCEPTION)).isTrue();
        assertThat(hook.acceptsEventType(EventType.PICKUP)).isFalse();
        assertThat(hook.acceptsEventType(EventType.IN_TRANSIT)).isFalse();
    }

    @Test
    @DisplayName("empty list accepts nothing")
    void emptyList() {
        Webhook hook = new Webhook();
        hook.setEventTypes(new String[] {});

        assertThat(hook.acceptsEventType(EventType.DELIVERED)).isFalse();
    }
}
