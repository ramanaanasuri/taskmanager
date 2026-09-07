package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable event-log row: who did what, when, from where.
 * The admin digest and the AI guardrail are both queries over this table.
 */
@Entity
@Table(name = "user_activity")
public class UserActivity {

    public static final String SIGNUP = "signup";
    public static final String LOGIN = "login";
    public static final String AI_CALL = "ai_call";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    private String detail;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public UserActivity() {}

    public UserActivity(String email, String eventType, String detail, String sourceIp) {
        this.email = email;
        this.eventType = eventType;
        this.detail = detail;
        this.sourceIp = sourceIp;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public String getSourceIp() { return sourceIp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
