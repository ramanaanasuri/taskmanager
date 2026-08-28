package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.InsightHub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightHubRepository extends JpaRepository<InsightHub, Long> {
    List<InsightHub> findByMentorEmail(String mentorEmail);
}
