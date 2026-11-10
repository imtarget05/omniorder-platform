package com.omniorder.gateway.config;

import com.omniorder.gateway.filter.JwtAuthenticationFilter;
import com.omniorder.gateway.filter.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public GatewayConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                         RateLimitingFilter rateLimitingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("identity-service", r -> r.path("/api/v1/auth/**")
                .filters(f -> f
                    .filter(rateLimitingFilter.apply(new RateLimitingFilter.Config(100, 60)))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config(true))))
                .uri("lb://identity-service"))
            .route("order-service", r -> r.path("/api/v1/orders/**")
                .filters(f -> f
                    .filter(rateLimitingFilter.apply(new RateLimitingFilter.Config(100, 60)))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config(true))))
                .uri("lb://order-service"))
            .route("inventory-service", r -> r.path("/api/v1/products/**")
                .filters(f -> f
                    .filter(rateLimitingFilter.apply(new RateLimitingFilter.Config(100, 60)))
                    .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config(true))))
                .uri("lb://inventory-service"))
            .build();
    }
}
