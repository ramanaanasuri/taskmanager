package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.service.EmailService;
import com.sriinfosoft.taskmanager.service.NotificationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test controller for notification features
 * 
 * ADDED for Email Integration - Testing endpoints for email notifications
 * 
 * WARNING: REMOVE IN PRODUCTION or add proper authentication
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "${cors.allowed-origins}", allowCredentials = "true")
public class NotificationTestController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationTestController.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationScheduler notificationScheduler;

    /**
     * Test endpoint to send a test email
     * 
     * ADDED for Email Integration
     * 
     * Usage: GET /api/test/email?to=user@example.com
     */
    @GetMapping("/email")
    public ResponseEntity<?> sendTestEmail(@RequestParam String to) {
        logger.info("🧪 TEST ENDPOINT: Sending test email to {}", to);
        logger.debug("DEBUG: Test email endpoint called"); //ADDED for Email Integration
        
        try {
            emailService.sendTestEmail(to);
            logger.info("✅ TEST: Email sent successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test email sent to " + to
            ));
        } catch (Exception e) {
            logger.error("❌ TEST: Email sending failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Test endpoint to manually trigger notification scheduler
     * 
     * ADDED for Email Integration
     * 
     * Usage: GET /api/test/scheduler
     */
    @GetMapping("/scheduler")
    public ResponseEntity<?> triggerScheduler() {
        logger.info("🧪 TEST ENDPOINT: Manually triggering notification scheduler");
        logger.debug("DEBUG: Manual scheduler trigger called"); //ADDED for Email Integration
        
        try {
            notificationScheduler.triggerManualCheck();
            logger.info("✅ TEST: Scheduler triggered successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Notification scheduler triggered manually"
            ));
        } catch (Exception e) {
            logger.error("❌ TEST: Scheduler trigger failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}