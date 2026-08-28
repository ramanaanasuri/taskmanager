package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.InsightHubMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsightHubMemberRepository extends JpaRepository<InsightHubMember, Long> {
    List<InsightHubMember> findByInsightHubId(Long insightHubId);
    List<InsightHubMember> findByMemberEmail(String memberEmail);
    Optional<InsightHubMember> findByInsightHubIdAndMemberEmail(Long insightHubId, String memberEmail);
    boolean existsByInsightHubIdAndMemberEmail(Long insightHubId, String memberEmail);
}
