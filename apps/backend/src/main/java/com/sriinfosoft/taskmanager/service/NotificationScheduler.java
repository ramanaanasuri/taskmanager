package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.NotificationLog;
import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.NotificationLogRepository;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler that checks for due tasks and sends notifications (push + email)
 * Runs every minute to check if any tasks are due
 * 
 * MODIFIED for Email Integration - Now sends both push and email notifications
 */
@Component
public class NotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    //ADDED for Email Integration - Email notification service
    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    /**
     * Runs every minute to check for tasks that are due
     * cron expression: "0 * * * * *" means: at second 0 of every minute
     * 
     * MODIFIED for Email Integration - Now sends both push and email notifications
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkAndSendDueTaskNotifications() {
        logger.info("🔔 Running scheduled task notification check at {}", LocalDateTime.now());
        logger.debug("DEBUG: Email Integration - Scheduler triggered"); //ADDED for Email Integration

        try {
            // Get current time
            LocalDateTime now = LocalDateTime.now();
            
            // Find tasks that:
            // 1. Are not completed
            // 2. Have notifications enabled
            // 3. Have a due date within the next 2 minutes (to catch tasks even if scheduler misses exact time)
            LocalDateTime checkUntil = now.plusMinutes(2);
            
            logger.debug("DEBUG: Checking for tasks between {} and {}", now.minusMinutes(1), checkUntil); //ADDED for Email Integration
            
            List<Task> dueTasks = taskRepository.findDueTasksForNotification(
                now.minusMinutes(1), // Look back 1 minute to catch any missed
                checkUntil,
                false // not completed
            );

            logger.info("📋 Found {} tasks due for notification", dueTasks.size());

            for (Task task : dueTasks) {
                try {
                    // Check if we've already sent notification for this task
                    // (to avoid duplicate notifications)
                    if (task.getReminderSent() == null || !task.getReminderSent()) {
                        logger.info("📤 Sending notifications for task: {} (ID: {})", task.getTitle(), task.getId());
                        logger.debug("DEBUG: Task details - Priority: {}, Due: {}, Email: {}", 
                            task.getPriority(), task.getDueDate(), task.getUserEmail()); //ADDED for Email Integration
                        
                        //MODIFIED for Email Integration - Now sends BOTH push and email notifications
                        sendAllNotifications(task);
                        
                        // Mark as notification sent
                        task.setReminderSent(true);
                        taskRepository.save(task);
                        
                        logger.info("✅ All notifications sent for task: {} (ID: {})", task.getTitle(), task.getId());
                    } else {
                        logger.debug("⏭️ Skipping task {} - notification already sent", task.getId());
                    }
                } catch (Exception e) {
                    logger.error("❌ Error sending notification for task {}: {}", task.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            logger.error("💥 Error in notification scheduler: {}", e.getMessage(), e);
        }
    }

    //ADDED for Email Integration - Send ALL notifications (push + email) for a task
    /**
     * Send ALL notifications (push + email) for a task
     */
    private void sendAllNotifications(Task task) {
        logger.debug("DEBUG: sendAllNotifications() called for task {}", task.getId()); //ADDED for Email Integration
        
        // 1. Send Push Notification
        sendPushNotificationWithLogging(task);
        
        // 2. Send Email Notification (NEW)
        sendEmailNotificationWithLogging(task);
    }

    /**
     * Send push notification for a task AND log the result
     * MODIFIED for Email Integration - Updated logging to distinguish between push and email
     */
    private void sendPushNotificationWithLogging(Task task) {
        logger.debug("DEBUG: Preparing PUSH notification for task {}", task.getId()); //ADDED for Email Integration
        
        String title = "⏰ Task Due: " + task.getTitle();
        String body = String.format(
            "Priority: %s | Due: %s",
            task.getPriority(),
            task.getDueDate().truncatedTo(ChronoUnit.MINUTES)
        );
        
        try {
            // Send the push notification
            pushNotificationService.sendNotificationToUser(
                task.getUserEmail(),
                title,
                body,
                task.getId()
            );
            
            //MODIFIED for Email Integration - Updated logging parameter to specify "push"
            logNotificationSuccess(task, "push", null);
            logger.info("✅ Push notification sent for task {}", task.getId());
            
        } catch (Exception e) {
            logger.error("❌ Failed to send push notification for task {}: {}", task.getId(), e.getMessage());
            
            //MODIFIED for Email Integration - Updated logging parameter to specify "push"
            logNotificationFailure(task, "push", e.getMessage());
        }
    }

    //ADDED for Email Integration - Send email notification for a task AND log the result
    /**
     * Send email notification for a task AND log the result
     */
    private void sendEmailNotificationWithLogging(Task task) {
        logger.debug("DEBUG: Preparing EMAIL notification for task {}", task.getId()); //ADDED for Email Integration
        logger.debug("DEBUG: Email will be sent to: {}", task.getUserEmail()); //ADDED for Email Integration
        
        try {
            // Send the email notification
            emailService.sendTaskDueNotification(task, task.getUserEmail());
            
            // Log successful email notification
            logNotificationSuccess(task, "email", task.getUserEmail());
            logger.info("✅ Email notification sent for task {} to {}", task.getId(), task.getUserEmail()); //ADDED for Email Integration
            
        } catch (Exception e) {
            logger.error("❌ Failed to send email notification for task {}: {}", task.getId(), e.getMessage());
            logger.error("DEBUG: Email error details: ", e); //ADDED for Email Integration
            
            // Log failed email notification
            logNotificationFailure(task, "email", e.getMessage());
        }
    }

    /**
     * Log successful notification to database
     * MODIFIED for Email Integration - Now accepts notificationType parameter ("push" or "email")
     */
    private void logNotificationSuccess(Task task, String notificationType, String endpoint) {
        logger.debug("DEBUG: Logging {} notification success for task {}", notificationType, task.getId()); //ADDED for Email Integration
        
        try {
            NotificationLog log = new NotificationLog();
            log.setTaskId(task.getId());
            log.setUserEmail(task.getUserEmail());
            log.setNotificationType(notificationType);  //MODIFIED for Email Integration - "push" or "email"
            log.setStatus("sent");
            log.setSentToEndpoint(endpoint);
            log.setDeviceType(task.getCreatedFromDevice() != null ? task.getCreatedFromDevice() : "web");
            log.setCreatedAt(LocalDateTime.now());
            
            notificationLogRepository.save(log);
            logger.debug("📝 {} notification logged successfully for task {}", notificationType, task.getId());
            
        } catch (Exception e) {
            // Don't let logging failure stop the process
            logger.error("⚠️ Failed to log {} notification for task {}: {}", notificationType, task.getId(), e.getMessage());
        }
    }

    /**
     * Log failed notification to database
     * MODIFIED for Email Integration - Now accepts notificationType parameter ("push" or "email")
     */
    private void logNotificationFailure(Task task, String notificationType, String errorMessage) {
        logger.debug("DEBUG: Logging {} notification failure for task {}", notificationType, task.getId()); //ADDED for Email Integration
        
        try {
            NotificationLog log = new NotificationLog();
            log.setTaskId(task.getId());
            log.setUserEmail(task.getUserEmail());
            log.setNotificationType(notificationType);  //MODIFIED for Email Integration - "push" or "email"
            log.setStatus("failed");
            log.setErrorMessage(errorMessage);
            log.setCreatedAt(LocalDateTime.now());
            
            notificationLogRepository.save(log);
            logger.debug("📝 {} notification failure logged for task {}", notificationType, task.getId());
            
        } catch (Exception e) {
            // Don't let logging failure stop the process
            logger.error("⚠️ Failed to log {} notification failure for task {}: {}", notificationType, task.getId(), e.getMessage());
        }
    }

    /**
     * Manual trigger for testing (can be called from a test endpoint)
     */
    public void triggerManualCheck() {
        logger.info("🔧 Manual notification check triggered");
        checkAndSendDueTaskNotifications();
    }
}