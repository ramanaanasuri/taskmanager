package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByInsightHubIdOrderByCreatedAtDesc(Long insightHubId);
    List<Question> findByInsightHubIdAndAskedByEmailOrderByCreatedAtDesc(Long insightHubId, String askedByEmail);
}
