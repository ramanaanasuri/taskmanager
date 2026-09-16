package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-user, per-channel reachability state. A user opts in to channels and
 * each must be verified before it counts. Guaranteed channels (EMAIL, SMS)
 * gate participation; PUSH is best-effort and never counts toward the floor.
 * Identified by email, consistent with existing ownership tables.
 */
@Entity
@Table(name = "channel_verification",
    uniqueConstraints = @UniqueConstraint(name = "uk_channel_verification",
        columnNames = {"user_email", "channel"}),
    indexes = @Index(name = "idx_cv_email", columnList = "user_email"))
public class ChannelVerification {

    public enum Channel { EMAIL, SMS, PUSH }
    public enum Status { SELECTED, PENDING, VERIFIED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    /** The address/number this channel verifies to (account email or phone). */
    @Column(name = "value")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.SELECTED;

    @Column(name = "code")
    private String code;

    @Column(name = "code_expires_at")
    private LocalDateTime codeExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "last_send_failed_at")
    private LocalDateTime lastSendFailedAt;

    /** Failed confirm attempts on the current code — brute-force guard. */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /** The last CONFIRMED value (phone/email). Set only on successful confirm;
     *  never cleared by requesting a new code — so a re-verify never drops the
     *  currently-verified number the send-gate relies on. */
    @Column(name = "verified_value")
    private String verifiedValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ChannelVerification() {}

    public ChannelVerification(String userEmail, Channel channel) {
        this.userEmail = userEmail;
        this.channel = channel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getCodeExpiresAt() { return codeExpiresAt; }
    public void setCodeExpiresAt(LocalDateTime codeExpiresAt) { this.codeExpiresAt = codeExpiresAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getLastSendFailedAt() { return lastSendFailedAt; }
    public void setLastSendFailedAt(LocalDateTime lastSendFailedAt) { this.lastSendFailedAt = lastSendFailedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public String getVerifiedValue() { return verifiedValue; }
    public void setVerifiedValue(String verifiedValue) { this.verifiedValue = verifiedValue; }
}
