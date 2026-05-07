package com.argus.shipmenttracker.security;

import java.util.UUID;

/**
 * Thread-local holder for the authenticated company id. Set by
 * {@link JwtAuthenticationFilter} at the start of each request and cleared
 * in a {@code finally} block. Service code calls {@link #requireCompanyId()}
 * to enforce that every operation runs inside a tenant scope.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCompanyId(UUID companyId) {
        CURRENT.set(companyId);
    }

    public static UUID getCompanyId() {
        return CURRENT.get();
    }

    public static UUID requireCompanyId() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                "No tenant context on this thread. Did the request go through JwtAuthenticationFilter?");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
