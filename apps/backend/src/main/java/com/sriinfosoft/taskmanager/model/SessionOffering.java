package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A live session offered by a mentor. capacity unifies 1:1 (1) and group (N) —
 * same code path. Pricing lives here (rate_amount 0/null = free). Meeting fields
 * are populated on schedule via the MeetingProvider seam.
 */
@Entity
@Table(name = "session_offering",
    indexes = {
        @Index(name = "idx_so_mentor_email", columnList = "mentor_email"),
        @Index(name = "idx_so_mentor_user_id", columnList = "mentor_user_id"),
        @Index(name = "idx_so_skill", columnList = "skill_id"),
        @Index(name = "idx_so_status", columnList = "status"),
        @Index(name = "idx_so_start", columnList = "start_time")
    })
public class SessionOffering {

    public enum Status { DRAFT, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, RESCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_email", nullable = false)
    private String mentorEmail;

    @Column(name = "mentor_user_id")
    private Long mentorUserId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "rate_currency")
    private String rateCurrency;

    @Column(name = "rate_amount")
    private BigDecimal rateAmount;

    @Column(name = "rate_description")
    private String rateDescription;

    @Column(name = "meeting_provider")
    private String meetingProvider;

    @Column(name = "meeting_provider_id")
    private String meetingProviderId;

    @Column(name = "zoom_join_url", length = 500)
    private String zoomJoinUrl;

    @Column(name = "zoom_host_url", length = 500)
    private String zoomHostUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "mentor_checked_in_at")
    private LocalDateTime mentorCheckedInAt;

    public SessionOffering() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMentorEmail() { return mentorEmail; }
    public void setMentorEmail(String mentorEmail) { this.mentorEmail = mentorEmail; }
    public Long getMentorUserId() { return mentorUserId; }
    public void setMentorUserId(Long mentorUserId) { this.mentorUserId = mentorUserId; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRateCurrency() { return rateCurrency; }
    public void setRateCurrency(String rateCurrency) { this.rateCurrency = rateCurrency; }
    public BigDecimal getRateAmount() { return rateAmount; }
    public void setRateAmount(BigDecimal rateAmount) { this.rateAmount = rateAmount; }
    public String getRateDescription() { return rateDescription; }
    public void setRateDescription(String rateDescription) { this.rateDescription = rateDescription; }
    public String getMeetingProvider() { return meetingProvider; }
    public void setMeetingProvider(String meetingProvider) { this.meetingProvider = meetingProvider; }
    public String getMeetingProviderId() { return meetingProviderId; }
    public void setMeetingProviderId(String meetingProviderId) { this.meetingProviderId = meetingProviderId; }
    public String getZoomJoinUrl() { return zoomJoinUrl; }
    public void setZoomJoinUrl(String zoomJoinUrl) { this.zoomJoinUrl = zoomJoinUrl; }
    public String getZoomHostUrl() { return zoomHostUrl; }
    public void setZoomHostUrl(String zoomHostUrl) { this.zoomHostUrl = zoomHostUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getMentorCheckedInAt() { return mentorCheckedInAt; }
    public void setMentorCheckedInAt(LocalDateTime mentorCheckedInAt) { this.mentorCheckedInAt = mentorCheckedInAt; }
}
