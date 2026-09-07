package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Writes the immutable activity trail and fires the instant admin alerts.
 * Recording must never break the user-facing flow: every write is
 * try/caught — losing one log row beats failing a login or an AI call.
 */
@Service
public class ActivityService {

    @Autowired private UserActivityRepository activityRepository;
    @Autowired private EmailService emailService;

    @Value("${admin.alert.email:}")
    private String adminAlertEmail;

    /** Record an event; on signup, alert the admin immediately. */
    public void record(String email, String eventType, String detail, String sourceIp) {
        try {
            activityRepository.save(new UserActivity(email, eventType, detail, sourceIp));
        } catch (Exception e) {
            System.out.println("⚠️ [Activity] failed to record " + eventType + " for " + email + ": " + e.getMessage());
        }
        if (UserActivity.SIGNUP.equals(eventType)) {
            alertAdmin("New signup: " + email,
                    "<h3>🆕 New Task Manager Pro signup</h3>"
                    + "<p><b>User:</b> " + email + "<br>"
                    + "<b>From IP:</b> " + (sourceIp == null ? "unknown" : sourceIp) + "</p>"
                    + "<p>They start on the free plan. The daily digest will show their consumption; "
                    + "the AI guardrail applies automatically.</p>");
        }
    }

    /** Best-effort admin email; disabled when admin.alert.email is empty. */
    public void alertAdmin(String subject, String html) {
        if (adminAlertEmail == null || adminAlertEmail.isBlank()) return;
        try {
            emailService.sendDigestEmail(adminAlertEmail, "[TM Admin] " + subject, html);
        } catch (Exception e) {
            System.out.println("⚠️ [Activity] admin alert failed: " + e.getMessage());
        }
    }
}
