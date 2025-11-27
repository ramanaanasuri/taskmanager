package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by email address
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find user by Stripe customer ID
     */
    Optional<User> findByStripeCustomerId(String stripeCustomerId);
    
    /**
     * Find user by Stripe subscription ID
     */
    Optional<User> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if Stripe customer ID already exists
     */
    boolean existsByStripeCustomerId(String stripeCustomerId);
}
