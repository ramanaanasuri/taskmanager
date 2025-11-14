package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_due_date", columnList = "due_date"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_notifications_enabled", columnList = "notifications_enabled"),
    @Index(name = "idx_reminder_sent", columnList = "reminder_sent"), //ADDED - for better query performance
    @Index(name = "idx_created_from_device", columnList = "created_from_device") //ADDED - for device filtering
})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private Boolean completed = false;
    
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.MEDIUM;    

    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = false;
    
    @Column(name = "push_endpoint", columnDefinition = "TEXT")
    private String pushEndpoint;
    
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(name = "sms_enabled")
    private Boolean smsEnabled = false;

    // NEW: Tracks if notification was already sent to prevent duplicates
    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;
    //ADDED - Track which device created the task (mobile/web/tablet)
    @Column(name = "created_from_device", length = 50)
    private String createdFromDevice = "web";

    //ADDED - Track client IP address for security/debugging
    @Column(name = "created_from_ip", length = 45)
    private String createdFromIp;

    //ADDED - Store full User-Agent string for detailed device info
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;    
    
    // Constructors
    public Task() {}
    
    public Task(String title, String userEmail) {
        this.title = title;
        this.userEmail = userEmail;
    }
    
    // Getters and Setters
    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getTitle() { 
        return title; 
    }
    
    public void setTitle(String title) { 
        this.title = title; 
    }
    
    public Boolean getCompleted() { 
        return completed; 
    }
    
    public void setCompleted(Boolean completed) { 
        this.completed = completed; 
    }
    
    public String getUserEmail() { 
        return userEmail; 
    }
    
    public void setUserEmail(String userEmail) { 
        this.userEmail = userEmail; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }
    
    public LocalDateTime getUpdatedAt() { 
        return updatedAt; 
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) { 
        this.updatedAt = updatedAt; 
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
        // Reset notification sent flag when due date changes
        if (dueDate != null) {
            this.reminderSent = false;
        }
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }  
    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }
    
    public void setNotificationsEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
    
    public String getPushEndpoint() {
        return pushEndpoint;
    }
    
    public void setPushEndpoint(String pushEndpoint) {
        this.pushEndpoint = pushEndpoint;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public Boolean getSmsEnabled() {
        return smsEnabled;
    }
    
    public void setSmsEnabled(Boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }   
    public Boolean getReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(Boolean reminderSent) {
        this.reminderSent = reminderSent;
    }   
    //ADDED - Getter for device tracking
    public String getCreatedFromDevice() {
        return createdFromDevice;
    }

    //ADDED - Setter for device tracking
    public void setCreatedFromDevice(String createdFromDevice) {
        this.createdFromDevice = createdFromDevice;
    }

    //ADDED - Getter for IP tracking
    public String getCreatedFromIp() {
        return createdFromIp;
    }

    //ADDED - Setter for IP tracking
    public void setCreatedFromIp(String createdFromIp) {
        this.createdFromIp = createdFromIp;
    }

    //ADDED - Getter for User-Agent tracking
    public String getUserAgent() {
        return userAgent;
    }

    //ADDED - Setter for User-Agent tracking
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }  
    
    /**
     * Called before persisting a new entity
     * Ensures all Boolean fields have non-null default values
     */
    @PrePersist
    public void prePersist() {
        // Ensure completed is never null
        if (completed == null) {
            completed = false;
        }
        
        // Ensure reminderSent is never null
        if (reminderSent == null) {
            reminderSent = false;
        }
        
        // Ensure notificationsEnabled is never null
        if (notificationsEnabled == null) {
            notificationsEnabled = false;
        }
        
        // Ensure smsEnabled is never null
        if (smsEnabled == null) {
            smsEnabled = false;
        }
        
        // Set timestamps if not already set
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
    /**
     * Called before updating an existing entity
     * Updates the updatedAt timestamp
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Also ensure boolean fields are not null during updates
        if (completed == null) {
            completed = false;
        }
        if (reminderSent == null) {
            reminderSent = false;
        }
        if (notificationsEnabled == null) {
            notificationsEnabled = false;
        }
        if (smsEnabled == null) {
            smsEnabled = false;
        }
    }       
}