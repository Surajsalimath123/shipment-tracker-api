package com.argus.shipmenttracker.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("setCompanyId is read back via requireCompanyId")
    void setAndGet() {
        UUID id = UUID.randomUUID();
        TenantContext.setCompanyId(id);

        assertThat(TenantContext.requireCompanyId()).isEqualTo(id);
        assertThat(TenantContext.getCompanyId()).isEqualTo(id);
    }

    @Test
    @DisplayName("requireCompanyId throws when no tenant is set")
    void requireWithoutSet() {
        TenantContext.clear();
        assertThatThrownBy(TenantContext::requireCompanyId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No tenant context");
    }

    @Test
    @DisplayName("clear removes the value")
    void clear() {
        TenantContext.setCompanyId(UUID.randomUUID());
        TenantContext.clear();
        assertThat(TenantContext.getCompanyId()).isNull();
    }
}
