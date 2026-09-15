package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.model.SessionParticipant;
import com.sriinfosoft.taskmanager.repository.SessionOfferingRepository;
import com.sriinfosoft.taskmanager.repository.SessionParticipantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Learner-side enrolment. The seat-claim is the worked example of "why SQL":
 * a pessimistic row lock on the offering makes count+insert atomic (capacity
 * safety across different users), and UNIQUE(offering,email) guarantees
 * idempotency (no duplicate seat for the same user). Gated on verification.
 */
@Service
public class EnrollmentService {

    private final SessionOfferingRepository offeringRepo;
    private final SessionParticipantRepository partRepo;
    private final ReachabilityService reachability;

    public EnrollmentService(SessionOfferingRepository offeringRepo,
                             SessionParticipantRepository partRepo,
                             ReachabilityService reachability) {
        this.offeringRepo = offeringRepo;
        this.partRepo = partRepo;
        this.reachability = reachability;
    }

    @Transactional
    public JoinResult join(String email, Long offeringId) {
        if (!reachability.isVerified(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "VERIFICATION_REQUIRED");
        }
        // Lock the offering row so concurrent joins serialize on the capacity check.
        SessionOffering o = offeringRepo.findByIdForUpdate(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        if (o.getStatus() != SessionOffering.Status.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offering is not open for enrolment");
        }
        if (partRepo.existsBySessionOfferingIdAndUserEmail(offeringId, email)) {
            return new JoinResult("ALREADY_ENROLLED", o.getZoomJoinUrl());   // idempotent
        }
        long count = partRepo.countBySessionOfferingId(offeringId);
        if (count >= o.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FULL");
        }
        try {
            partRepo.save(new SessionParticipant(offeringId, email, SessionParticipant.Role.LEARNER));
        } catch (DataIntegrityViolationException dup) {
            // lost a same-user race against the UNIQUE constraint -> still idempotent
            return new JoinResult("ALREADY_ENROLLED", o.getZoomJoinUrl());
        }
        return new JoinResult("ENROLLED", o.getZoomJoinUrl());
    }

    /** Browse SCHEDULED offerings (optionally by skill). Never includes the join URL. */
    public List<OfferingView> browse(Long skillId) {
        List<SessionOffering> list = (skillId == null)
                ? offeringRepo.findByStatusOrderByStartTimeAsc(SessionOffering.Status.SCHEDULED)
                : offeringRepo.findByStatusAndSkillIdOrderByStartTimeAsc(SessionOffering.Status.SCHEDULED, skillId);
        List<OfferingView> out = new ArrayList<>();
        for (SessionOffering o : list) out.add(view(o, false));   // link hidden in browse
        return out;
    }

    /** Detail. Join URL included only for the mentor (owner) or an enrolled learner. */
    public OfferingView detail(String email, Long offeringId) {
        SessionOffering o = offeringRepo.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        boolean authorized = o.getMentorEmail().equals(email)
                || partRepo.existsBySessionOfferingIdAndUserEmail(offeringId, email);
        return view(o, authorized);
    }

    /** The learner's enrolled sessions (join URL included — they're enrolled). */
    public List<OfferingView> mySessions(String email) {
        List<OfferingView> out = new ArrayList<>();
        for (SessionParticipant p : partRepo.findByUserEmail(email)) {
            offeringRepo.findById(p.getSessionOfferingId()).ifPresent(o -> out.add(view(o, true)));
        }
        return out;
    }

    private OfferingView view(SessionOffering o, boolean includeLink) {
        long count = partRepo.countBySessionOfferingId(o.getId());
        int seatsLeft = Math.max(0, o.getCapacity() - (int) count);
        return new OfferingView(
                o.getId(), o.getMentorEmail(), o.getSkillId(), o.getTitle(), o.getDescription(),
                o.getStartTime(), o.getDurationMin(), o.getCapacity(), seatsLeft, o.getStatus().name(),
                o.getRateCurrency(), o.getRateAmount(), o.getRateDescription(),
                includeLink ? o.getZoomJoinUrl() : null);
    }

    public record JoinResult(String outcome, String joinUrl) {}

    public record OfferingView(Long id, String mentorEmail, Long skillId, String title, String description,
                               LocalDateTime startTime, int durationMin, int capacity, int seatsLeft,
                               String status, String rateCurrency, BigDecimal rateAmount,
                               String rateDescription, String joinUrl) {}
}
