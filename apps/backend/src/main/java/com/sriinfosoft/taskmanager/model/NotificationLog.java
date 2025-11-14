package com.sriinfosoft.taskmanager.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a log of notification attempts
 * Tracks all notifications sent (successful or failed)
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;  // 'push', 'email', 'sms'

    @Column(name = "status", nullable = false)
    private String status;  // 'sent', 'failed', 'pending'

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_to_endpoint", columnDefinition = "TEXT")
    private String sentToEndpoint;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public NotificationLog() {
    }

    public NotificationLog(Long taskId, String userEmail, String notificationType, String status) {
        this.taskId = taskId;
        this.userEmail = userEmail;
        this.notificationType = notificationType;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSentToEndpoint() {
        return sentToEndpoint;
    }

    public void setSentToEndpoint(String sentToEndpoint) {
        this.sentToEndpoint = sentToEndpoint;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "NotificationLog{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", userEmail='" + userEmail + '\'' +
                ", notificationType='" + notificationType + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}