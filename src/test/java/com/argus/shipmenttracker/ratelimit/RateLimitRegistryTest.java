package com.argus.shipmenttracker.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRegistryTest {

    @Test
    @DisplayName("each company gets its own bucket; the same company always gets the same bucket")
    void perCompanyBuckets() {
        RateLimitProperties props = new RateLimitProperties();
        props.setAuthenticated(new RateLimitProperties.Tier(1000, 10));
        RateLimitRegistry registry = new RateLimitRegistry(props);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        Bucket bucketA1 = registry.bucketFor(a);
        Bucket bucketA2 = registry.bucketFor(a);
        Bucket bucketB  = registry.bucketFor(b);

        assertThat(bucketA1).isSameAs(bucketA2);
        assertThat(bucketA1).isNotSameAs(bucketB);
    }

    @Test
    @DisplayName("bucket starts full and rejects requests beyond the burst capacity")
    void exhaustsAtBurstCapacity() {
        RateLimitProperties props = new RateLimitProperties();
        props.setAuthenticated(new RateLimitProperties.Tier(1000, 3));
        RateLimitRegistry registry = new RateLimitRegistry(props);

        Bucket bucket = registry.bucketFor(UUID.randomUUID());

        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();
    }
}
