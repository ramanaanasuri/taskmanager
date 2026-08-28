package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A question a member asks inside a insightHub. Its status drives the
 * intake loop; the mentor is always the gate between a draft/escalation
 * and anything a member sees.
 *
 *   NEW ── agent drafts ──▶ DRAFTED ── mentor approve ──▶ APPROVED ──▶ DELIVERED
 *    │            │
 *    │            └── mentor reject ──▶ REJECTED
 *    └── agent can't ground ──▶ NEEDS_MENTOR ── mentor writes ──▶ APPROVED ──▶ DELIVERED
 */
@Entity
@Table(name = "insight_hub_questions", indexes = {
    @Index(name = "idx_q_hub", columnList = "insight_hub_id"),
    @Index(name = "idx_q_status", columnList = "status")
})
public class Question {

    public enum Status { NEW, DRAFTED, NEEDS_MENTOR, APPROVED, DELIVERED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_hub_id", nullable = false)
    private Long insightHubId;

    @Column(name = "asked_by_email", nullable = false)
    private String askedByEmail;

    @Column(nullable = false, length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Question() {}

    public Question(Long insightHubId, String askedByEmail, String text) {
        this.insightHubId = insightHubId;
        this.askedByEmail = askedByEmail;
        this.text = text;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInsightHubId() { return insightHubId; }
    public void setInsightHubId(Long insightHubId) { this.insightHubId = insightHubId; }

    public String getAskedByEmail() { return askedByEmail; }
    public void setAskedByEmail(String askedByEmail) { this.askedByEmail = askedByEmail; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
