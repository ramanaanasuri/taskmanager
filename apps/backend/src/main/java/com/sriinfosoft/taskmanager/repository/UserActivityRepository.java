package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    long countByEmailAndEventTypeAndCreatedAtAfter(String email, String eventType, LocalDateTime after);

    // ADDED for SMS Cost Guard — global daily count, not scoped to one user
    long countByEventTypeAndCreatedAtAfter(String eventType, LocalDateTime after);

    List<UserActivity> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);
}
