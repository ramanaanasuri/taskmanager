package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.Answer;
import com.sriinfosoft.taskmanager.model.InsightHub;
import com.sriinfosoft.taskmanager.model.InsightHubMember;
import com.sriinfosoft.taskmanager.model.Question;
import com.sriinfosoft.taskmanager.repository.AnswerRepository;
import com.sriinfosoft.taskmanager.repository.InsightHubMemberRepository;
import com.sriinfosoft.taskmanager.repository.InsightHubRepository;
import com.sriinfosoft.taskmanager.repository.QuestionRepository;
import com.sriinfosoft.taskmanager.repository.SkillRepository;
import com.sriinfosoft.taskmanager.model.Skill;
import com.sriinfosoft.taskmanager.service.authz.AuthorizationService;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import com.sriinfosoft.taskmanager.service.KbRetrievalService;
import com.sriinfosoft.taskmanager.service.MentorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Learning InsightHub REST surface. JWT-protected by SecurityConfig's
 * anyRequest().authenticated() — no security change needed. Ownership is
 * enforced here the same way the rest of the app does it: resolve the caller's
 * email from the security context and check insightHub membership/role.
 */
@RestController
@RequestMapping("/api")
public class MentorController {

    @Autowired private MentorService mentorService;
    @Autowired private KbRetrievalService kb;
    @Autowired private InsightHubRepository insightHubRepo;
    @Autowired private InsightHubMemberRepository memberRepo;
    @Autowired private QuestionRepository questionRepo;
    @Autowired private AnswerRepository answerRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SkillRepository skillRepo;
    @Autowired private AuthorizationService authz;   // per-topic review authorization

    // =============================================================== insight hubs

    @PostMapping("/insight-hubs")
    public ResponseEntity<?> createInsightHub(@RequestBody Map<String, String> body) {
        String email = currentEmail();
        if (email == null) return unauth();
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return ResponseEntity.badRequest().body(err("name is required"));

        InsightHub insightHub = insightHubRepo.save(new InsightHub(email, name));
        memberRepo.save(new InsightHubMember(insightHub.getId(), email, InsightHubMember.Role.MENTOR));
        // parity: the creator can review their hub's questions across active topics
        for (Skill s : skillRepo.findByActiveTrueOrderByName()) authz.grant(email, "hubadmin", s.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(insightHubJson(insightHub, InsightHubMember.Role.MENTOR));
    }

    /** InsightHubs the caller belongs to (as mentor or member). */
    @GetMapping("/insight-hubs")
    public ResponseEntity<?> listInsightHubs() {
        String email = currentEmail();
        if (email == null) return unauth();
        List<Map<String, Object>> out = new ArrayList<>();
        for (InsightHubMember m : memberRepo.findByMemberEmail(email)) {
            insightHubRepo.findById(m.getInsightHubId())
                    .ifPresent(c -> out.add(insightHubJson(c, m.getRole())));
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/insight-hubs/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String email = currentEmail();
        if (email == null) return unauth();
        InsightHub insightHub = insightHubRepo.findById(id).orElse(null);
        if (insightHub == null) return notFound("insightHub");
        if (!insightHub.getMentorEmail().equals(email)) return forbidden("only the mentor can add members");

        String memberEmail = body.getOrDefault("email", "").trim().toLowerCase();
        if (memberEmail.isEmpty()) return ResponseEntity.badRequest().body(err("email is required"));
        if (!userRepo.existsByEmail(memberEmail)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(err("No Task Manager user with that email. They must sign in once first."));
        }
        if (memberRepo.existsByInsightHubIdAndMemberEmail(id, memberEmail)) {
            return ResponseEntity.ok(Map.of("status", "already a member"));
        }
        memberRepo.save(new InsightHubMember(id, memberEmail, InsightHubMember.Role.MEMBER));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "added", "email", memberEmail));
    }

    @GetMapping("/insight-hubs/{id}/members")
    public ResponseEntity<?> listMembers(@PathVariable Long id) {
        String email = currentEmail();
        if (email == null) return unauth();
        if (membership(id, email).isEmpty()) return forbidden("not a member of this insightHub");
        List<Map<String, Object>> out = new ArrayList<>();
        for (InsightHubMember m : memberRepo.findByInsightHubId(id)) {
            out.add(Map.of("email", m.getMemberEmail(), "role", m.getRole().name()));
        }
        return ResponseEntity.ok(out);
    }

    // ============================================================= questions

    /** A member (or the mentor) submits a question; intake drafts or escalates. */
    @GetMapping("/insight-hubs/topics")
    public ResponseEntity<?> topics() {
        String email = currentEmail();
        if (email == null) return unauth();
        List<Map<String, Object>> topics = new ArrayList<>();
        for (Skill s : skillRepo.findByActiveTrueAndAskableTrueOrderByName()) {
            Map<String, Object> t = new HashMap<>();
            t.put("id", s.getId()); t.put("name", s.getName()); t.put("slug", s.getSlug());
            topics.add(t);
        }
        InsightHub def = insightHubRepo.findAll().stream()
                .min(Comparator.comparing(InsightHub::getId)).orElse(null);
        Map<String, Object> out = new HashMap<>();
        out.put("topics", topics);
        out.put("reviewable", authz.topicsFor(email, "review"));
        out.put("defaultHubId", def != null ? def.getId() : null);
        out.put("defaultHubName", def != null ? def.getName() : null);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/insight-hubs/{id}/questions")
    public ResponseEntity<?> ask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String email = currentEmail();
        if (email == null) return unauth();
        if (membership(id, email).isEmpty()) {
            // any authenticated user may ask into the DEFAULT hub; auto-enroll as a learner
            if (id.equals(defaultHubId())) {
                memberRepo.save(new InsightHubMember(id, email, InsightHubMember.Role.MEMBER));
            } else {
                return forbidden("not a member of this insightHub");
            }
        }

        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) return ResponseEntity.badRequest().body(err("text is required"));
        if (text.length() > 2000) text = text.substring(0, 2000);

        Question q = new Question(id, email, text);
        q.setSkillId(resolveSkillId(body.get("skillId")));   // topic (defaults to the default active topic)
        Question saved = questionRepo.save(q);
        Question result = mentorService.intake(saved);   // synchronous: draft or escalate
        return ResponseEntity.status(HttpStatus.CREATED).body(questionJson(result, email, canReview(result, email)));
    }

    /** Mentor sees all questions in the insightHub; a member sees only their own. */
    @GetMapping("/insight-hubs/{id}/questions")
    public ResponseEntity<?> listQuestions(@PathVariable Long id) {
        String email = currentEmail();
        if (email == null) return unauth();
        Optional<InsightHubMember> m = membership(id, email);
        boolean isDefault = id.equals(defaultHubId());
        if (m.isEmpty() && !isDefault) return forbidden("not a member of this insightHub");
        boolean mentor = m.map(x -> x.getRole() == InsightHubMember.Role.MENTOR).orElse(false);

        List<Question> qs = mentor
                ? questionRepo.findByInsightHubIdOrderByCreatedAtDesc(id)
                : questionRepo.findByInsightHubIdAndAskedByEmailOrderByCreatedAtDesc(id, email);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Question q : qs) out.add(questionJson(q, email, mentor));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/questions/{qid}")
    public ResponseEntity<?> getQuestion(@PathVariable Long qid) {
        String email = currentEmail();
        if (email == null) return unauth();
        Question q = questionRepo.findById(qid).orElse(null);
        if (q == null) return notFound("question");
        Optional<InsightHubMember> m = membership(q.getInsightHubId(), email);
        if (m.isEmpty()) return forbidden("not a member of this insightHub");
        boolean mentor = m.get().getRole() == InsightHubMember.Role.MENTOR;
        if (!mentor && !q.getAskedByEmail().equals(email)) return forbidden("not your question");
        return ResponseEntity.ok(questionJson(q, email, mentor));
    }

    @PostMapping("/questions/{qid}/approve")
    public ResponseEntity<?> approve(@PathVariable Long qid, @RequestBody(required = false) Map<String, String> body) {
        return mentorAction(qid, (q, email) -> {
            if (q.getStatus() != Question.Status.DRAFTED)
                return ResponseEntity.badRequest().body(err("question is not awaiting approval"));
            String edited = body == null ? null : body.get("text");
            return ResponseEntity.ok(questionJson(mentorService.approve(q, email, edited), email, true));
        });
    }

    @PostMapping("/questions/{qid}/reject")
    public ResponseEntity<?> reject(@PathVariable Long qid) {
        return mentorAction(qid, (q, email) ->
                ResponseEntity.ok(questionJson(mentorService.reject(q), email, true)));
    }

    /** The NEEDS_MENTOR path: the mentor writes the answer directly. */
    @PostMapping("/questions/{qid}/answer")
    public ResponseEntity<?> answer(@PathVariable Long qid, @RequestBody Map<String, String> body) {
        return mentorAction(qid, (q, email) -> {
            String text = body == null ? "" : body.getOrDefault("text", "").trim();
            if (text.isEmpty()) return ResponseEntity.badRequest().body(err("text is required"));
            return ResponseEntity.ok(questionJson(mentorService.answerDirectly(q, email, text), email, true));
        });
    }

    /** Mentor's "refresh KB": drop the cache so the next question re-fetches. */
    @PostMapping("/insight-hubs/{id}/refresh-kb")
    public ResponseEntity<?> refreshKb(@PathVariable Long id) {
        String email = currentEmail();
        if (email == null) return unauth();
        if (!isMentor(id, email)) return forbidden("only the mentor can refresh the KB");
        kb.refresh();
        return ResponseEntity.ok(Map.of("status", "kb cache cleared", "urls", kb.configuredUrls()));
    }

    // =============================================================== helpers

    private interface MentorOp { ResponseEntity<?> apply(Question q, String email); }

    /** Shared guard for mentor-only actions on a question. */
    private ResponseEntity<?> mentorAction(Long qid, MentorOp op) {
        String email = currentEmail();
        if (email == null) return unauth();
        Question q = questionRepo.findById(qid).orElse(null);
        if (q == null) return notFound("question");
        if (!canReview(q, email)) return forbidden("not authorized to review this topic");
        return op.apply(q, email);
    }

    private Optional<InsightHubMember> membership(Long insightHubId, String email) {
        return memberRepo.findByInsightHubIdAndMemberEmail(insightHubId, email);
    }

    private boolean isMentor(Long insightHubId, String email) {
        return membership(insightHubId, email)
                .map(m -> m.getRole() == InsightHubMember.Role.MENTOR).orElse(false);
    }

    /** Per-topic review gate: may this user review THIS question's topic? */
    private boolean canReview(Question q, String email) {
        String topic = topicSlug(q);
        return topic != null && authz.can(email, topic, "review");
    }

    /** Resolve a question's topic slug (from its skill), or the default active topic. */
    private String topicSlug(Question q) {
        if (q.getSkillId() != null) {
            return skillRepo.findById(q.getSkillId()).map(Skill::getSlug).orElse(defaultTopicSlug());
        }
        return defaultTopicSlug();
    }

    /** Resolve an incoming skillId (string), defaulting to the default active topic. */
    private Long resolveSkillId(String raw) {
        if (raw != null && !raw.isBlank()) {
            try { Long id = Long.valueOf(raw.trim());
                  if (skillRepo.findById(id).map(Skill::isActive).orElse(false)) return id; }
            catch (NumberFormatException ignored) {}
        }
        return skillRepo.findBySlug("investing").map(Skill::getId)
                .orElseGet(() -> skillRepo.findByActiveTrueOrderByName().stream().findFirst().map(Skill::getId).orElse(null));
    }

    /** The canonical Knowledge Circle hub (earliest-created) that clients ask into. */
    private Long defaultHubId() {
        return insightHubRepo.findAll().stream()
                .min(Comparator.comparing(InsightHub::getId)).map(InsightHub::getId).orElse(null);
    }

    private String defaultTopicSlug() {
        return skillRepo.findBySlug("investing").map(Skill::getSlug)
                .orElseGet(() -> skillRepo.findByActiveTrueOrderByName().stream().findFirst().map(Skill::getSlug).orElse(null));
    }

    private Map<String, Object> insightHubJson(InsightHub c, InsightHubMember.Role role) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("mentorEmail", c.getMentorEmail());
        m.put("role", role.name());
        return m;
    }

    /**
     * Question JSON tuned to the viewer: the mentor sees the draft and its
     * sources; a member sees the final answer only once it is DELIVERED.
     */
    private Map<String, Object> questionJson(Question q, String viewerEmail, boolean mentor) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", q.getId());
        m.put("insightHubId", q.getInsightHubId());
        m.put("skillId", q.getSkillId());
        m.put("askedByEmail", q.getAskedByEmail());
        m.put("text", q.getText());
        m.put("status", q.getStatus().name());
        m.put("createdAt", q.getCreatedAt());

        Answer a = answerRepo.findByQuestionId(q.getId()).orElse(null);
        if (a != null) {
            if (mentor) {
                m.put("draftText", a.getDraftText());
                m.put("finalText", a.getFinalText());
                m.put("sources", a.getSourcesList());
                m.put("origin", a.getOrigin().name());
            } else if (q.getStatus() == Question.Status.DELIVERED) {
                m.put("finalText", a.getFinalText());
                m.put("sources", a.getSourcesList());
            }
        }
        return m;
    }

    private String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof String email && email.contains("@")) return email;
        return null;
    }

    private static Map<String, Object> err(String msg) { return Map.of("error", msg); }
    private static ResponseEntity<?> unauth() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err("Not authenticated"));
    }
    private static ResponseEntity<?> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err(msg));
    }
    private static ResponseEntity<?> notFound(String what) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err("no such " + what));
    }
}
