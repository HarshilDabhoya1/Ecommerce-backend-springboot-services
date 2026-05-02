package com.scaler.userservice.repository;

import com.scaler.userservice.model.Session;
import com.scaler.userservice.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByTokenAndStatus(String token, SessionStatus status);
}