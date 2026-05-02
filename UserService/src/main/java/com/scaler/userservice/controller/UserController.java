package com.scaler.userservice.controller;

import com.scaler.userservice.dto.LoginRequestDto;
import com.scaler.userservice.dto.SignUpRequestDto;
import com.scaler.userservice.dto.UserResponseDto;
import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.User;
import com.scaler.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Value("${admin.signup.secret}")
    private String adminSignupSecret;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signUp(@RequestBody SignUpRequestDto request) {
        User user = userService.signUp(request.getName(), request.getEmail(), request.getPassword());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(user));
    }

    /**
     * Creates a user with ADMIN role.
     * Requires X-Admin-Secret header matching the value in application.properties.
     * This endpoint is intentionally NOT in the gateway's public-paths list —
     * it still requires the secret, so it cannot be called without knowing it.
     */
    @PostMapping("/admin/signup")
    public ResponseEntity<UserResponseDto> signUpAdmin(
            @RequestHeader("X-Admin-Secret") String secret,
            @RequestBody SignUpRequestDto request) {

        if (!adminSignupSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User user = userService.signUpAdmin(request.getName(), request.getEmail(), request.getPassword());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(@RequestBody LoginRequestDto request) {
        Session session = userService.login(request.getEmail(), request.getPassword());

        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_TOKEN", session.getToken());

        return ResponseEntity.ok().headers(headers).body(toResponseDto(session.getUser()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("AUTH_TOKEN") String token) {
        userService.logout(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<UserResponseDto> validateToken(@RequestHeader("AUTH_TOKEN") String token) {
        User user = userService.validateToken(token);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(toResponseDto(user));
    }

    private UserResponseDto toResponseDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        return dto;
    }
}