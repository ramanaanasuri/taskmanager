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
 * Scheduler that checks for due tasks and sends push notifications
 * Runs every minute to check if any tasks are due
 */
@Component
public class NotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;  // ADDED

    /**
     * Runs every minute to check for tasks that are due
     * cron expression: "0 * * * * *" means: at second 0 of every minute
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkAndSendDueTaskNotifications() {
        logger.info("🔔 Running scheduled task notification check at {}", LocalDateTime.now());

        try {
            // Get current time
            LocalDateTime now = LocalDateTime.now();
            
            // Find tasks that:
            // 1. Are not completed
            // 2. Have notifications enabled
            // 3. Have a due date within the next 2 minutes (to catch tasks even if scheduler misses exact time)
            LocalDateTime checkUntil = now.plusMinutes(2);
            
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
                        logger.info("📤 Sending notification for task: {} (ID: {})", task.getTitle(), task.getId());
                        
                        // Send notification and log result
                        sendTaskNotificationWithLogging(task);
                        
                        // Mark as notification sent
                        task.setReminderSent(true);
                        taskRepository.save(task);
                        
                        logger.info("✅ Notification sent and logged for task: {} (ID: {})", task.getTitle(), task.getId());
                    } else {
                        logger.debug("⏭️ Skipping task {} - notification already sent", task.getId());
                    }
                } catch (Exception e) {
                    logger.error("❌ Error sending notification for task {}: {}", task.getId(), e.getMessage(), e);
                    // Log the failure
                    logNotificationFailure(task, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("💥 Error in notification scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Send push notification for a task AND log the result
     */
    private void sendTaskNotificationWithLogging(Task task) {
        String title = "⏰ Task Due: " + task.getTitle();
        String body = String.format(
            "Priority: %s | Due: %s",
            task.getPriority(),
            task.getDueDate().truncatedTo(ChronoUnit.MINUTES)
        );
        
        try {
            // Send the notification (void method - doesn't return endpoint)
            pushNotificationService.sendNotificationToUser(
                task.getUserEmail(),
                title,
                body,task.getId()  //ADDED - Pass task ID so notification includes task context
            );
            
            // Log successful notification (endpoint will be null, that's fine)
            logNotificationSuccess(task, null);
            
        } catch (Exception e) {
            logger.error("Failed to send push notification for task {}: {}", task.getId(), e.getMessage());
            
            // Log failed notification
            logNotificationFailure(task, e.getMessage());
            
            throw e;
        }
    }

    /**
     * Log successful notification to database
     */
    private void logNotificationSuccess(Task task, String endpoint) {
        try {
            NotificationLog log = new NotificationLog();
            log.setTaskId(task.getId());
            log.setUserEmail(task.getUserEmail());
            log.setNotificationType("push");  // lowercase to match enum
            log.setStatus("sent");  // Use 'sent' status
            log.setSentToEndpoint(endpoint);
            log.setDeviceType("web");  // Can be enhanced to get actual device type
            log.setCreatedAt(LocalDateTime.now());
            
            notificationLogRepository.save(log);
            logger.debug("📝 Notification logged successfully for task {}", task.getId());
            
        } catch (Exception e) {
            // Don't let logging failure stop the process
            logger.error("⚠️ Failed to log notification for task {}: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * Log failed notification to database
     */
    private void logNotificationFailure(Task task, String errorMessage) {
        try {
            NotificationLog log = new NotificationLog();
            log.setTaskId(task.getId());
            log.setUserEmail(task.getUserEmail());
            log.setNotificationType("push");
            log.setStatus("failed");  // Use 'failed' status
            log.setErrorMessage(errorMessage);
            log.setCreatedAt(LocalDateTime.now());
            
            notificationLogRepository.save(log);
            logger.debug("📝 Notification failure logged for task {}", task.getId());
            
        } catch (Exception e) {
            // Don't let logging failure stop the process
            logger.error("⚠️ Failed to log notification failure for task {}: {}", task.getId(), e.getMessage());
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