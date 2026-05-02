package com.scaler.paymentservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    @Value("${stripe.secret-key}")
    private String secretKey;

    /**
     * Initialises the Stripe SDK with the secret key from application.properties.
     * In test mode the key always starts with "sk_test_".
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        boolean testMode = secretKey.startsWith("sk_test_");
        log.info("Stripe SDK initialised — test mode: {}", testMode);
    }
}
