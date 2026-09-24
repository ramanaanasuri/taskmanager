package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.TaskRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "${cors.allowed-origins}", allowCredentials = "true")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private TaskRepository taskRepository;

    // ----------------- helpers -----------------

    private ResponseEntity<?> unauthenticated() {
        log.warn("Unauthenticated request to /api/tasks - returning 401");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthenticated or invalid token"));
    }

    //ADDED - Helper method to detect device type from User-Agent
    /**
     * Determines device type (mobile/tablet/web) from User-Agent string
     */
    private String getDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "unknown";
        }
        
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("mobile") || userAgent.contains("android") || 
            userAgent.contains("iphone") || userAgent.contains("ipod")) {
            return "mobile";
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return "tablet";
        } else {
            return "web";
        }
    }

    //ADDED - Helper method to get real client IP address
    /**
     * Extracts the real client IP address, handling proxies and load balancers
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If X-Forwarded-For contains multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }    

    /**
     * Extract the signed-in user's email from Spring Security.
     * Works for:
     *  - JWT (principal as UserDetails or String)
     *  - OAuth2 (principal as OAuth2User)
     * Returns null if it can’t resolve a usable identity (to avoid 500s).
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authenticated principal in the security context");
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            // Common for JWT filters that build a UserDetails with username=email
            String email = userDetails.getUsername();
            log.debug("Resolved identity from JWT (UserDetails)");
            return email;
        }

        if (principal instanceof String s) {
            // Some JWT filters store the subject/email directly as String
            log.debug("Resolved identity from JWT (String subject)");
            return s;
        }

        if (principal instanceof OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            if (email != null) {
                log.debug("Resolved identity from OAuth2 email attribute");
                return email;
            }
            // fallbacks (provider-dependent)
            String preferred = oAuth2User.getAttribute("preferred_username");
            if (preferred != null) {
                log.debug("Resolved identity from OAuth2 preferred_username");
                return preferred;
            }
            String name = oAuth2User.getName();
            log.debug("Resolved identity from OAuth2 getName() fallback");
            return name;
        }

        // Last resort – Spring often sets getName() to username/sub
        String name = authentication.getName();
        log.debug("Resolved identity from authentication.getName() fallback");
        return (name != null && !name.isBlank()) ? name : null;
    }

    // ----------------- endpoints -----------------

    @GetMapping
    public ResponseEntity<?> getAllTasks() {
        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                return unauthenticated();
            }

            List<Task> tasks = taskRepository.findByUserEmail(email);
            log.debug("Listed {} tasks", tasks.size());
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            log.error("Failed to list tasks", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error fetching tasks"));
        }
    }

    //MODIFIED - Added HttpServletRequest parameter and device tracking logic
    @PostMapping
    public ResponseEntity<?> createTask(
            @Valid @RequestBody Task task,
            HttpServletRequest request) { //ADDED - for capturing device info
        
        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                return unauthenticated();
            }

            task.setId(null); // always a new row: a client-supplied id would make save() overwrite an existing (possibly another user's) task
            task.setUserEmail(email);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            //ADDED - Capture device information
            String userAgent = request.getHeader("User-Agent");
            String clientIp = getClientIp(request);
            String deviceType = getDeviceType(userAgent);
            
            task.setCreatedFromDevice(deviceType);
            task.setCreatedFromIp(clientIp);
            task.setUserAgent(userAgent);
            
            //END ADDED

            //ADDED - Ensure reminder_sent is false by default
            if (task.getReminderSent() == null) {
                task.setReminderSent(false);
            }

            Task saved = taskRepository.save(task);
            log.info("Task created id={} device={}", saved.getId(), deviceType);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (Exception e) {
            log.error("Failed to create task", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error creating task"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestBody Task taskDetails) {
        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                return unauthenticated();
            }

            Optional<Task> opt = taskRepository.findById(id);
            if (opt.isEmpty()) {
                log.debug("Task id={} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }

            Task task = opt.get();

            if (!email.equals(task.getUserEmail())) {
                log.warn("Forbidden: update attempted on task id={} owned by another user", id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden"));
            }

            if (taskDetails.getTitle() != null) {
                task.setTitle(taskDetails.getTitle());
            }
            if (taskDetails.getCompleted() != null) {
                task.setCompleted(taskDetails.getCompleted());
            }
            // Update priority
            if (taskDetails.getPriority() != null) {
                task.setPriority(taskDetails.getPriority());
            }
            //ADDED - Update notification fields (for edit modal support)
            // Update email notifications
            if (taskDetails.getEmailEnabled() != null) {
                task.setEmailEnabled(taskDetails.getEmailEnabled());
            }
            
            // Update push notifications
            if (taskDetails.getNotificationsEnabled() != null) {
                task.setNotificationsEnabled(taskDetails.getNotificationsEnabled());
            }
            
            // Update SMS notifications
            if (taskDetails.getSmsEnabled() != null) {
                task.setSmsEnabled(taskDetails.getSmsEnabled());
            }
            
            // Update phone number
            if (taskDetails.getPhoneNumber() != null) {
                task.setPhoneNumber(taskDetails.getPhoneNumber());
            }
            //END ADDED            

            // Update due date
            task.setDueDate(taskDetails.getDueDate());
            task.setUpdatedAt(LocalDateTime.now());
            Task saved = taskRepository.save(task);
            log.info("Task updated id={}", saved.getId());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to update task id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error updating task"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                return unauthenticated();
            }

            Optional<Task> opt = taskRepository.findById(id);
            if (opt.isEmpty()) {
                log.debug("Task id={} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }

            Task task = opt.get();

            if (!email.equals(task.getUserEmail())) {
                log.warn("Forbidden: delete attempted on task id={} owned by another user", id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden"));
            }

            taskRepository.delete(task);
            log.info("Task deleted id={}", id);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Failed to delete task id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error deleting task"));
        }
    }
}
