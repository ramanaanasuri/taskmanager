package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.model.SessionParticipant;
import com.sriinfosoft.taskmanager.repository.SessionOfferingRepository;
import com.sriinfosoft.taskmanager.repository.SessionParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The pre-session lobby handshake. Check-in is intent-based readiness (a
 * timestamp), not ambient presence. The lobby view is field-gated by role:
 * the mentor sees the learner roster; a learner sees mentor status + self.
 * Reach is authorized here (mentor<->enrolled-learner only) then delegated.
 */
@Service
public class LobbyService {

    private final SessionOfferingRepository offeringRepo;
    private final SessionParticipantRepository partRepo;
    private final ReachabilityService reachability;
    private final ReachService reachService;

    public LobbyService(SessionOfferingRepository offeringRepo,
                        SessionParticipantRepository partRepo,
                        ReachabilityService reachability,
                        ReachService reachService) {
        this.offeringRepo = offeringRepo;
        this.partRepo = partRepo;
        this.reachability = reachability;
        this.reachService = reachService;
    }

    /** Mentor (owner) or enrolled learner marks themselves ready. */
    public void checkIn(String email, Long offeringId) {
        if (!reachability.isVerified(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "VERIFICATION_REQUIRED");
        }
        SessionOffering o = offeringRepo.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        if (o.getMentorEmail().equals(email)) {
            o.setMentorCheckedInAt(LocalDateTime.now());
            offeringRepo.save(o);
            return;
        }
        SessionParticipant p = partRepo.findBySessionOfferingIdAndUserEmail(offeringId, email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of this session"));
        p.setCheckedInAt(LocalDateTime.now());
        partRepo.save(p);
    }

    /** Role-gated lobby snapshot. */
    public LobbyView lobby(String email, Long offeringId) {
        SessionOffering o = offeringRepo.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        boolean mentorReady = o.getMentorCheckedInAt() != null;

        if (o.getMentorEmail().equals(email)) {
            List<ParticipantView> roster = new ArrayList<>();
            for (SessionParticipant p : partRepo.findBySessionOfferingId(offeringId)) {
                roster.add(new ParticipantView(p.getUserEmail(), p.getCheckedInAt() != null));
            }
            return new LobbyView("MENTOR", o.getId(), o.getTitle(), mentorReady, roster, null, o.getZoomJoinUrl());
        }
        SessionParticipant self = partRepo.findBySessionOfferingIdAndUserEmail(offeringId, email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of this session"));
        ParticipantView selfView = new ParticipantView(self.getUserEmail(), self.getCheckedInAt() != null);
        return new LobbyView("LEARNER", o.getId(), o.getTitle(), mentorReady, null, selfView, o.getZoomJoinUrl());
    }

    /** Authorize and send a reach: mentor -> a specific enrolled learner, or learner -> the mentor. */
    public ReachService.ReachResult reach(String fromEmail, Long offeringId,
                                          String targetEmail, List<String> channels) {
        SessionOffering o = offeringRepo.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        boolean fromMentor = o.getMentorEmail().equals(fromEmail);
        boolean fromLearner = partRepo.existsBySessionOfferingIdAndUserEmail(offeringId, fromEmail);
        if (!fromMentor && !fromLearner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of this session");
        }
        String to;
        if (fromMentor) {
            if (targetEmail == null || targetEmail.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetEmail required");
            }
            if (!partRepo.existsBySessionOfferingIdAndUserEmail(offeringId, targetEmail)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target is not enrolled");
            }
            to = targetEmail;
        } else {
            to = o.getMentorEmail();   // a learner can only reach the mentor
        }
        return reachService.reach(fromEmail, to, offeringId, o.getTitle(), channels);
    }

    public record ParticipantView(String email, boolean checkedIn) {}
    public record LobbyView(String role, Long offeringId, String title, boolean mentorCheckedIn,
                            List<ParticipantView> participants, ParticipantView self, String joinUrl) {}
}
