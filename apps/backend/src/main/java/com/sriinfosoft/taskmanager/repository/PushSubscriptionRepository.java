package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    
    List<PushSubscription> findByUserEmail(String userEmail);
    
    Optional<PushSubscription> findByEndpoint(String endpoint);
    
    void deleteByUserEmail(String userEmail);
}
