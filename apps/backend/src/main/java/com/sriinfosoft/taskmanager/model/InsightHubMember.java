package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Membership of a user in a insightHub. The mentor is also a member row with
 * role MENTOR, so membership queries are uniform. Members are existing
 * Task Manager users, identified by email.
 */
@Entity
@Table(name = "insight_hub_members", indexes = {
    @Index(name = "idx_member_hub", columnList = "insight_hub_id"),
    @Index(name = "idx_member_email", columnList = "member_email")
})
public class InsightHubMember {

    public enum Role { MENTOR, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_hub_id", nullable = false)
    private Long insightHubId;

    @Column(name = "member_email", nullable = false)
    private String memberEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

    public InsightHubMember() {}

    public InsightHubMember(Long insightHubId, String memberEmail, Role role) {
        this.insightHubId = insightHubId;
        this.memberEmail = memberEmail;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInsightHubId() { return insightHubId; }
    public void setInsightHubId(Long insightHubId) { this.insightHubId = insightHubId; }

    public String getMemberEmail() { return memberEmail; }
    public void setMemberEmail(String memberEmail) { this.memberEmail = memberEmail; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
