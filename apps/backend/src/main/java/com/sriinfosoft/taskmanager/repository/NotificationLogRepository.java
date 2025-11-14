package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing notification logs
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    
    /**
     * Find all logs for a specific task
     */
    List<NotificationLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    
    /**
     * Find all logs for a specific user
     */
    List<NotificationLog> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    
    /**
     * Find logs by status (sent, failed, pending)
     */
    List<NotificationLog> findByStatusOrderByCreatedAtDesc(String status);
    
    /**
     * Find logs within a date range
     */
    @Query("SELECT nl FROM NotificationLog nl WHERE nl.createdAt BETWEEN :start AND :end ORDER BY nl.createdAt DESC")
    List<NotificationLog> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * Count total notifications sent
     */
    @Query("SELECT COUNT(nl) FROM NotificationLog nl WHERE nl.status = 'sent'")
    long countSuccessfulNotifications();
    
    /**
     * Count failed notifications
     */
    @Query("SELECT COUNT(nl) FROM NotificationLog nl WHERE nl.status = 'failed'")
    long countFailedNotifications();
}