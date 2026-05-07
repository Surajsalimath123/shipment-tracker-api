package com.argus.shipmenttracker.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signs outbound webhook bodies with HMAC-SHA256 using the per-webhook
 * secret. Subscribers verify the {@code X-Shipment-Tracker-Signature}
 * header to confirm the payload originated from us and was not tampered.
 */
@Slf4j
@Component
public class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Shipment-Tracker-Signature";
    public static final String TIMESTAMP_HEADER = "X-Shipment-Tracker-Timestamp";

    private static final String ALGORITHM = "HmacSHA256";

    public String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign webhook payload", ex);
        }
    }
}
