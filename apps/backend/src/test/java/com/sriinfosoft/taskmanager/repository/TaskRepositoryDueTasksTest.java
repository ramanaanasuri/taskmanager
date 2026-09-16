package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB-backed test for findDueTasksForNotification (against H2). Guards the fix
 * where email-only or SMS-only tasks were being skipped because the query only
 * checked notificationsEnabled (= push). A task must be picked up if ANY channel
 * is enabled.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:duetasks;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false"
})
class TaskRepositoryDueTasksTest {

    @Autowired TaskRepository repo;

    private Task task(String title, boolean push, boolean email, boolean sms,
                      boolean completed, boolean reminderSent, LocalDateTime due) {
        Task t = new Task(title, "user@example.com");
        t.setNotificationsEnabled(push);
        t.setEmailEnabled(email);
        t.setSmsEnabled(sms);
        t.setCompleted(completed);
        t.setDueDate(due);          // setDueDate re-arms (resets reminderSent) -> set reminderSent AFTER
        t.setReminderSent(reminderSent);
        return t;
    }

    @Test
    void picksUpAnyEnabledChannel_notJustPush() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now;                       // inside the window
        LocalDateTime start = now.minusMinutes(1);
        LocalDateTime end = now.plusMinutes(2);

        repo.save(task("push-only",  true,  false, false, false, false, due));
        repo.save(task("email-only", false, true,  false, false, false, due));   // was being skipped
        repo.save(task("sms-only",   false, false, true,  false, false, due));   // was being skipped
        repo.save(task("all-off",    false, false, false, false, false, due));   // must NOT fire
        repo.save(task("completed",  false, true,  false, true,  false, due));   // completed -> no
        repo.save(task("already",    false, true,  false, false, true,  due));   // reminderSent -> no
        repo.save(task("out-window", false, true,  false, false, false, now.plusHours(6))); // outside -> no

        List<Task> dueTasks = repo.findDueTasksForNotification(start, end, false);
        List<String> titles = dueTasks.stream().map(Task::getTitle).toList();

        assertThat(titles).containsExactlyInAnyOrder("push-only", "email-only", "sms-only");
        assertThat(titles).doesNotContain("all-off", "completed", "already", "out-window");
    }
}
