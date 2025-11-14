package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    
    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;
    
    @Column(name = "p256dh", nullable = false, columnDefinition = "TEXT")
    private String p256dh;
    
    @Column(name = "auth", nullable = false, columnDefinition = "TEXT")
    private String auth;

    @Column(name = "device_type")
    private String deviceType;  // "web", "mobile", "tablet"
    
    @Column(name = "browser")
    private String browser;  // "Chrome", "Firefox", "Safari", etc.
    
    @Column(name = "os")
    private String os;  // "Windows", "Android", "iOS", "macOS", "Linux"
    
    @Column(name = "device_name")
    private String deviceName;  // "Windows PC", "iPhone", "Samsung Galaxy", etc.
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public PushSubscription() {}
    
    public PushSubscription(String userEmail, String endpoint, String p256dh, String auth) {
        this.userEmail = userEmail;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUserEmail() {
        return userEmail;
    }
    
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public String getP256dh() {
        return p256dh;
    }
    
    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }
    
    public String getAuth() {
        return auth;
    }
    
    public void setAuth(String auth) {
        this.auth = auth;
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

    public String getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }
    
    public String getBrowser() {
        return browser;
    }
    
    public void setBrowser(String browser) {
        this.browser = browser;
    }
    
    public String getOs() {
        return os;
    }
    
    public void setOs(String os) {
        this.os = os;
    }
    
    public String getDeviceName() {
        return deviceName;
    }
    
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    
    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }
    
    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }    
}
