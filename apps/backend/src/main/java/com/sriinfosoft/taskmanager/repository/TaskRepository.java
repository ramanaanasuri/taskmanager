package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserEmail(String userEmail);

    /**
     * Find tasks that are due for notification
     * Used by NotificationScheduler to check for due tasks across all users
     * 
     * @param start - start of time window to check
     * @param end - end of time window to check
     * @param completed - whether task is completed
     * @return list of tasks due for notification
     */
    @Query("SELECT t FROM Task t WHERE " +
    "t.notificationsEnabled = true AND " +
    "t.completed = :completed AND " +
    "t.dueDate BETWEEN :start AND :end AND " +
    "(t.reminderSent = false OR t.reminderSent IS NULL) " +
    "ORDER BY t.dueDate ASC")
    List<Task> findDueTasksForNotification(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        @Param("completed") boolean completed
    );
}