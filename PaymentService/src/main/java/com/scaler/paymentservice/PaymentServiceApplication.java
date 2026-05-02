package com.scaler.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} activates Spring's scheduled task executor,
 * which picks up all {@code @Scheduled} methods in the application context.
 * Without this annotation, {@link com.scaler.paymentservice.scheduler.PaymentReconciliationScheduler}
 * would be registered as a bean but its cron method would never fire.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
