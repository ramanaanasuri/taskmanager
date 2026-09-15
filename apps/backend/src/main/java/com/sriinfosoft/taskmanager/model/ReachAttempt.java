package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Log of every reach/nudge: proof of outreach and future intelligence. */
@Entity
@Table(name = "reach_attempt",
    indexes = {
        @Index(name = "idx_ra_offering", columnList = "session_offering_id"),
        @Index(name = "idx_ra_to", columnList = "to_email")
    })
public class ReachAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_offering_id", nullable = false)
    private Long sessionOfferingId;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    /** Channels actually used, e.g. "EMAIL,SMS". */
    @Column(name = "channels")
    private String channels;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReachAttempt() {}
    public ReachAttempt(Long offeringId, String fromEmail, String toEmail, String channels, String outcome) {
        this.sessionOfferingId = offeringId; this.fromEmail = fromEmail;
        this.toEmail = toEmail; this.channels = channels; this.outcome = outcome;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionOfferingId() { return sessionOfferingId; }
    public void setSessionOfferingId(Long v) { this.sessionOfferingId = v; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String v) { this.fromEmail = v; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String v) { this.toEmail = v; }
    public String getChannels() { return channels; }
    public void setChannels(String v) { this.channels = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
