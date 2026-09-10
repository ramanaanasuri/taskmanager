package com.sriinfosoft.taskmanager.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending SMS notifications via AWS SNS
 * 
 * ADDED for SMS Integration - New service class following EmailService pattern
 */
@Service
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    //ADDED for SMS Integration - AWS SNS configuration from environment variables
    @Value("${aws.sns.region}")
    private String awsRegion;

    @Value("${aws.sns.access-key}")
    private String awsAccessKey;

    @Value("${aws.sns.secret-key}")
    private String awsSecretKey;

    // ADDED for SMS provider switch: "sns" (default) or "twilio" (trial-friendly test rail)
    @Value("${sms.provider:sns}")
    private String smsProvider;

    @Value("${twilio.account-sid:}")
    private String twilioSid;

    @Value("${twilio.auth-token:}")
    private String twilioToken;

    @Value("${twilio.from-number:}")
    private String twilioFrom;

    // ADDED for Twilio trial — when set (e.g. sms_appointment_reminders), sent as the
    // Body instead of the real message: trial accounts only accept template names.
    // Leave empty after upgrading to send real task text.
    @Value("${twilio.trial-template:}")
    private String twilioTrialTemplate;

    @Value("${frontend.url}")
    private String frontendUrl;

    // ADDED for SMS Cost Guard — global daily cap (0 disables sending entirely)
    @Value("${sms.daily.cap:20}")
    private int smsDailyCap;

    @Autowired
    private UserActivityRepository userActivityRepository;

    @Autowired
    private ActivityService activityService;

    // ADDED for SMS Cost Guard — remembers which day the cap alert already went out
    private LocalDate capAlertDate;

    private AmazonSNS snsClient;

    /**
     * Initialize AWS SNS client after bean construction
     * Similar to PushNotificationService.init()
     * 
     * ADDED for SMS Integration - PostConstruct initialization
     */
    @PostConstruct
    public void init() {
        logger.info("🔧 Initializing SMS Notification Service (provider: {})...", smsProvider);
        if ("twilio".equalsIgnoreCase(smsProvider)) {
            logger.info("✅ SMS provider is Twilio — SNS client not built.");
            return;
        }
        logger.debug("DEBUG: AWS SNS Region: {}", awsRegion); //ADDED for SMS Integration

        try {
            // Create AWS credentials
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey);
            logger.debug("DEBUG: AWS credentials created"); //ADDED for SMS Integration

            // Build SNS client
            this.snsClient = AmazonSNSClientBuilder.standard()
                    .withRegion(awsRegion)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .build();

            logger.info("✅ SMS Notification Service initialized successfully for region: {}", awsRegion);

        } catch (Exception e) {
            logger.error("❌ Failed to initialize SMS Notification Service: {}", e.getMessage(), e);
            throw new RuntimeException("SMS service initialization failed", e);
        }
    }

    /**
     * Send SMS notification for a due task
     * Main method called by NotificationScheduler
     * 
     * ADDED for SMS Integration - Main SMS sending method
     * 
     * @param task The task that is due
     * @param phoneNumber Phone number to send to (E.164 format: +919876543210)
     * @throws Exception if SMS sending fails
     */
    public void sendTaskDueSmsNotification(Task task, String phoneNumber) throws Exception {
        logger.info("📱 Preparing SMS notification for task {} to {}", task.getId(), maskPhoneNumber(phoneNumber));
        logger.debug("DEBUG: Phone number: {}", phoneNumber); //ADDED for SMS Integration
        logger.debug("DEBUG: Task priority: {}", task.getPriority()); //ADDED for SMS Integration

        // Validate phone number format (E.164: +[country code][number])
        if (!isValidPhoneNumber(phoneNumber)) {
            logger.error("❌ Invalid phone number format: {}. Must be E.164 format (e.g., +919876543210)", phoneNumber);
            throw new IllegalArgumentException("Invalid phone number format. Must be E.164 format (e.g., +919876543210)");
        }

        // Build SMS message content
        String message = buildSmsMessage(task);
        logger.debug("DEBUG: SMS message length: {} characters", message.length()); //ADDED for SMS Integration

        try {
            // Send SMS via AWS SNS
            sendSms(phoneNumber, message, task.getUserEmail()); // ADDED for SMS Cost Guard — owner email for the activity trail
            logger.info("✅ SMS sent successfully to {}", maskPhoneNumber(phoneNumber));

        } catch (Exception e) {
            logger.error("❌ Failed to send SMS to {}: {}", maskPhoneNumber(phoneNumber), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send SMS via AWS SNS with message attributes
     * 
     * ADDED for SMS Integration - Core AWS SNS send method
     */
    private void sendSms(String phoneNumber, String message, String userEmail) throws Exception {
        // ADDED for SMS Cost Guard — enforce global daily cap BEFORE any provider send
        if (dailyCapReached(phoneNumber)) {
            return; // skip silently; scheduler marks the task notified, no retry storm
        }
        // ADDED for SMS provider switch
        if ("twilio".equalsIgnoreCase(smsProvider)) {
            sendViaTwilio(phoneNumber, message);
            recordSmsSent(userEmail, phoneNumber); // ADDED for SMS Cost Guard
            return;
        }
        logger.debug("DEBUG: Sending SMS via AWS SNS..."); //ADDED for SMS Integration
        logger.debug("DEBUG: SNS Client initialized: {}", (snsClient != null)); //ADDED for SMS Integration

        if (snsClient == null) {
            logger.error("❌ SNS client not initialized. Cannot send SMS.");
            throw new IllegalStateException("SNS client not initialized");
        }

        try {
            // Set message attributes for SMS
            Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();

            // Set SMS type to Transactional (high priority, no promotional filtering)
            smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                    .withStringValue("Transactional")
                    .withDataType("String"));

            // Set sender ID (shown as sender name on recipient's phone)
            smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                    .withStringValue("TaskMgr")  // Max 11 alphanumeric characters
                    .withDataType("String"));

            logger.debug("DEBUG: Message attributes set"); //ADDED for SMS Integration

            // Create publish request
            PublishRequest publishRequest = new PublishRequest()
                    .withPhoneNumber(phoneNumber)
                    .withMessage(message)
                    .withMessageAttributes(smsAttributes);

            logger.debug("DEBUG: Publish request created"); //ADDED for SMS Integration

            // Send SMS
            PublishResult result = snsClient.publish(publishRequest);

            logger.info("✅ SMS published to AWS SNS. MessageId: {}", result.getMessageId());
            logger.debug("DEBUG: SNS Response - MessageId: {}, StatusCode: {}", 
                result.getMessageId(), 
                result.getSdkHttpMetadata().getHttpStatusCode()); //ADDED for SMS Integration

            recordSmsSent(userEmail, phoneNumber); // ADDED for SMS Cost Guard

        } catch (Exception e) {
            logger.error("❌ AWS SNS publish failed: {}", e.getMessage(), e);
            logger.debug("DEBUG: Exception type: {}", e.getClass().getName()); //ADDED for SMS Integration
            throw e;
        }
    }

    /**
     * ADDED for SMS Cost Guard — true when today's global send count has hit the cap.
     * Alerts the admin once per day (in-memory marker; a restart re-alerts once, harmless).
     */
    private boolean dailyCapReached(String phoneNumber) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long sentToday = userActivityRepository.countByEventTypeAndCreatedAtAfter(UserActivity.SMS_SENT, startOfDay);
        if (sentToday < smsDailyCap) {
            return false;
        }
        logger.warn("⚠️ SMS daily cap reached ({}/{}) — skipping send to {}",
                sentToday, smsDailyCap, maskPhoneNumber(phoneNumber));
        LocalDate today = LocalDate.now();
        if (!today.equals(capAlertDate)) {
            capAlertDate = today;
            activityService.alertAdmin("SMS daily cap reached (" + smsDailyCap + ")",
                    "<h3>📱 SMS daily cap reached</h3>"
                    + "<p>" + smsDailyCap + " SMS have been sent today; further sends are skipped until midnight.</p>"
                    + "<p>Provider: " + smsProvider + " · raise SMS_DAILY_CAP in .env if this is expected.</p>");
        }
        return true;
    }

    /**
     * ADDED for SMS Cost Guard — one activity row per successful send, both rails.
     * Uses ActivityService so a failed log write never breaks the send itself.
     */
    private void recordSmsSent(String userEmail, String phoneNumber) {
        activityService.record(userEmail, UserActivity.SMS_SENT,
                "to " + maskPhoneNumber(phoneNumber) + " via " + smsProvider, null);
    }

    /**
     * ADDED for SMS provider switch — Twilio Messages API via plain HTTP.
     * One form-encoded POST with Basic auth: no SDK dependency required.
     * Trial accounts send only to verified caller IDs and prefix the body
     * with a trial notice — both fine for end-to-end feature testing.
     */
    private void sendViaTwilio(String phoneNumber, String message) throws Exception {
        if (twilioSid.isBlank() || twilioToken.isBlank() || twilioFrom.isBlank()) {
            throw new IllegalStateException("Twilio provider selected but TWILIO_* env is incomplete");
        }
        // ADDED for Twilio trial — trial API rejects free-form bodies (error 572006);
        // substitute the configured predefined template name when one is set.
        if (twilioTrialTemplate != null && !twilioTrialTemplate.isBlank()) {
            logger.info("ℹ️ Twilio trial template '{}' used in place of task text (upgrade + unset TWILIO_TRIAL_TEMPLATE for real bodies)", twilioTrialTemplate);
            message = twilioTrialTemplate;
        }
        String form = "To=" + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8)
                + "&From=" + URLEncoder.encode(twilioFrom, StandardCharsets.UTF_8)
                + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        String auth = Base64.getEncoder().encodeToString(
                (twilioSid + ":" + twilioToken).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            logger.info("✅ SMS sent via Twilio to {} (status {})", phoneNumber, res.statusCode());
        } else {
            logger.error("❌ Twilio send failed: status {} body {}", res.statusCode(), res.body());
            throw new IllegalStateException("Twilio send failed: " + res.statusCode());
        }
    }

    /**
     * Build SMS message content
     * Similar to EmailService.buildEmailContent() but for SMS (160 character limit consideration)
     * 
     * ADDED for SMS Integration - Message formatting
     */
    private String buildSmsMessage(Task task) {
        logger.debug("DEBUG: Building SMS message for task {}", task.getId()); //ADDED for SMS Integration

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
        String formattedDate = task.getDueDate() != null 
            ? task.getDueDate().format(formatter) 
            : "Not set";

        // Build concise SMS message (considering 160 character SMS limit)
        // Format: ⏰ Task Due: Title | Priority: HIGH | Due: Dec 25 at 3:00 PM
        String message = String.format(
            "⏰ Task Due: %s | Priority: %s | Due: %s",
            truncateText(task.getTitle(), 40),  // Limit title length
            task.getPriority().toString(),
            formattedDate
        );

        logger.debug("DEBUG: SMS message built - Length: {} chars", message.length()); //ADDED for SMS Integration
        
        return message;
    }

    /**
     * Validate phone number format (E.164 format: +[country code][number])
     * E.164 format examples:
     * - India: +919876543210
     * - USA: +11234567890
     * - UK: +441234567890
     * 
     * ADDED for SMS Integration - Phone validation
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            logger.debug("DEBUG: Phone number is null or empty"); //ADDED for SMS Integration
            return false;
        }

        // E.164 format: starts with +, followed by country code and number
        // Total length: 8-15 digits (including country code)
        boolean isValid = phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
        logger.debug("DEBUG: Phone validation result: {} for number: {}", isValid, maskPhoneNumber(phoneNumber)); //ADDED for SMS Integration

        return isValid;
    }

    /**
     * Mask phone number for logging (privacy protection)
     * Example: +919876543210 → +91****3210
     * 
     * ADDED for SMS Integration - Privacy protection in logs
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "***";
        }
        
        // Show first 3 chars (+91) and last 4 digits, mask the rest
        String prefix = phoneNumber.substring(0, Math.min(3, phoneNumber.length()));
        String suffix = phoneNumber.substring(Math.max(0, phoneNumber.length() - 4));
        int maskedLength = phoneNumber.length() - prefix.length() - suffix.length();
        
        return prefix + "*".repeat(Math.max(0, maskedLength)) + suffix;
    }

    /**
     * Truncate text to specified length (for SMS character limit)
     * 
     * ADDED for SMS Integration - Text truncation utility
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Send test SMS (for debugging/testing)
     * Similar to EmailService.sendTestEmail()
     * 
     * ADDED for SMS Integration - Testing method
     */
    public void sendTestSms(String phoneNumber) throws Exception {
        logger.info("🧪 Sending test SMS to {}", maskPhoneNumber(phoneNumber));
        logger.debug("DEBUG: Test SMS initiated"); //ADDED for SMS Integration

        if (!isValidPhoneNumber(phoneNumber)) {
            logger.error("❌ Invalid phone number format for test SMS: {}", phoneNumber);
            throw new IllegalArgumentException("Invalid phone number format");
        }

        String testMessage = "Test SMS from Task Manager. SMS service is working correctly! ✅";
        
        try {
            // ADDED for SMS Cost Guard — test sends cost money too, so they count toward the cap
            sendSms(phoneNumber, testMessage, "sms-test@sriinfosoft.local");
            logger.info("✅ Test SMS sent to {}", maskPhoneNumber(phoneNumber));
        } catch (Exception e) {
            logger.error("❌ Test SMS failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}