package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The admin's morning answer to "who is using my app?": yesterday's signups,
 * active users, per-user AI consumption, and any guardrail blocks — one email.
 * Pure read-model over user_activity + users; sending failures only log.
 */
@Service
public class AdminDigestService {

    @Autowired private UserActivityRepository activityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivityService activityService;

    @Value("${admin.alert.email:}")
    private String adminAlertEmail;

    @Scheduled(cron = "${admin.digest.cron:0 0 7 * * *}", zone = "${ai.digest.zone:America/Los_Angeles}")
    public void sendDailyAdminDigest() {
        if (adminAlertEmail == null || adminAlertEmail.isBlank()) return;
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime from = yesterday.atStartOfDay();
        LocalDateTime to = yesterday.plusDays(1).atStartOfDay();
        List<UserActivity> events = activityRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);

        StringBuilder signups = new StringBuilder();
        TreeSet<String> active = new TreeSet<>();
        Map<String, Integer> aiCalls = new LinkedHashMap<>();
        for (UserActivity e : events) {
            active.add(e.getEmail());
            if (UserActivity.SIGNUP.equals(e.getEventType())) {
                signups.append("<li>").append(e.getEmail())
                       .append(" — ").append(e.getCreatedAt().toLocalTime())
                       .append(e.getSourceIp() == null ? "" : " from " + e.getSourceIp())
                       .append("</li>");
            } else if (UserActivity.AI_CALL.equals(e.getEventType())) {
                aiCalls.merge(e.getEmail(), 1, Integer::sum);
            }
        }

        StringBuilder aiRows = new StringBuilder();
        for (Map.Entry<String, Integer> en : aiCalls.entrySet()) {
            User u = userRepository.findByEmail(en.getKey()).orElse(null);
            aiRows.append("<tr><td>").append(en.getKey())
                  .append("</td><td>").append(en.getValue())
                  .append("</td><td>").append(u == null ? "?" : u.getSubscriptionPlan())
                  .append("</td><td>").append(u == null ? "?" :
                          u.getAiRequestsUsed() + "/" + u.getAiRequestsLimit())
                  .append("</td></tr>");
        }

        StringBuilder blocked = new StringBuilder();
        for (User u : userRepository.findAll()) {
            if (Boolean.TRUE.equals(u.getAiBlocked())) {
                blocked.append("<li>").append(u.getEmail())
                       .append(" — ").append(u.getBlockedReason()).append("</li>");
            }
        }

        String html = "<h2>📊 Task Manager Pro — daily admin digest (" + yesterday + ")</h2>"
                + "<p><b>Active users:</b> " + active.size()
                + " · <b>New signups:</b> " + (signups.length() == 0 ? "0" : "") + "</p>"
                + (signups.length() == 0 ? "" : "<h3>🆕 Signups</h3><ul>" + signups + "</ul>")
                + (aiRows.length() == 0 ? "<p>No AI calls yesterday.</p>"
                        : "<h3>🤖 AI consumption</h3>"
                        + "<table border='1' cellpadding='6' cellspacing='0'>"
                        + "<tr><th>User</th><th>Calls yesterday</th><th>Plan</th><th>Month used/limit</th></tr>"
                        + aiRows + "</table>")
                + (blocked.length() == 0 ? "" : "<h3>🛑 Currently blocked</h3><ul>" + blocked + "</ul>")
                + "<p style='color:#888'>Guardrail and alerts are active; reply to nobody — this is a machine.</p>";

        activityService.alertAdmin("Daily digest " + yesterday
                + " · " + active.size() + " active", html);
        System.out.println("📧 [AdminDigest] sent for " + yesterday + " (" + events.size() + " events)");
    }
}
