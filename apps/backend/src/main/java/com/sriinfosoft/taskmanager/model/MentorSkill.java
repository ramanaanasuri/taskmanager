package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;

/** Join row: which skills a mentor teaches. Unique per (mentor, skill). */
@Entity
@Table(name = "mentor_skill",
    uniqueConstraints = @UniqueConstraint(name = "uk_mentor_skill",
        columnNames = {"mentor_profile_id", "skill_id"}),
    indexes = @Index(name = "idx_ms_skill", columnList = "skill_id"))
public class MentorSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_profile_id", nullable = false)
    private Long mentorProfileId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    public MentorSkill() {}
    public MentorSkill(Long mentorProfileId, Long skillId) {
        this.mentorProfileId = mentorProfileId; this.skillId = skillId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMentorProfileId() { return mentorProfileId; }
    public void setMentorProfileId(Long mentorProfileId) { this.mentorProfileId = mentorProfileId; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
}
