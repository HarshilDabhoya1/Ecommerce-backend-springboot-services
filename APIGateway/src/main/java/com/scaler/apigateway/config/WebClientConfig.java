package com.scaler.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Plain WebClient.Builder used by AuthenticationFilter to call
     * UserService /users/validate via a direct URL (not lb://).
     * Gateway routing still uses its own lb:// mechanism for all other routes.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
