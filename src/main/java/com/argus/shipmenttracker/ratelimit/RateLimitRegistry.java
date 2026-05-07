package com.argus.shipmenttracker.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lazily-built cache of one Bucket per tenant. The bucket capacity
 * (burst) and refill rate (steady-state) come from {@link RateLimitProperties}.
 *
 * For multi-instance deployments, swap this for a Redis-backed bucket
 * proxy (Bucket4j ships one) so all replicas share the same view.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class RateLimitRegistry {

    private final RateLimitProperties properties;
    private final ConcurrentMap<UUID, Bucket> tenantBuckets = new ConcurrentHashMap<>();

    public Bucket bucketFor(UUID companyId) {
        return tenantBuckets.computeIfAbsent(companyId, id -> buildBucket(properties.getAuthenticated()));
    }

    private Bucket buildBucket(RateLimitProperties.Tier tier) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(tier.getBurstCapacity())
            .refillGreedy(tier.getRequestsPerMinute(), Duration.ofMinutes(1))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
