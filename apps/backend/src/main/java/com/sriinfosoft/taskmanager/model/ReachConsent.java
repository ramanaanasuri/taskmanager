package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A user's reachability agreement: which channels they opted in to and the
 * terms version they accepted. One row per user (identified by email).
 */
@Entity
@Table(name = "reach_consent",
    uniqueConstraints = @UniqueConstraint(name = "uk_reach_consent_email",
        columnNames = {"user_email"}))
public class ReachConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    /** Comma-separated selected channels, e.g. "EMAIL,SMS". */
    @Column(name = "channels_selected")
    private String channelsSelected;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ReachConsent() {}

    public ReachConsent(String userEmail, String channelsSelected, String termsVersion) {
        this.userEmail = userEmail;
        this.channelsSelected = channelsSelected;
        this.termsVersion = termsVersion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getChannelsSelected() { return channelsSelected; }
    public void setChannelsSelected(String channelsSelected) { this.channelsSelected = channelsSelected; }

    public String getTermsVersion() { return termsVersion; }
    public void setTermsVersion(String termsVersion) { this.termsVersion = termsVersion; }

    public LocalDateTime getAgreedAt() { return agreedAt; }
    public void setAgreedAt(LocalDateTime agreedAt) { this.agreedAt = agreedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
