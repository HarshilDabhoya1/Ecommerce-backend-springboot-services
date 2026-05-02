package com.scaler.reviewservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Isolated JPA auditing configuration.
 *
 * Keeping @EnableJpaAuditing here (rather than on ReviewServiceApplication) prevents
 * the "JPA metamodel must not be empty" error in @WebMvcTest unit tests.
 * @WebMvcTest only loads the web layer and has no JPA context, so placing
 * @EnableJpaAuditing on the main class causes the jpaAuditingHandler bean to
 * fail during test startup. A plain @Configuration class is excluded by @WebMvcTest
 * automatically, so the auditing handler is never instantiated in that context.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
