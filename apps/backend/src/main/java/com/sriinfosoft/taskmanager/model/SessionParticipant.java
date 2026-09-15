package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A learner enrolled in an offering. UNIQUE(session_offering_id, user_email)
 * guarantees idempotent enrolment (no duplicate seat for the same user);
 * capacity is protected separately by a pessimistic lock on the offering
 * during the seat-claim. checked_in_at supports the lobby handshake.
 */
@Entity
@Table(name = "session_participant",
    uniqueConstraints = @UniqueConstraint(name = "uk_participant",
        columnNames = {"session_offering_id", "user_email"}),
    indexes = {
        @Index(name = "idx_sp_offering", columnList = "session_offering_id"),
        @Index(name = "idx_sp_user_email", columnList = "user_email"),
        @Index(name = "idx_sp_user_id", columnList = "user_id")
    })
public class SessionParticipant {

    public enum Role { LEARNER, MENTOR_OBSERVER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_offering_id", nullable = false)
    private Long sessionOfferingId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.LEARNER;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt = LocalDateTime.now();

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    public SessionParticipant() {}
    public SessionParticipant(Long sessionOfferingId, String userEmail, Role role) {
        this.sessionOfferingId = sessionOfferingId; this.userEmail = userEmail; this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionOfferingId() { return sessionOfferingId; }
    public void setSessionOfferingId(Long v) { this.sessionOfferingId = v; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String v) { this.userEmail = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { this.role = v; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime v) { this.enrolledAt = v; }
    public LocalDateTime getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(LocalDateTime v) { this.checkedInAt = v; }
}
