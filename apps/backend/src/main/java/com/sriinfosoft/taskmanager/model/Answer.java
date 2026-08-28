package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An answer to a Question. draftText holds what the agent produced (if any);
 * finalText holds what the mentor approved/wrote and what the member receives.
 * sources lists the KB documents the draft cited (newline-separated
 * "Title|URL" rows), so the mentor can see the grounding at review time.
 */
@Entity
@Table(name = "insight_hub_answers", indexes = {
    @Index(name = "idx_a_question", columnList = "question_id")
})
public class Answer {

    public enum Origin { AGENT, MENTOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "draft_text", columnDefinition = "TEXT")
    private String draftText;

    @Column(name = "final_text", columnDefinition = "TEXT")
    private String finalText;

    @Column(name = "sources", columnDefinition = "TEXT")
    private String sources;   // newline-separated "Title|URL" rows

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin = Origin.AGENT;

    @Column(name = "approved_by_email")
    private String approvedByEmail;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Answer() {}

    public Answer(Long questionId, Origin origin) {
        this.questionId = questionId;
        this.origin = origin;
    }

    /** Sources as a list, decoded from the stored newline form. */
    public List<String> getSourcesList() {
        List<String> out = new ArrayList<>();
        if (sources != null && !sources.isBlank()) {
            for (String line : sources.split("\n")) {
                if (!line.isBlank()) out.add(line.trim());
            }
        }
        return out;
    }

    public void setSourcesList(List<String> list) {
        this.sources = (list == null || list.isEmpty()) ? null : String.join("\n", list);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getDraftText() { return draftText; }
    public void setDraftText(String draftText) { this.draftText = draftText; }

    public String getFinalText() { return finalText; }
    public void setFinalText(String finalText) { this.finalText = finalText; }

    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }

    public Origin getOrigin() { return origin; }
    public void setOrigin(Origin origin) { this.origin = origin; }

    public String getApprovedByEmail() { return approvedByEmail; }
    public void setApprovedByEmail(String approvedByEmail) { this.approvedByEmail = approvedByEmail; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
