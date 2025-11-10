package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserEmail(String userEmail);
    @Query("SELECT t FROM Task t WHERE t.notificationsEnabled = true AND t.userEmail = :userEmail")
    List<Task> findTasksWithNotificationsEnabled(@Param("userEmail") String userEmail);    
}