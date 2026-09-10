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

    // ADDED for SMS Cost Guard — digest visibility into SMS volume and cost
    @Value("${sms.provider:sns}")
    private String smsProvider;

    @Value("${sms.daily.cap:20}")
    private int smsDailyCap;

    private static final double SMS_COST_PER_MSG = 0.0123; // blended US estimate

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
        int smsYesterday = 0; // ADDED for SMS Cost Guard
        for (UserActivity e : events) {
            active.add(e.getEmail());
            if (UserActivity.SIGNUP.equals(e.getEventType())) {
                signups.append("<li>").append(e.getEmail())
                       .append(" — ").append(e.getCreatedAt().toLocalTime())
                       .append(e.getSourceIp() == null ? "" : " from " + e.getSourceIp())
                       .append("</li>");
            } else if (UserActivity.AI_CALL.equals(e.getEventType())) {
                aiCalls.merge(e.getEmail(), 1, Integer::sum);
            } else if (UserActivity.SMS_SENT.equals(e.getEventType())) { // ADDED for SMS Cost Guard
                smsYesterday++;
            }
        }

        // ADDED for SMS Cost Guard — month-to-date volume via the global count query
        long smsMonth = activityRepository.countByEventTypeAndCreatedAtAfter(
                UserActivity.SMS_SENT, LocalDate.now().withDayOfMonth(1).atStartOfDay());

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
                // ADDED for SMS Cost Guard — yesterday + month-to-date volume with cost estimate
                + "<h3>📱 SMS</h3><p><b>Yesterday:</b> " + smsYesterday
                + " (~$" + String.format("%.2f", smsYesterday * SMS_COST_PER_MSG) + ")"
                + " · <b>Month-to-date:</b> " + smsMonth
                + " (~$" + String.format("%.2f", smsMonth * SMS_COST_PER_MSG) + ")"
                + " · <b>Daily cap:</b> " + smsDailyCap + "</p>"
                + "<p style='color:#888'>SMS sends cost real money — provider: " + smsProvider + ".</p>"
                + (blocked.length() == 0 ? "" : "<h3>🛑 Currently blocked</h3><ul>" + blocked + "</ul>")
                + "<p style='color:#888'>Guardrail and alerts are active; reply to nobody — this is a machine.</p>";

        activityService.alertAdmin("Daily digest " + yesterday
                + " · " + active.size() + " active", html);
        System.out.println("📧 [AdminDigest] sent for " + yesterday + " (" + events.size() + " events)");
    }
}
