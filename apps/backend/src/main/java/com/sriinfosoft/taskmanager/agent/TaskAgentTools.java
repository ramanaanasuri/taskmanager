package com.sriinfosoft.taskmanager.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.model.TaskPriority;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * APP-SPECIFIC TOOLBOX for Task Manager Pro. Five tools, all scoped to the
 * authenticated user's email. A future application (payments, e-commerce)
 * ships its own class like this — same interface, different domain — and
 * the agent loop works unchanged.
 *
 * Deliberate omissions (guardrails in code, not prompt):
 *   - NO delete tool exists in v1: the agent cannot call what is not defined.
 *   - update/complete/schedule mutate existing data -> one per turn unless
 *     the user confirmed (enforced by AgentService).
 */
public class TaskAgentTools {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** Model/user wall-clock (ctx zone) -> UTC for storage. */
    private static LocalDateTime toUtc(LocalDateTime local, ZoneId zone) {
        return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Stored UTC -> user wall-clock for anything shown to the model. */
    private static LocalDateTime toLocal(LocalDateTime utc, ZoneId zone) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
    }

    private static String line(Task t, ZoneId zone) {
        return "{id:" + t.getId()
             + ", title:\"" + t.getTitle() + "\""
             + ", priority:" + t.getPriority()
             + ", due:" + (t.getDueDate() == null ? "none" : toLocal(t.getDueDate(), zone).format(ISO))
             + ", completed:" + Boolean.TRUE.equals(t.getCompleted())
             + ", notify{email:" + Boolean.TRUE.equals(t.getEmailEnabled())
             + ",push:" + Boolean.TRUE.equals(t.getNotificationsEnabled())
             + ",sms:" + Boolean.TRUE.equals(t.getSmsEnabled()) + "}}";
    }

    /** Load a task only if it belongs to this user. */
    private static Optional<Task> owned(TaskRepository repo, String email, long id) {
        return repo.findById(id).filter(t -> email.equals(t.getUserEmail()));
    }

    // ---------------------------------------------------------------- list
    @Component
    public static class ListTasks implements AgentTool {
        @Autowired private TaskRepository repo;

        public String name() { return "list_tasks"; }
        public String description() {
            return "List the user's tasks. filter: all | open | completed | overdue | today | week (open = not completed).";
        }
        public String parametersSchema() {
            return """
                {"type":"object","properties":{
                   "filter":{"type":"string","enum":["all","open","completed","overdue","today","week"]}
                 }}""";
        }
        public boolean mutatesExistingData() { return false; }

        public String execute(AgentContext ctx, JsonNode args) {
            String userEmail = ctx.userEmail();
            ZoneId zone = ctx.zone();
            String filter = args.path("filter").asText("open");
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC); // due_date is stored UTC
            List<Task> tasks = repo.findByUserEmail(userEmail).stream().filter(t -> switch (filter) {
                case "completed" -> Boolean.TRUE.equals(t.getCompleted());
                case "overdue"   -> !Boolean.TRUE.equals(t.getCompleted())
                                     && t.getDueDate() != null && t.getDueDate().isBefore(now);
                case "today"     -> !Boolean.TRUE.equals(t.getCompleted()) && t.getDueDate() != null
                                     && toLocal(t.getDueDate(), zone).toLocalDate()
                                        .equals(toLocal(now, zone).toLocalDate()); // "today" in the USER's day
                case "week"      -> !Boolean.TRUE.equals(t.getCompleted()) && t.getDueDate() != null
                                     && t.getDueDate().isAfter(now) && t.getDueDate().isBefore(now.plusDays(7));
                case "all"       -> true;
                default          -> !Boolean.TRUE.equals(t.getCompleted());
            }).toList();
            if (tasks.isEmpty()) return "No tasks match filter '" + filter + "'.";
            StringBuilder sb = new StringBuilder("Tasks (" + tasks.size() + "):\n");
            tasks.forEach(t -> sb.append(line(t, zone)).append('\n'));
            return sb.toString();
        }
    }

    // -------------------------------------------------------------- create
    @Component
    public static class CreateTask implements AgentTool {
        @Autowired private TaskRepository repo;

        public String name() { return "create_task"; }
        public String description() {
            return "Create a new task for the user. dueDate format YYYY-MM-DDTHH:mm (user-local), omit if none.";
        }
        public String parametersSchema() {
            return """
                {"type":"object","properties":{
                   "title":{"type":"string"},
                   "priority":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                   "dueDate":{"type":"string"},
                   "notifyEmail":{"type":"boolean"}
                 },"required":["title"]}""";
        }
        public boolean mutatesExistingData() { return false; } // additive, low risk

        public String execute(AgentContext ctx, JsonNode args) {
            String userEmail = ctx.userEmail();
            ZoneId zone = ctx.zone();
            String title = args.path("title").asText("").trim();
            if (title.isEmpty()) return "ERROR: title is required.";
            Task t = new Task();
            t.setUserEmail(userEmail);
            t.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            try { t.setPriority(TaskPriority.valueOf(args.path("priority").asText("MEDIUM"))); }
            catch (Exception e) { t.setPriority(TaskPriority.MEDIUM); }
            String due = args.path("dueDate").asText(null);
            if (due != null && !due.isBlank()) {
                try { t.setDueDate(toUtc(LocalDateTime.parse(due, ISO), zone)); }
                catch (Exception e) { return "ERROR: dueDate must be YYYY-MM-DDTHH:mm, got '" + due + "'."; }
            }
            t.setEmailEnabled(args.path("notifyEmail").asBoolean(false));
            Task saved = repo.save(t);
            return "Created: " + line(saved, zone);
        }
    }

    // -------------------------------------------------------------- update
    @Component
    public static class UpdateTask implements AgentTool {
        @Autowired private TaskRepository repo;

        public String name() { return "update_task"; }
        public String description() {
            return "Update fields of one existing task by id (title, priority, dueDate). Only include fields to change.";
        }
        public String parametersSchema() {
            return """
                {"type":"object","properties":{
                   "id":{"type":"integer"},
                   "title":{"type":"string"},
                   "priority":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                   "dueDate":{"type":"string"}
                 },"required":["id"]}""";
        }
        public boolean mutatesExistingData() { return true; }

        public String execute(AgentContext ctx, JsonNode args) {
            String userEmail = ctx.userEmail();
            ZoneId zone = ctx.zone();
            Optional<Task> opt = owned(repo, userEmail, args.path("id").asLong(-1));
            if (opt.isEmpty()) return "ERROR: no task with that id belongs to this user.";
            Task t = opt.get();
            if (args.hasNonNull("title")) t.setTitle(args.get("title").asText());
            if (args.hasNonNull("priority")) {
                try { t.setPriority(TaskPriority.valueOf(args.get("priority").asText())); }
                catch (Exception e) { return "ERROR: priority must be LOW, MEDIUM or HIGH."; }
            }
            if (args.hasNonNull("dueDate")) {
                try { t.setDueDate(toUtc(LocalDateTime.parse(args.get("dueDate").asText(), ISO), zone)); }
                catch (Exception e) { return "ERROR: dueDate must be YYYY-MM-DDTHH:mm."; }
            }
            t.setUpdatedAt(LocalDateTime.now());
            return "Updated: " + line(repo.save(t), zone);
        }
    }

    // ------------------------------------------------------------ complete
    @Component
    public static class CompleteTask implements AgentTool {
        @Autowired private TaskRepository repo;

        public String name() { return "complete_task"; }
        public String description() { return "Mark one task as completed by id."; }
        public String parametersSchema() {
            return """
                {"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}""";
        }
        public boolean mutatesExistingData() { return true; }

        public String execute(AgentContext ctx, JsonNode args) {
            String userEmail = ctx.userEmail();
            ZoneId zone = ctx.zone();
            Optional<Task> opt = owned(repo, userEmail, args.path("id").asLong(-1));
            if (opt.isEmpty()) return "ERROR: no task with that id belongs to this user.";
            Task t = opt.get();
            t.setCompleted(true);
            t.setUpdatedAt(LocalDateTime.now());
            return "Completed: " + line(repo.save(t), zone);
        }
    }

    // ------------------------------------------- schedule / notifications
    @Component
    public static class ScheduleNotification implements AgentTool {
        @Autowired private TaskRepository repo;

        public String name() { return "schedule_notification"; }
        public String description() {
            return "Enable or disable a notification channel (email, push, sms) for one task by id.";
        }
        public String parametersSchema() {
            return """
                {"type":"object","properties":{
                   "id":{"type":"integer"},
                   "channel":{"type":"string","enum":["email","push","sms"]},
                   "enabled":{"type":"boolean"}
                 },"required":["id","channel","enabled"]}""";
        }
        public boolean mutatesExistingData() { return true; }

        public String execute(AgentContext ctx, JsonNode args) {
            String userEmail = ctx.userEmail();
            ZoneId zone = ctx.zone();
            Optional<Task> opt = owned(repo, userEmail, args.path("id").asLong(-1));
            if (opt.isEmpty()) return "ERROR: no task with that id belongs to this user.";
            Task t = opt.get();
            boolean enabled = args.path("enabled").asBoolean(false);
            switch (args.path("channel").asText("")) {
                case "email" -> t.setEmailEnabled(enabled);
                case "push"  -> t.setNotificationsEnabled(enabled);
                case "sms"   -> {
                    if (enabled && (t.getPhoneNumber() == null || t.getPhoneNumber().isBlank())) {
                        return "ERROR: this task has no phone number on file; SMS must be set up in the task form first.";
                    }
                    t.setSmsEnabled(enabled);
                }
                default -> { return "ERROR: channel must be email, push or sms."; }
            }
            t.setUpdatedAt(LocalDateTime.now());
            return "Notification updated: " + line(repo.save(t), zone);
        }
    }
}
