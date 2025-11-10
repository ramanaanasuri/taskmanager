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
    
    @Column(nullable = false, length = 500, unique = true)
    private String endpoint;
    
    @Column(nullable = false, length = 200)
    private String p256dh;
    
    @Column(nullable = false, length = 50)
    private String auth;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Constructors
    public PushSubscription() {}
    
    public PushSubscription(String userEmail, String endpoint, String p256dh, String auth) {
        this.userEmail = userEmail;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
