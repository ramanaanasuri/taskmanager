package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.repository.SessionOfferingRepository;
import com.sriinfosoft.taskmanager.repository.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Seat-claim outcomes (Enrolled/Full/Already) + Zoom-link gating in views. */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock SessionOfferingRepository offeringRepo;
    @Mock SessionParticipantRepository partRepo;
    @Mock ReachabilityService reachability;

    EnrollmentService service;
    private static final String LEARNER = "learner@example.com";

    @BeforeEach
    void setup() { service = new EnrollmentService(offeringRepo, partRepo, reachability); }

    private SessionOffering scheduled(int capacity) {
        SessionOffering o = new SessionOffering();
        o.setId(9L); o.setMentorEmail("mentor@example.com"); o.setSkillId(1L);
        o.setStatus(SessionOffering.Status.SCHEDULED); o.setCapacity(capacity);
        o.setStartTime(LocalDateTime.now().plusDays(1)); o.setDurationMin(60);
        o.setZoomJoinUrl("https://meet.jit.si/room9");
        return o;
    }

    @Test
    void join_unverified_isForbidden() {
        when(reachability.isVerified(LEARNER)).thenReturn(false);
        assertThatThrownBy(() -> service.join(LEARNER, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void join_happy_enrolled() {
        when(reachability.isVerified(LEARNER)).thenReturn(true);
        when(offeringRepo.findByIdForUpdate(9L)).thenReturn(Optional.of(scheduled(2)));
        when(partRepo.existsBySessionOfferingIdAndUserEmail(9L, LEARNER)).thenReturn(false);
        when(partRepo.countBySessionOfferingId(9L)).thenReturn(0L);

        EnrollmentService.JoinResult r = service.join(LEARNER, 9L);
        assertThat(r.outcome()).isEqualTo("ENROLLED");
        assertThat(r.joinUrl()).contains("meet.jit.si");
    }

    @Test
    void join_full_isConflict() {
        when(reachability.isVerified(LEARNER)).thenReturn(true);
        when(offeringRepo.findByIdForUpdate(9L)).thenReturn(Optional.of(scheduled(1)));
        when(partRepo.existsBySessionOfferingIdAndUserEmail(9L, LEARNER)).thenReturn(false);
        when(partRepo.countBySessionOfferingId(9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.join(LEARNER, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void join_alreadyEnrolled_isIdempotent() {
        when(reachability.isVerified(LEARNER)).thenReturn(true);
        when(offeringRepo.findByIdForUpdate(9L)).thenReturn(Optional.of(scheduled(2)));
        when(partRepo.existsBySessionOfferingIdAndUserEmail(9L, LEARNER)).thenReturn(true);

        EnrollmentService.JoinResult r = service.join(LEARNER, 9L);
        assertThat(r.outcome()).isEqualTo("ALREADY_ENROLLED");
    }

    @Test
    void browse_hidesJoinUrl() {
        when(offeringRepo.findByStatusOrderByStartTimeAsc(SessionOffering.Status.SCHEDULED))
                .thenReturn(List.of(scheduled(3)));
        when(partRepo.countBySessionOfferingId(9L)).thenReturn(1L);

        List<EnrollmentService.OfferingView> out = service.browse(null);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).joinUrl()).isNull();          // link hidden in browse
        assertThat(out.get(0).seatsLeft()).isEqualTo(2);
    }

    @Test
    void detail_showsLinkToOwner_hidesFromStranger() {
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(scheduled(3)));
        when(partRepo.countBySessionOfferingId(9L)).thenReturn(0L);
        // stranger: not owner, not enrolled
        when(partRepo.existsBySessionOfferingIdAndUserEmail(9L, "stranger@example.com")).thenReturn(false);

        assertThat(service.detail("mentor@example.com", 9L).joinUrl()).isNotNull();   // owner
        assertThat(service.detail("stranger@example.com", 9L).joinUrl()).isNull();    // stranger
    }
}
