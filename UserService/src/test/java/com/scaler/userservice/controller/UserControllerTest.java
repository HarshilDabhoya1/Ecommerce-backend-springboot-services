package com.scaler.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.userservice.dto.LoginRequestDto;
import com.scaler.userservice.dto.SignUpRequestDto;
import com.scaler.userservice.model.Role;
import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.SessionStatus;
import com.scaler.userservice.model.User;
import com.scaler.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Security is excluded because UserService is the auth service itself —
 * security for its own endpoints is handled at the API Gateway level.
 * Excluding it keeps these tests focused purely on controller logic.
 */
@WebMvcTest(
    value = UserController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserService userService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private User buildUser() {
        User u = new User();
        u.setId(1L);
        u.setName("Harshil Dabhoya");
        u.setEmail("harshil@example.com");
        u.setPassword("$2a$encoded");
        u.setRole(Role.USER);
        return u;
    }

    private Session buildSession(User user) {
        Session s = new Session();
        s.setId(1L);
        s.setToken("test-uuid-token");
        s.setUser(user);
        s.setStatus(SessionStatus.ACTIVE);
        s.setExpiredAt(Date.from(Instant.now().plusSeconds(86400 * 3)));
        return s;
    }

    // ── POST /users/signup ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /users/signup")
    class SignUp {

        @Test
        @DisplayName("returns 201 CREATED with user body on successful registration")
        void returns201_onSuccess() throws Exception {
            given(userService.signUp("Harshil Dabhoya", "harshil@example.com", "password123"))
                    .willReturn(buildUser());

            SignUpRequestDto request = new SignUpRequestDto();
            request.setName("Harshil Dabhoya");
            request.setEmail("harshil@example.com");
            request.setPassword("password123");

            mockMvc.perform(post("/users/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("harshil@example.com"))
                    .andExpect(jsonPath("$.name").value("Harshil Dabhoya"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    // Password must never be returned in the response
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("returns 409 CONFLICT when email is already registered")
        void returns409_whenEmailTaken() throws Exception {
            given(userService.signUp(anyString(), anyString(), anyString())).willReturn(null);

            SignUpRequestDto request = new SignUpRequestDto();
            request.setName("Harshil");
            request.setEmail("harshil@example.com");
            request.setPassword("password123");

            mockMvc.perform(post("/users/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    // ── POST /users/login ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /users/login")
    class Login {

        @Test
        @DisplayName("returns 200 OK with user body and AUTH_TOKEN header on success")
        void returns200_withToken_onSuccess() throws Exception {
            User user = buildUser();
            Session session = buildSession(user);
            given(userService.login("harshil@example.com", "password123")).willReturn(session);

            LoginRequestDto request = new LoginRequestDto();
            request.setEmail("harshil@example.com");
            request.setPassword("password123");

            mockMvc.perform(post("/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    // Token must be in the response header so the client can store it
                    .andExpect(header().string("AUTH_TOKEN", "test-uuid-token"))
                    .andExpect(jsonPath("$.email").value("harshil@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("returns 401 UNAUTHORIZED when credentials are wrong")
        void returns401_whenCredentialsWrong() throws Exception {
            given(userService.login(anyString(), anyString())).willReturn(null);

            LoginRequestDto request = new LoginRequestDto();
            request.setEmail("harshil@example.com");
            request.setPassword("wrongPassword");

            mockMvc.perform(post("/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 401 UNAUTHORIZED when email is not registered")
        void returns401_whenEmailNotFound() throws Exception {
            given(userService.login("noone@example.com", "pass")).willReturn(null);

            LoginRequestDto request = new LoginRequestDto();
            request.setEmail("noone@example.com");
            request.setPassword("pass");

            mockMvc.perform(post("/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /users/logout ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /users/logout")
    class Logout {

        @Test
        @DisplayName("returns 200 OK after logout (service call is void)")
        void returns200_onSuccess() throws Exception {
            mockMvc.perform(post("/users/logout")
                            .header("AUTH_TOKEN", "test-uuid-token"))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /users/validate ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /users/validate")
    class ValidateToken {

        @Test
        @DisplayName("returns 200 OK with user details when token is valid")
        void returns200_whenTokenValid() throws Exception {
            User user = buildUser();
            given(userService.validateToken("valid-token")).willReturn(user);

            mockMvc.perform(post("/users/validate")
                            .header("AUTH_TOKEN", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("harshil@example.com"));
        }

        @Test
        @DisplayName("returns 401 UNAUTHORIZED when token is invalid or expired")
        void returns401_whenTokenInvalid() throws Exception {
            given(userService.validateToken("expired-token")).willReturn(null);

            mockMvc.perform(post("/users/validate")
                            .header("AUTH_TOKEN", "expired-token"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
