package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    boolean existsBySessionOfferingIdAndUserEmail(Long sessionOfferingId, String userEmail);
    long countBySessionOfferingId(Long sessionOfferingId);
    Optional<SessionParticipant> findBySessionOfferingIdAndUserEmail(Long sessionOfferingId, String userEmail);
    List<SessionParticipant> findBySessionOfferingId(Long sessionOfferingId);
    List<SessionParticipant> findByUserEmail(String userEmail);
}
