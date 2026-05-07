package com.argus.shipmenttracker.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    @DisplayName("signature is deterministic for the same body and secret")
    void deterministic() {
        String body = "{\"eventId\":\"abc\"}";
        String secret = "shared-secret";

        String first  = signer.sign(body, secret);
        String second = signer.sign(body, secret);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("sha256=").hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("signature differs for different secrets")
    void secretSensitive() {
        String body = "{\"eventId\":\"abc\"}";

        String a = signer.sign(body, "secret-a");
        String b = signer.sign(body, "secret-b");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("signature differs for different bodies")
    void bodySensitive() {
        String secret = "shared";

        String a = signer.sign("{\"id\":1}", secret);
        String b = signer.sign("{\"id\":2}", secret);

        assertThat(a).isNotEqualTo(b);
    }
}
