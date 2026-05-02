package com.scaler.userservice.service;

import com.scaler.userservice.model.Role;
import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.SessionStatus;
import com.scaler.userservice.model.User;
import com.scaler.userservice.producers.UserEventProducer;
import com.scaler.userservice.repository.SessionRepository;
import com.scaler.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock SessionRepository sessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserEventProducer userEventProducer;

    @InjectMocks UserServiceImpl userService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setName("Harshil Dabhoya");
        u.setEmail(email);
        u.setPassword("$2a$10$encodedPassword");
        u.setRole(Role.USER);
        return u;
    }

    private Session buildSession(User user, SessionStatus status, Date expiredAt) {
        Session s = new Session();
        s.setId(1L);
        s.setToken("uuid-token-abc");
        s.setUser(user);
        s.setStatus(status);
        s.setExpiredAt(expiredAt);
        return s;
    }

    // ── signUp ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("signUp")
    class SignUp {

        @Test
        @DisplayName("returns saved User when email is not already registered")
        void success_whenEmailNotTaken() {
            given(userRepository.findByEmail("harshil@example.com")).willReturn(Optional.empty());
            given(passwordEncoder.encode("rawPass")).willReturn("$2a$encodedPass");

            User saved = buildUser(1L, "harshil@example.com");
            given(userRepository.save(any(User.class))).willReturn(saved);

            User result = userService.signUp("Harshil Dabhoya", "harshil@example.com", "rawPass");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("harshil@example.com");
        }

        @Test
        @DisplayName("encodes the raw password before persisting")
        void encodesPassword() {
            given(userRepository.findByEmail(any())).willReturn(Optional.empty());
            given(passwordEncoder.encode("rawPass")).willReturn("$2a$encoded");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            userService.signUp("Harshil Dabhoya", "harshil@example.com", "rawPass");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
        }

        @Test
        @DisplayName("always sets Role.USER on new sign-up (no self-promotion to ADMIN)")
        void setsRoleUser() {
            given(userRepository.findByEmail(any())).willReturn(Optional.empty());
            given(passwordEncoder.encode(any())).willReturn("encoded");
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            userService.signUp("Harshil", "harshil@example.com", "pass");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("publishes a welcome email event after successful sign-up")
        void publishesWelcomeEmail() {
            given(userRepository.findByEmail(any())).willReturn(Optional.empty());
            given(passwordEncoder.encode(any())).willReturn("encoded");
            User saved = buildUser(1L, "harshil@example.com");
            given(userRepository.save(any())).willReturn(saved);

            userService.signUp("Harshil", "harshil@example.com", "pass");

            then(userEventProducer).should().sendWelcomeEmail(saved);
        }

        @Test
        @DisplayName("returns null when email is already registered")
        void returnsNull_whenEmailTaken() {
            given(userRepository.findByEmail("harshil@example.com"))
                    .willReturn(Optional.of(buildUser(1L, "harshil@example.com")));

            User result = userService.signUp("Harshil", "harshil@example.com", "pass");

            assertThat(result).isNull();
            then(userRepository).should(never()).save(any());
            then(userEventProducer).should(never()).sendWelcomeEmail(any());
        }
    }

    // ── login ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a saved ACTIVE session when credentials are correct")
        void success_whenCredentialsValid() {
            User user = buildUser(1L, "harshil@example.com");
            given(userRepository.findByEmail("harshil@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("rawPass", user.getPassword())).willReturn(true);

            Session savedSession = buildSession(user, SessionStatus.ACTIVE,
                    Date.from(Instant.now().plusSeconds(86400 * 3)));
            given(sessionRepository.save(any(Session.class))).willReturn(savedSession);

            Session result = userService.login("harshil@example.com", "rawPass");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(result.getUser()).isEqualTo(user);
        }

        @Test
        @DisplayName("generates a UUID token for the new session")
        void generatesUuidToken() {
            User user = buildUser(1L, "harshil@example.com");
            given(userRepository.findByEmail(any())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(true);
            given(sessionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            userService.login("harshil@example.com", "rawPass");

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            then(sessionRepository).should().save(captor.capture());
            String token = captor.getValue().getToken();
            // UUID has the form xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 chars)
            assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("sets session expiry ~3 days in the future")
        void setsSessionExpiryTo3Days() {
            User user = buildUser(1L, "harshil@example.com");
            given(userRepository.findByEmail(any())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(any(), any())).willReturn(true);
            given(sessionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            userService.login("harshil@example.com", "rawPass");

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            then(sessionRepository).should().save(captor.capture());
            Date expiredAt = captor.getValue().getExpiredAt();

            // Should be roughly 3 days from now (within a 10-second window for test speed)
            long expectedMs = System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000);
            assertThat(expiredAt.getTime()).isCloseTo(expectedMs, org.assertj.core.data.Offset.offset(10_000L));
        }

        @Test
        @DisplayName("returns null when email is not registered")
        void returnsNull_whenEmailNotFound() {
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            Session result = userService.login("unknown@example.com", "pass");

            assertThat(result).isNull();
            then(sessionRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("returns null when password is incorrect")
        void returnsNull_whenPasswordWrong() {
            User user = buildUser(1L, "harshil@example.com");
            given(userRepository.findByEmail("harshil@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPass", user.getPassword())).willReturn(false);

            Session result = userService.login("harshil@example.com", "wrongPass");

            assertThat(result).isNull();
            then(sessionRepository).should(never()).save(any());
        }
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("marks the active session as ENDED")
        void endsActiveSession() {
            User user = buildUser(1L, "harshil@example.com");
            Session session = buildSession(user, SessionStatus.ACTIVE,
                    Date.from(Instant.now().plusSeconds(86400)));
            given(sessionRepository.findByTokenAndStatus("uuid-token-abc", SessionStatus.ACTIVE))
                    .willReturn(Optional.of(session));
            given(sessionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            userService.logout("uuid-token-abc");

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            then(sessionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.ENDED);
        }

        @Test
        @DisplayName("does nothing gracefully when no active session exists for the token")
        void doesNothing_whenSessionNotFound() {
            given(sessionRepository.findByTokenAndStatus(any(), eq(SessionStatus.ACTIVE)))
                    .willReturn(Optional.empty());

            userService.logout("non-existent-token");   // must not throw

            then(sessionRepository).should(never()).save(any());
        }
    }

    // ── validateToken ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("returns the User when token is active and not yet expired")
        void returnsUser_whenTokenValid() {
            User user = buildUser(1L, "harshil@example.com");
            Session session = buildSession(user, SessionStatus.ACTIVE,
                    Date.from(Instant.now().plusSeconds(86400 * 2)));  // expires in 2 days
            given(sessionRepository.findByTokenAndStatus("uuid-token-abc", SessionStatus.ACTIVE))
                    .willReturn(Optional.of(session));

            User result = userService.validateToken("uuid-token-abc");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("harshil@example.com");
        }

        @Test
        @DisplayName("returns null and ends the session when token is expired")
        void returnsNull_andEndsSession_whenExpired() {
            User user = buildUser(1L, "harshil@example.com");
            // Expiry set to 1 hour in the PAST → token is expired
            Session session = buildSession(user, SessionStatus.ACTIVE,
                    Date.from(Instant.now().minusSeconds(3600)));
            given(sessionRepository.findByTokenAndStatus("uuid-token-abc", SessionStatus.ACTIVE))
                    .willReturn(Optional.of(session));
            given(sessionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            User result = userService.validateToken("uuid-token-abc");

            assertThat(result).isNull();
            // The expired session must be marked ENDED
            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            then(sessionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.ENDED);
        }

        @Test
        @DisplayName("returns null when no active session exists for the token")
        void returnsNull_whenSessionNotFound() {
            given(sessionRepository.findByTokenAndStatus("bad-token", SessionStatus.ACTIVE))
                    .willReturn(Optional.empty());

            User result = userService.validateToken("bad-token");

            assertThat(result).isNull();
        }
    }
}
