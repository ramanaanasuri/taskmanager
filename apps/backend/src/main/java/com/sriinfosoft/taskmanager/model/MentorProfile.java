package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A user who has opted in to teach. Identified by email; user_id nullable for migration. */
@Entity
@Table(name = "mentor_profile",
    uniqueConstraints = @UniqueConstraint(name = "uk_mentor_email", columnNames = {"mentor_email"}),
    indexes = { @Index(name = "idx_mp_user_id", columnList = "mentor_user_id"),
                @Index(name = "idx_mp_active", columnList = "active") })
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_email", nullable = false)
    private String mentorEmail;

    @Column(name = "mentor_user_id")
    private Long mentorUserId;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MentorProfile() {}
    public MentorProfile(String mentorEmail) { this.mentorEmail = mentorEmail; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMentorEmail() { return mentorEmail; }
    public void setMentorEmail(String mentorEmail) { this.mentorEmail = mentorEmail; }
    public Long getMentorUserId() { return mentorUserId; }
    public void setMentorUserId(Long mentorUserId) { this.mentorUserId = mentorUserId; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
