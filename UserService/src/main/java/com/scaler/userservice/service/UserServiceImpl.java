package com.scaler.userservice.service;

import com.scaler.userservice.model.Role;
import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.SessionStatus;
import com.scaler.userservice.model.User;
import com.scaler.userservice.producers.UserEventProducer;
import com.scaler.userservice.repository.SessionRepository;
import com.scaler.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;

    public UserServiceImpl(UserRepository userRepository, SessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder, UserEventProducer userEventProducer) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.userEventProducer = userEventProducer;
    }

    @Override
    public User signUp(String name, String email, String password) {
        return createUser(name, email, password, Role.USER);
    }

    @Override
    public User signUpAdmin(String name, String email, String password) {
        return createUser(name, email, password, Role.ADMIN);
    }

    private User createUser(String name, String email, String password, Role role) {
        if (userRepository.findByEmail(email).isPresent()) {
            return null;
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);

        User saved = userRepository.save(user);

        // Fire-and-forget: publish welcome email event to Kafka
        userEventProducer.sendWelcomeEmail(saved);

        return saved;
    }

    @Override
    public Session login(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }

        String token = UUID.randomUUID().toString();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 3);

        Session session = new Session();
        session.setToken(token);
        session.setUser(user);
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpiredAt(calendar.getTime());

        return sessionRepository.save(session);
    }

    @Override
    public void logout(String token) {
        Optional<Session> sessionOpt = sessionRepository.findByTokenAndStatus(token, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            return;
        }

        Session session = sessionOpt.get();
        session.setStatus(SessionStatus.ENDED);
        sessionRepository.save(session);
    }

    @Override
    public User validateToken(String token) {
        Optional<Session> sessionOpt = sessionRepository.findByTokenAndStatus(token, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            return null;
        }

        Session session = sessionOpt.get();

        if (session.getExpiredAt().before(new Date())) {
            session.setStatus(SessionStatus.ENDED);
            sessionRepository.save(session);
            return null;
        }

        return session.getUser();
    }
}
