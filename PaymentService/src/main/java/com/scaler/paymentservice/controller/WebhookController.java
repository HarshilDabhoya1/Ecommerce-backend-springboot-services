package com.scaler.paymentservice.controller;

import com.scaler.paymentservice.service.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives and validates Stripe webhook events.
 *
 * <h2>How Stripe Webhooks Work</h2>
 * <ol>
 *   <li>You register this endpoint URL in Stripe Dashboard → Developers → Webhooks.</li>
 *   <li>When a payment event occurs (success, failure, refund…), Stripe sends an HTTP POST
 *       to this endpoint with a JSON payload and a {@code Stripe-Signature} header.</li>
 *   <li>We verify the signature using the Webhook Signing Secret (different from your API key)
 *       to ensure the request is genuinely from Stripe, not a spoofed request.</li>
 *   <li>We process the event and return HTTP 200. If we return any non-2xx status,
 *       Stripe will retry the event for up to 3 days.</li>
 * </ol>
 *
 * <h2>Local Testing with Stripe CLI</h2>
 * <pre>
 *   stripe listen --forward-to localhost:8084/webhooks/stripe
 * </pre>
 * This gives you a temporary webhook secret to put in {@code application-local.properties}.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Stripe webhook receiver.
     *
     * <p>IMPORTANT: the request body must be read as raw bytes/String — do NOT let Spring
     * parse it as JSON first, because the signature is computed over the exact raw bytes.
     * Any transformation (pretty-printing, re-serialising) will break the signature check.
     *
     * @param payload         raw request body as a String
     * @param stripeSignature value of the {@code Stripe-Signature} header sent by Stripe
     * @return 200 OK on success; 400 if the signature is invalid; 500 on processing error
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignature) {

        try {
            webhookService.processEvent(payload, stripeSignature);
            // Always return 200 quickly — heavy processing happens inside the service.
            // Stripe considers anything other than 2xx a failure and will retry.
            return ResponseEntity.ok("Webhook received");

        } catch (SignatureVerificationException e) {
            // Invalid signature — possible spoofed request, reject it.
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");

        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            // Return 500 so Stripe retries — a transient error shouldn't lose the event.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }
}
