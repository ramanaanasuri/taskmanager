package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "${cors.allowed-origins}", allowCredentials = "true")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    // ----------------- helpers -----------------

    private ResponseEntity<?> unauthenticated() {
        System.err.println("❌ Unauthenticated request – returning 401");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthenticated or invalid token"));
    }

    /**
     * Extract the signed-in user's email from Spring Security.
     * Works for:
     *  - JWT (principal as UserDetails or String)
     *  - OAuth2 (principal as OAuth2User)
     * Returns null if it can’t resolve a usable identity (to avoid 500s).
     */
    private String getCurrentUserEmail() {
        System.out.println("=== getCurrentUserEmail() called ===");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Authentication object: " + authentication);
        System.out.println("Is authenticated: " + (authentication != null && authentication.isAuthenticated()));

        if (authentication == null || !authentication.isAuthenticated()) {
            System.err.println("ERROR: No authentication in context");
            return null;
        }

        Object principal = authentication.getPrincipal();
        System.out.println("Principal type: " + (principal != null ? principal.getClass().getName() : "null"));
        System.out.println("Principal value: " + principal);

        if (principal instanceof UserDetails userDetails) {
            // Common for JWT filters that build a UserDetails with username=email
            String email = userDetails.getUsername();
            System.out.println("✅ JWT (UserDetails) – email: " + email);
            return email;
        }

        if (principal instanceof String s) {
            // Some JWT filters store the subject/email directly as String
            System.out.println("✅ JWT (String) – email: " + s);
            return s;
        }

        if (principal instanceof OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            System.out.println("OAuth2 attributes: " + oAuth2User.getAttributes());
            if (email != null) {
                System.out.println("✅ OAuth2 – email: " + email);
                return email;
            }
            // fallbacks (provider-dependent)
            String preferred = oAuth2User.getAttribute("preferred_username");
            if (preferred != null) {
                System.out.println("✅ OAuth2 – preferred_username: " + preferred);
                return preferred;
            }
            String name = oAuth2User.getName();
            System.out.println("⚠️ OAuth2 – falling back to getName(): " + name);
            return name;
        }

        // Last resort – Spring often sets getName() to username/sub
        String name = authentication.getName();
        System.out.println("⚠️ Generic fallback – authentication.getName(): " + name);
        return (name != null && !name.isBlank()) ? name : null;
    }

    // ----------------- endpoints -----------------

    @GetMapping
    public ResponseEntity<?> getAllTasks() {
        System.out.println("\n🔍 === GET /api/tasks called ===");
        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                System.err.println("ERROR in getAllTasks: unable to resolve email from principal");
                return unauthenticated();
            }

            System.out.println("Fetching tasks for user: " + email);
            List<Task> tasks = taskRepository.findByUserEmail(email);
            System.out.println("Found " + tasks.size() + " tasks");
            return ResponseEntity.ok(tasks);

        } catch (Exception e) {
            System.err.println("ERROR in getAllTasks: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error fetching tasks"));
        }
    }

    @PostMapping
    public ResponseEntity<?> createTask(@Valid @RequestBody Task task) {
        System.out.println("\n➕ === POST /api/tasks called ===");
        System.out.println("Incoming title: " + task.getTitle());

        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                System.err.println("ERROR in createTask: unable to resolve email from principal");
                return unauthenticated();
            }

            System.out.println("Creating task for user: " + email);
            task.setUserEmail(email);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            Task saved = taskRepository.save(task);
            System.out.println("✅ Task created: id=" + saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (Exception e) {
            System.err.println("ERROR in createTask: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error creating task"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestBody Task taskDetails) {
        System.out.println("\n✏️ === PUT /api/tasks/" + id + " called ===");
        System.out.println("Patch -> title: " + taskDetails.getTitle() + ", completed: " + taskDetails.getCompleted());

        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                System.err.println("ERROR in updateTask: unable to resolve email from principal");
                return unauthenticated();
            }

            Optional<Task> opt = taskRepository.findById(id);
            if (opt.isEmpty()) {
                System.err.println("ERROR: task " + id + " not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }

            Task task = opt.get();
            System.out.println("Task owner: " + task.getUserEmail());

            if (!email.equals(task.getUserEmail())) {
                System.err.println("ERROR: user " + email + " tried to update task owned by " + task.getUserEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden"));
            }

            if (taskDetails.getTitle() != null) {
                System.out.println("Updating title to: " + taskDetails.getTitle());
                task.setTitle(taskDetails.getTitle());
            }
            if (taskDetails.getCompleted() != null) {
                System.out.println("Updating completed to: " + taskDetails.getCompleted());
                task.setCompleted(taskDetails.getCompleted());
            }
            // Update priority
            if (taskDetails.getPriority() != null) {
                task.setPriority(taskDetails.getPriority());
            }

            // Update due date
            task.setDueDate(taskDetails.getDueDate());
            task.setUpdatedAt(LocalDateTime.now());
            Task saved = taskRepository.save(task);
            System.out.println("✅ Task updated: id=" + saved.getId());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            System.err.println("ERROR in updateTask: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error updating task"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        System.out.println("\n🗑️ === DELETE /api/tasks/" + id + " called ===");

        try {
            String email = getCurrentUserEmail();
            if (email == null || email.isBlank()) {
                System.err.println("ERROR in deleteTask: unable to resolve email from principal");
                return unauthenticated();
            }

            Optional<Task> opt = taskRepository.findById(id);
            if (opt.isEmpty()) {
                System.err.println("ERROR: task " + id + " not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found"));
            }

            Task task = opt.get();
            System.out.println("Task owner: " + task.getUserEmail());

            if (!email.equals(task.getUserEmail())) {
                System.err.println("ERROR: user " + email + " tried to delete task owned by " + task.getUserEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden"));
            }

            taskRepository.delete(task);
            System.out.println("✅ Task deleted: id=" + id);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            System.err.println("ERROR in deleteTask: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error deleting task"));
        }
    }
}
