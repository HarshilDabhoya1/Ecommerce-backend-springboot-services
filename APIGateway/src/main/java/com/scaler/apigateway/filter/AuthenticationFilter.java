package com.scaler.apigateway.filter;

import com.scaler.apigateway.dto.UserResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global filter that:
 * 1. Validates the AUTH_TOKEN header against UserService.
 * 2. On success, extracts userId and role from the validate response and
 *    forwards them as X-User-Id and X-User-Role headers so downstream
 *    services can enforce RBAC without calling UserService themselves.
 *
 * Public paths bypass both validation and header injection.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/users/signup", "/users/admin/signup", "/users/login", "/users/logout"
    );

    @Value("${userservice.validate-url}")
    private String userServiceValidateUrl;

    private final WebClient.Builder webClientBuilder;

    public AuthenticationFilter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("AUTH_TOKEN");

        if (token == null || token.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return webClientBuilder.build()
                .post()
                .uri(userServiceValidateUrl)
                .header("AUTH_TOKEN", token)
                .retrieve()
                .bodyToMono(UserResponseDto.class)
                // onErrorResume is placed HERE — before flatMap — so it only covers
                // failures from the UserService validate call (connection refused,
                // 4xx/5xx from UserService, etc.).
                // It must NOT be placed after flatMap, because then it would also
                // catch errors from chain.filter(), masking real downstream failures
                // (e.g. ProductService lb:// unreachable) as a misleading 401.
                .onErrorResume(e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete().then(Mono.empty());
                })
                .flatMap(user -> {
                    // Mutate request: inject user context as headers for downstream services
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id",   String.valueOf(user.getId()))
                            .header("X-User-Role", user.getRole() != null ? user.getRole() : "USER")
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
