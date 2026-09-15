package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.model.SessionParticipant;
import com.sriinfosoft.taskmanager.repository.SessionOfferingRepository;
import com.sriinfosoft.taskmanager.repository.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lobby: check-in role resolution + role-gated lobby view. */
@ExtendWith(MockitoExtension.class)
class LobbyServiceTest {

    @Mock SessionOfferingRepository offeringRepo;
    @Mock SessionParticipantRepository partRepo;
    @Mock ReachabilityService reachability;
    @Mock ReachService reachService;

    LobbyService service;
    private static final String MENTOR = "mentor@example.com";
    private static final String LEARNER = "learner@example.com";

    @BeforeEach
    void setup() { service = new LobbyService(offeringRepo, partRepo, reachability, reachService); }

    private SessionOffering offering() {
        SessionOffering o = new SessionOffering();
        o.setId(9L); o.setMentorEmail(MENTOR); o.setTitle("AWS");
        o.setStatus(SessionOffering.Status.SCHEDULED);
        return o;
    }

    @Test
    void checkIn_asMentor_stampsMentor() {
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(offering()));
        service.checkIn(MENTOR, 9L);
        verify(offeringRepo).save(any(SessionOffering.class));
    }

    @Test
    void checkIn_asStranger_isForbidden() {
        when(reachability.isVerified("x@example.com")).thenReturn(true);
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(offering()));
        when(partRepo.findBySessionOfferingIdAndUserEmail(9L, "x@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkIn("x@example.com", 9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void lobby_asMentor_returnsRoster() {
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(offering()));
        SessionParticipant p = new SessionParticipant(9L, LEARNER, SessionParticipant.Role.LEARNER);
        when(partRepo.findBySessionOfferingId(9L)).thenReturn(List.of(p));
        LobbyService.LobbyView v = service.lobby(MENTOR, 9L);
        assertThat(v.role()).isEqualTo("MENTOR");
        assertThat(v.participants()).hasSize(1);
        assertThat(v.self()).isNull();
    }

    @Test
    void lobby_asLearner_returnsSelfOnly() {
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(offering()));
        SessionParticipant p = new SessionParticipant(9L, LEARNER, SessionParticipant.Role.LEARNER);
        when(partRepo.findBySessionOfferingIdAndUserEmail(9L, LEARNER)).thenReturn(Optional.of(p));
        LobbyService.LobbyView v = service.lobby(LEARNER, 9L);
        assertThat(v.role()).isEqualTo("LEARNER");
        assertThat(v.participants()).isNull();               // no peer roster for learners
        assertThat(v.self()).isNotNull();
    }

    @Test
    void reach_fromStranger_isForbidden() {
        when(offeringRepo.findById(9L)).thenReturn(Optional.of(offering()));
        when(partRepo.existsBySessionOfferingIdAndUserEmail(9L, "x@example.com")).thenReturn(false);
        assertThatThrownBy(() -> service.reach("x@example.com", 9L, LEARNER, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
