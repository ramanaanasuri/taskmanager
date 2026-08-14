package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tier 1b: AI daily digest.
 *
 * Design rule: SQL decides, the model phrases. Deterministic filtering
 * selects each user's overdue / due-today / high-priority-this-week tasks;
 * the model's only job is to write two friendly paragraphs about them.
 *
 * Metering: an AI credit is consumed per AI-phrased digest, through the
 * same AiUsageService gate as the interactive endpoints — scheduled AI is
 * not a loophole. Users without credit (free plan, or exhausted) still get
 * the digest, phrased by the plain template at zero model cost. Any model
 * failure also falls back to the template: reliability is a feature.
 *
 * Off by default (ai.digest.enabled=false). For testing, set in .env:
 *   DIGEST_ENABLED=true
 *   DIGEST_CRON="0 0-59/2 * * * *"   (every 2 minutes; remove after testing)
 */
@Service
public class DailyDigestService {

    private static final Logger logger = LoggerFactory.getLogger(DailyDigestService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a");

    @Autowired private TaskRepository taskRepository;
    @Autowired private EmailService emailService;
    @Autowired private AiService aiService;
    @Autowired private AiUsageService aiUsageService;

    @Value("${ai.digest.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${ai.digest.cron:0 0 8 * * *}", zone = "${ai.digest.zone:America/Los_Angeles}")
    public void sendDailyDigests() {
        if (!enabled) {
            return;
        }
        logger.info("📬 [Digest] Daily digest run starting");

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime weekAhead = now.plusDays(7);

        Map<String, List<Task>> byUser = taskRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getCompleted()))
                .filter(t -> t.getDueDate() != null)
                .collect(Collectors.groupingBy(Task::getUserEmail));

        int sent = 0;
        for (Map.Entry<String, List<Task>> entry : byUser.entrySet()) {
            String email = entry.getKey();
            List<Task> tasks = entry.getValue();

            List<Task> overdue = tasks.stream()
                    .filter(t -> t.getDueDate().isBefore(now)).toList();
            List<Task> dueToday = tasks.stream()
                    .filter(t -> !t.getDueDate().isBefore(now)
                              && t.getDueDate().toLocalDate().equals(today)).toList();
            List<Task> highThisWeek = tasks.stream()
                    .filter(t -> t.getDueDate().isAfter(now)
                              && t.getDueDate().isBefore(weekAhead)
                              && !t.getDueDate().toLocalDate().equals(today)
                              && "HIGH".equals(String.valueOf(t.getPriority()))).toList();

            if (overdue.isEmpty() && dueToday.isEmpty() && highThisWeek.isEmpty()) {
                continue; // nothing worth a digest
            }

            String facts = buildFacts(overdue, dueToday, highThisWeek);
            String summary = null;

            // Metering-gated AI phrasing: check -> call -> consume on success only
            if (aiUsageService.hasCredit(email) && aiService.isConfigured()) {
                try {
                    summary = aiService.summarizeTasks(facts);
                    aiUsageService.consume(email);
                    logger.info("📬 [Digest] AI-phrased digest for {}", email);
                } catch (Exception e) {
                    logger.warn("📬 [Digest] AI failed for {} ({}), using template", email, e.getMessage());
                }
            }
            if (summary == null) {
                summary = templateSummary(overdue, dueToday, highThisWeek);
                logger.info("📬 [Digest] Templated digest for {}", email);
            }

            try {
                emailService.sendDigestEmail(email, "🌅 Your tasks today", buildHtml(summary, facts));
                sent++;
            } catch (Exception e) {
                logger.error("📬 [Digest] Email send failed for {}: {}", email, e.getMessage());
            }
        }
        logger.info("📬 [Digest] Run complete — {} digest(s) sent", sent);
    }

    private String buildFacts(List<Task> overdue, List<Task> dueToday, List<Task> highWeek) {
        StringBuilder sb = new StringBuilder();
        appendGroup(sb, "OVERDUE", overdue);
        appendGroup(sb, "DUE TODAY", dueToday);
        appendGroup(sb, "HIGH PRIORITY THIS WEEK", highWeek);
        return sb.toString();
    }

    private void appendGroup(StringBuilder sb, String label, List<Task> tasks) {
        sb.append(label).append(" (").append(tasks.size()).append("):\n");
        for (Task t : tasks) {
            sb.append("- ").append(t.getTitle())
              .append(" [").append(t.getPriority()).append("] due ")
              .append(t.getDueDate().format(FMT)).append('\n');
        }
        sb.append('\n');
    }

    /** Zero-cost fallback used for free-tier users and on any AI failure. */
    private String templateSummary(List<Task> overdue, List<Task> dueToday, List<Task> highWeek) {
        StringBuilder sb = new StringBuilder();
        if (!overdue.isEmpty()) {
            sb.append("You have ").append(overdue.size())
              .append(overdue.size() == 1 ? " overdue task" : " overdue tasks")
              .append(" that could use attention. ");
        }
        if (!dueToday.isEmpty()) {
            sb.append(dueToday.size() == 1 ? "One task is" : dueToday.size() + " tasks are")
              .append(" due today. ");
        }
        if (!highWeek.isEmpty()) {
            sb.append("Looking ahead, ").append(highWeek.size())
              .append(" high-priority ").append(highWeek.size() == 1 ? "item is" : "items are")
              .append(" coming up this week.");
        }
        return sb.toString().trim();
    }

    private String buildHtml(String summary, String facts) {
        String factsHtml = facts.replace("&", "&amp;").replace("<", "&lt;")
                                .replace(">", "&gt;").replace("\n", "<br>");
        String summaryHtml = summary.replace("&", "&amp;").replace("<", "&lt;")
                                    .replace(">", "&gt;").replace("\n", "<br>");
        return "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:0 auto\">"
             + "<h2 style=\"color:#5b21b6\">🌅 Your tasks today</h2>"
             + "<p style=\"font-size:15px;line-height:1.6\">" + summaryHtml + "</p>"
             + "<hr style=\"border:none;border-top:1px solid #e5e7eb\">"
             + "<p style=\"font-size:12px;color:#6b7280;line-height:1.7\">" + factsHtml + "</p>"
             + "</div>";
    }
}
