package com.scaler.emailservice.consumers;

import com.scaler.emailservice.dtos.SendEmailEventDto;
import com.scaler.emailservice.utils.EmailUtil;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendEmailConsumer {

    private final EmailUtil emailUtil;

    @KafkaListener(topics = "send-email", groupId = "email-service")
    public void onSendEmailEvent(SendEmailEventDto event) {
        try {
            emailUtil.sendEmail(event.getTo(), event.getSubject(), event.getBody());
        } catch (MessagingException e) {
            // log and handle — could push to a dead-letter topic
            System.err.println("Failed to send email to " + event.getTo() + ": " + e.getMessage());
        }
    }
}