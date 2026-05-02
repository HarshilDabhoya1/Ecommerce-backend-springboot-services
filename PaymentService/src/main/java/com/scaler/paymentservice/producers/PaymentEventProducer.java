package com.scaler.paymentservice.producers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.paymentservice.dto.SendEmailEventDto;
import com.scaler.paymentservice.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);
    private static final String EMAIL_TOPIC = "send-email";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // ── Payment success ──────────────────────────────────────────────────────

    public void sendPaymentSuccessEmail(Payment payment) {
        String subject = "Payment Confirmed – Order #" + payment.getOrderId();
        String body = """
                <html><body>
                <h2>Payment Successful!</h2>
                <p>Hi,</p>
                <p>Your payment of <strong>%s %.2f</strong> for Order <strong>#%d</strong> has been processed successfully.</p>
                <p><strong>Transaction ID:</strong> %s</p>
                <p><strong>Payment Method:</strong> %s</p>
                <p>Thank you for shopping with us!</p>
                </body></html>
                """.formatted(
                payment.getCurrency(),
                payment.getAmount(),
                payment.getOrderId(),
                payment.getTransactionId(),
                payment.getPaymentMethod().name().replace("_", " ")
        );

        publish(payment.getUserEmail(), subject, body);
    }

    // ── Payment failed ────────────────────────────────────────────────────────

    public void sendPaymentFailedEmail(Payment payment) {
        String subject = "Payment Failed – Order #" + payment.getOrderId();
        String body = """
                <html><body>
                <h2>Payment Failed</h2>
                <p>Hi,</p>
                <p>Unfortunately, your payment of <strong>%s %.2f</strong> for Order <strong>#%d</strong> could not be processed.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>Please try again or use a different payment method. If the problem persists, contact our support team.</p>
                </body></html>
                """.formatted(
                payment.getCurrency(),
                payment.getAmount(),
                payment.getOrderId(),
                payment.getFailureReason()
        );

        publish(payment.getUserEmail(), subject, body);
    }

    // ── Refund ────────────────────────────────────────────────────────────────

    public void sendRefundEmail(Payment refund, String reason) {
        String subject = "Refund Initiated – Order #" + refund.getOrderId();
        String body = """
                <html><body>
                <h2>Refund Initiated</h2>
                <p>Hi,</p>
                <p>A refund of <strong>%s %.2f</strong> for Order <strong>#%d</strong> has been initiated.</p>
                <p><strong>Reason:</strong> %s</p>
                <p><strong>Refund Reference:</strong> %s</p>
                <p>Please allow 5–7 business days for the amount to reflect in your account.</p>
                </body></html>
                """.formatted(
                refund.getCurrency(),
                refund.getAmount(),
                refund.getOrderId(),
                reason,
                refund.getTransactionId()
        );

        publish(refund.getUserEmail(), subject, body);
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void publish(String to, String subject, String body) {
        try {
            SendEmailEventDto event = new SendEmailEventDto(to, subject, body);
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(EMAIL_TOPIC, json);
            log.info("Payment email event published to topic '{}' for recipient '{}'", EMAIL_TOPIC, to);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment email event for recipient '{}': {}", to, e.getMessage());
        } catch (Exception e) {
            // Kafka unavailable — payment is already saved; email will not be sent.
            // Do not let a Kafka outage fail the HTTP response.
            log.warn("Failed to publish payment email event for recipient '{}': {}", to, e.getMessage());
        }
    }
}
