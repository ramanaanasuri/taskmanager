package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.ReachAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReachAttemptRepository extends JpaRepository<ReachAttempt, Long> {
    long countByToEmailAndCreatedAtAfter(String toEmail, LocalDateTime after);
}
