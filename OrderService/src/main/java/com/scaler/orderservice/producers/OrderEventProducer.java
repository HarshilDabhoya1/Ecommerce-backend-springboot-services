package com.scaler.orderservice.producers;

import com.scaler.orderservice.dto.SendEmailEventDto;
import com.scaler.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String SEND_EMAIL_TOPIC = "send-email";

    private final KafkaTemplate<String, SendEmailEventDto> kafkaTemplate;

    public void sendOrderConfirmationEmail(Order order) {
        try {
            String subject = "Order Confirmed — Order #" + order.getId();
            String body = buildOrderConfirmationBody(order);
            SendEmailEventDto event = new SendEmailEventDto(order.getUserEmail(), subject, body);
            kafkaTemplate.send(SEND_EMAIL_TOPIC, String.valueOf(order.getId()), event);
        } catch (Exception e) {
            // Kafka unavailable — order is already saved; email will not be sent.
            // Do not let a Kafka outage fail the HTTP response.
            log.warn("Failed to publish order confirmation event for order #{}: {}", order.getId(), e.getMessage());
        }
    }

    public void sendOrderCancellationEmail(Order order) {
        try {
            String subject = "Order Cancelled — Order #" + order.getId();
            String body = "<p>Hi,</p>"
                    + "<p>Your order <strong>#" + order.getId() + "</strong> has been <strong>cancelled</strong>.</p>"
                    + "<p>If you did not request this cancellation, please contact support.</p>";
            SendEmailEventDto event = new SendEmailEventDto(order.getUserEmail(), subject, body);
            kafkaTemplate.send(SEND_EMAIL_TOPIC, String.valueOf(order.getId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish order cancellation event for order #{}: {}", order.getId(), e.getMessage());
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private String buildOrderConfirmationBody(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hi,</p>");
        sb.append("<p>Thank you for your order! Here is your summary:</p>");
        sb.append("<table border='1' cellpadding='6' cellspacing='0'>");
        sb.append("<tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr>");

        order.getItems().forEach(item -> {
            sb.append("<tr>")
              .append("<td>").append(item.getProductTitle()).append("</td>")
              .append("<td>").append(item.getQuantity()).append("</td>")
              .append("<td>₹").append(String.format("%.2f", item.getUnitPrice())).append("</td>")
              .append("<td>₹").append(String.format("%.2f", item.getQuantity() * item.getUnitPrice())).append("</td>")
              .append("</tr>");
        });

        sb.append("</table>");
        sb.append("<p><strong>Total: ₹").append(String.format("%.2f", order.getTotalAmount())).append("</strong></p>");
        sb.append("<p>We'll notify you once your order is shipped.</p>");
        return sb.toString();
    }
}
