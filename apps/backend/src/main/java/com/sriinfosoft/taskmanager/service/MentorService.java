package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.Answer;
import com.sriinfosoft.taskmanager.model.InsightHub;
import com.sriinfosoft.taskmanager.model.Question;
import com.sriinfosoft.taskmanager.repository.AnswerRepository;
import com.sriinfosoft.taskmanager.repository.InsightHubRepository;
import com.sriinfosoft.taskmanager.repository.QuestionRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Learning InsightHub intake loop, as a deterministic pipeline with a human
 * gate. A member's question is retrieved against the mentor's KB and either
 * drafted (grounded) or escalated to the mentor (not grounded / AI down /
 * mentor out of AI credit). The mentor approves, edits, rejects, or writes the
 * answer directly; on approval it is delivered to the member by email.
 *
 * The agentic pattern here is supervisor-routing + grounded-RAG + human-in-the-
 * loop. Every guardrail is in code, not in a prompt:
 *   - APPROVAL GATE: nothing reaches a member without an approve/answer action.
 *   - ESCALATE, DON'T FABRICATE: weak grounding (or the model's own
 *     INSUFFICIENT_GROUNDING) routes to the mentor; it never guesses.
 *   - SOURCE CITATION: every agent draft records the passages it used.
 *   - METERING: an agent draft consumes one of the MENTOR's AI credits; when the
 *     mentor is out of credit the question escalates (mentor answers manually) —
 *     members never see billing.
 */
@Service
public class MentorService {

    private static final Logger logger = LoggerFactory.getLogger(MentorService.class);

    @Value("${kb.grounding.threshold}")
    private double groundingThreshold;

    @Autowired private KbRetrievalService kb;
    @Autowired private AiService aiService;
    @Autowired private AiUsageService aiUsageService;
    @Autowired private EmailService emailService;
    @Autowired private InsightHubRepository insightHubRepo;
    @Autowired private QuestionRepository questionRepo;
    @Autowired private AnswerRepository answerRepo;

    /**
     * Handle a freshly submitted question: retrieve, then draft or escalate.
     * Returns the question in its resulting status (DRAFTED or NEEDS_MENTOR).
     */
    public Question intake(Question q) {
        InsightHub insightHub = insightHubRepo.findById(q.getInsightHubId()).orElse(null);
        if (insightHub == null) {                 // defensive; controller checks first
            return escalate(q, "hub not found");
        }
        String mentorEmail = insightHub.getMentorEmail();

        // Metering: an agent draft spends one of the mentor's credits. No credit
        // -> escalate to the mentor rather than charge or fabricate.
        if (!aiUsageService.hasCredit(mentorEmail)) {
            return escalate(q, "mentor out of AI credit");
        }

        KbRetrievalService.RetrievalResult r = kb.search(q.getText(), 4);
        if (logger.isDebugEnabled()) {
            logger.debug("[Mentor] retrieval for question {}: empty={} bestScore={} threshold={} passages={}",
                q.getId(), r.isEmpty(), r.isEmpty() ? "n/a" : r.bestScore(), groundingThreshold, r.passages().size());
        }
        if (r.isEmpty() || r.bestScore() < groundingThreshold) {
            return escalate(q, "weak grounding (bestScore below threshold)");
        }

        String passagesBlock = buildPassagesBlock(r.passages());
        String draft;
        try {
            draft = aiService.draftAnswer(q.getText(), passagesBlock);
        } catch (AiService.AiUnavailableException e) {
            return escalate(q, "AI provider unavailable");
        }

        if (draft == null || draft.isBlank() || draft.contains("INSUFFICIENT_GROUNDING")) {
            return escalate(q, "model declined (insufficient grounding)");
        }

        // Success: record the draft + its sources, consume one mentor credit.
        aiUsageService.consume(mentorEmail);
        Answer a = answerRepo.findByQuestionId(q.getId()).orElse(new Answer(q.getId(), Answer.Origin.AGENT));
        a.setOrigin(Answer.Origin.AGENT);
        a.setDraftText(draft);
        a.setSourcesList(sourceLabels(r.passages()));
        answerRepo.save(a);

        q.setStatus(Question.Status.DRAFTED);
        return questionRepo.save(q);
    }

    /** Mentor approves the current draft (optionally edited) and it is delivered. */
    public Question approve(Question q, String mentorEmail, String editedText) {
        Answer a = answerRepo.findByQuestionId(q.getId())
                .orElseThrow(() -> new IllegalStateException("no answer to approve"));
        String finalText = (editedText != null && !editedText.isBlank())
                ? editedText.trim() : a.getDraftText();
        a.setFinalText(finalText);
        if (editedText != null && !editedText.isBlank()) a.setOrigin(Answer.Origin.MENTOR);
        a.setApprovedByEmail(mentorEmail);
        a.setApprovedAt(LocalDateTime.now());
        answerRepo.save(a);
        return deliver(q, a);
    }

    /** Mentor writes the answer directly (the NEEDS_MENTOR path) and it is delivered. */
    public Question answerDirectly(Question q, String mentorEmail, String text) {
        Answer a = answerRepo.findByQuestionId(q.getId()).orElse(new Answer(q.getId(), Answer.Origin.MENTOR));
        a.setOrigin(Answer.Origin.MENTOR);
        a.setFinalText(text.trim());
        a.setApprovedByEmail(mentorEmail);
        a.setApprovedAt(LocalDateTime.now());
        answerRepo.save(a);
        return deliver(q, a);
    }

    /** Mentor rejects the draft; nothing is delivered. */
    public Question reject(Question q) {
        q.setStatus(Question.Status.REJECTED);
        return questionRepo.save(q);
    }

    // ------------------------------------------------------------- internals

    private Question escalate(Question q, String reason) {
        logger.info("[Mentor] escalating question {} in hub {}: {}", q.getId(), q.getInsightHubId(), reason);
        q.setStatus(Question.Status.NEEDS_MENTOR);
        return questionRepo.save(q);
    }

    private Question deliver(Question q, Answer a) {
        try {
            String html = "<p>" + escapeHtml(a.getFinalText()).replace("\n", "<br>") + "</p>"
                    + "<hr><p style=\"color:#667\">You asked: " + escapeHtml(q.getText()) + "</p>";
            emailService.sendDigestEmail(q.getAskedByEmail(), subjectFor(q), html);
        } catch (MessagingException e) {
            // Delivery failure shouldn't lose the approval; log and still mark delivered
            // state is NOT set so a retry path could resend. Here we surface it.
            logger.warn("[Mentor] email delivery failed for question {}: {}", q.getId(), e.getMessage());
            q.setStatus(Question.Status.APPROVED);   // approved but not yet delivered
            return questionRepo.save(q);
        }
        q.setStatus(Question.Status.DELIVERED);
        return questionRepo.save(q);
    }

    /**
     * One subject per question so mail clients don't thread every answer
     * under a single "Answer to your question" conversation.
     */
    private String subjectFor(Question q) {
        String text = q.getText() == null ? "" : q.getText().trim().replaceAll("\\s+", " ");
        if (text.length() > 60) {
            int cut = text.lastIndexOf(' ', 60);
            text = text.substring(0, cut > 30 ? cut : 60) + "\u2026";
        }
        return "InsightHub #" + q.getId() + ": " + text;
    }

    private String buildPassagesBlock(List<KbRetrievalService.ScoredPassage> passages) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (KbRetrievalService.ScoredPassage p : passages) {
            sb.append("[").append(i++).append("] ").append(p.sourceTitle()).append("\n")
              .append(p.text()).append("\n\n");
        }
        return sb.toString();
    }

    private List<String> sourceLabels(List<KbRetrievalService.ScoredPassage> passages) {
        List<String> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (KbRetrievalService.ScoredPassage p : passages) {
            String key = p.sourceUrl();
            if (!seen.contains(key)) {          // one row per distinct source
                seen.add(key);
                out.add(p.sourceTitle() + "|" + p.sourceUrl());
            }
        }
        return out;
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
