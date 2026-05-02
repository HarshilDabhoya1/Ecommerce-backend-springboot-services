package com.scaler.userservice.producers;

import com.scaler.userservice.dto.SendEmailEventDto;
import com.scaler.userservice.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private static final String SEND_EMAIL_TOPIC = "send-email";

    private final KafkaTemplate<String, SendEmailEventDto> kafkaTemplate;

    public void sendWelcomeEmail(User user) {
        String subject = "Welcome to the platform, " + user.getName() + "!";
        String body = "<p>Hi <strong>" + user.getName() + "</strong>,</p>"
                + "<p>Your account has been created successfully.</p>"
                + "<p>Your registered email is: <strong>" + user.getEmail() + "</strong></p>"
                + "<p>You can now log in and start exploring our products.</p>"
                + "<br><p>Welcome aboard!</p>";

        SendEmailEventDto event = new SendEmailEventDto(user.getEmail(), subject, body);
        kafkaTemplate.send(SEND_EMAIL_TOPIC, String.valueOf(user.getId()), event);
    }
}
