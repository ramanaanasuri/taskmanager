package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.ReachConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReachConsentRepository extends JpaRepository<ReachConsent, Long> {
    Optional<ReachConsent> findByUserEmail(String userEmail);
}
