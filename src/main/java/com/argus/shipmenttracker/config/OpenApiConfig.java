package com.argus.shipmenttracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI shipmentTrackerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Shipment Tracker API")
                .version("v1")
                .description("""
                    Real-time shipment event tracking API.
                    Records carrier events, exposes a unified status view,
                    and notifies subscribed external systems via signed
                    webhooks. Multi-tenant, JWT-authenticated, rate-limited.""")
                .contact(new Contact().name("Argus Logistics Engineering").email("api@arguslogistics.com"))
                .license(new License().name("Proprietary")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local development"),
                new Server().url("https://api.shipment-tracker.example.com").description("Production")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT issued by POST /api/v1/auth/token")));
    }
}
