package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.Task;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Service for sending email notifications
 * 
 * ADDED for Email Integration - New service class
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    /**
     * Send task due notification email
     * 
     * ADDED for Email Integration - Main email sending method
     * 
     * @param task The task that is due
     * @param userEmail Email address to send to
     * @throws MessagingException if email sending fails
     */
    public void sendTaskDueNotification(Task task, String userEmail) throws MessagingException {
        logger.info("📧 Preparing email notification for task {} to {}", task.getId(), userEmail);
        logger.debug("DEBUG: Email from address: {}", fromEmail); //ADDED for Email Integration
        logger.debug("DEBUG: Frontend URL: {}", frontendUrl); //ADDED for Email Integration

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(userEmail);
        helper.setSubject("⏰ Task Due: " + task.getTitle());

        String htmlContent = buildEmailContent(task);
        logger.debug("DEBUG: Email HTML content length: {} characters", htmlContent.length()); //ADDED for Email Integration
        
        helper.setText(htmlContent, true); // true = HTML content

        logger.debug("DEBUG: Sending email via JavaMailSender..."); //ADDED for Email Integration
        mailSender.send(message);
        logger.info("✅ Email sent successfully to {}", userEmail);
    }

    /**
     * Build HTML email content
     * 
     * ADDED for Email Integration - Creates beautiful HTML email template
     */
    private String buildEmailContent(Task task) {
        logger.debug("DEBUG: Building email content for task {}", task.getId()); //ADDED for Email Integration
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
        String formattedDate = task.getDueDate() != null 
            ? task.getDueDate().format(formatter) 
            : "Not set";

        String priorityColor = getPriorityColor(task.getPriority().toString());
        String taskUrl = frontendUrl + "/?taskId=" + task.getId();

        logger.debug("DEBUG: Email template data - Priority: {}, Color: {}, URL: {}", 
            task.getPriority(), priorityColor, taskUrl); //ADDED for Email Integration

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f4f4f4; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 24px;">⏰ Task Reminder</h1>
                                    </td>
                                </tr>
                                
                                <!-- Content -->
                                <tr>
                                    <td style="padding: 30px;">
                                        <h2 style="color: #333333; margin-top: 0;">Your task is due!</h2>
                                        
                                        <div style="background-color: #f8f9fa; border-left: 4px solid %s; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                            <h3 style="color: #333333; margin-top: 0;">%s</h3>
                                            <p style="color: #666666; margin: 10px 0;">
                                                <strong>Priority:</strong> 
                                                <span style="background-color: %s; color: white; padding: 3px 10px; border-radius: 12px; font-size: 12px;">
                                                    %s
                                                </span>
                                            </p>
                                            <p style="color: #666666; margin: 10px 0;">
                                                <strong>Due Date:</strong> %s
                                            </p>
                                        </div>
                                        
                                        <div style="text-align: center; margin: 30px 0;">
                                            <a href="%s" 
                                               style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); 
                                                      color: white; 
                                                      padding: 12px 30px; 
                                                      text-decoration: none; 
                                                      border-radius: 25px; 
                                                      display: inline-block;
                                                      font-weight: bold;">
                                                View Task
                                            </a>
                                        </div>
                                        
                                        <p style="color: #999999; font-size: 12px; text-align: center; margin-top: 30px;">
                                            You're receiving this because you have notifications enabled for this task.
                                        </p>
                                    </td>
                                </tr>
                                
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8f9fa; padding: 20px; text-align: center;">
                                        <p style="color: #999999; font-size: 12px; margin: 0;">
                                            Task Manager by SriInfoSoft<br>
                                            <a href="%s" style="color: #667eea; text-decoration: none;">Visit Dashboard</a>
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                priorityColor,
                task.getTitle(),
                priorityColor,
                task.getPriority().toString(),
                formattedDate,
                taskUrl,
                frontendUrl
            );
    }

    /**
     * Get color for priority badge
     * 
     * ADDED for Email Integration - Maps priority levels to colors
     */
    private String getPriorityColor(String priority) {
        return switch (priority.toUpperCase()) {
            case "HIGH" -> "#dc3545";
            case "MEDIUM" -> "#ffc107";
            case "LOW" -> "#28a745";
            default -> "#6c757d";
        };
    }

    /**
     * Send test email (for debugging)
     * 
     * ADDED for Email Integration - Testing endpoint
     */
    public void sendTestEmail(String toEmail) throws MessagingException {
        logger.info("🧪 Sending test email to {}", toEmail);
        logger.debug("DEBUG: Test email from: {}", fromEmail); //ADDED for Email Integration
        
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("Test Email from Task Manager");
        helper.setText("<h1>Test Email</h1><p>Email service is working correctly!</p>", true);

        mailSender.send(message);
        logger.info("✅ Test email sent to {}", toEmail);
    }
}