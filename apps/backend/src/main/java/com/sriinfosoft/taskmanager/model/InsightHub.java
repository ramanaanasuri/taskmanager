package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A learning insightHub: one mentor sharing knowledge with several members.
 * Follows the codebase convention of referencing users by email string
 * (like Task.userEmail) rather than a JPA relationship.
 */
@Entity
@Table(name = "insight_hubs", indexes = {
    @Index(name = "idx_hub_mentor", columnList = "mentor_email")
})
public class InsightHub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_email", nullable = false)
    private String mentorEmail;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public InsightHub() {}

    public InsightHub(String mentorEmail, String name) {
        this.mentorEmail = mentorEmail;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMentorEmail() { return mentorEmail; }
    public void setMentorEmail(String mentorEmail) { this.mentorEmail = mentorEmail; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
