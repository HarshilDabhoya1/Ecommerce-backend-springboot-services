package com.scaler.paymentservice.scheduler;

import com.scaler.paymentservice.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Cron Job — automatically triggers payment reconciliation on a schedule.
 *
 * <h2>What is a Cron Job?</h2>
 * A cron job is a task that runs automatically at a fixed time or interval,
 * without anyone manually triggering it. The name comes from the Unix {@code cron}
 * daemon. In Spring Boot, you use {@code @Scheduled} and enable it with
 * {@code @EnableScheduling} on a config class.
 *
 * <h2>Cron Expression Format</h2>
 * Spring's cron uses 6 fields (unlike Unix which uses 5):
 * <pre>
 *   ┌─ second       (0–59)
 *   │  ┌─ minute    (0–59)
 *   │  │  ┌─ hour   (0–23)
 *   │  │  │  ┌─ day of month (1–31)
 *   │  │  │  │  ┌─ month     (1–12 or JAN–DEC)
 *   │  │  │  │  │  ┌─ day of week (0–7 or MON–SUN; 0 and 7 = Sunday)
 *   │  │  │  │  │  │
 *   0  0  *  *  *  *   ← every hour at minute 0
 *   0  0  2  *  *  *   ← every day at 2:00 AM
 *   0 30  9  *  *  MON ← every Monday at 09:30 AM
 * </pre>
 *
 * <h2>This Scheduler</h2>
 * <ul>
 *   <li>Runs reconciliation <b>every hour at minute 0</b> (e.g. 1:00, 2:00, 3:00…).</li>
 *   <li>The threshold inside {@link ReconciliationService} is 10 minutes — so any payment
 *       stuck in PENDING for 10+ minutes will be investigated within the hour.</li>
 *   <li>The schedule is configurable via {@code RECONCILIATION_CRON} environment variable
 *       without recompiling (defaults to hourly if not set).</li>
 * </ul>
 */
@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    public PaymentReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Runs every hour at minute 0 by default.
     *
     * <p>Override by setting the {@code RECONCILIATION_CRON} environment variable, e.g.:
     * <ul>
     *   <li>{@code 0 0 * * * *}   — every hour (default)</li>
     *   <li>{@code 0 *\/5 * * * *} — every 5 minutes (useful for dev testing)</li>
     *   <li>{@code 0 0 2 * * *}   — every day at 2 AM</li>
     * </ul>
     */
    @Scheduled(cron = "${RECONCILIATION_CRON:0 0 * * * *}")
    public void runReconciliation() {
        log.info("=== Reconciliation job started at {} ===", Instant.now());
        try {
            ReconciliationService.ReconciliationResult result = reconciliationService.reconcile();
            log.info("=== Reconciliation job finished — {} ===", result);
        } catch (Exception e) {
            // Catch everything so one bad run doesn't stop the scheduler from running next time.
            log.error("=== Reconciliation job failed with unexpected error ===", e);
        }
    }
}
