package com.sriinfosoft.taskmanager.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Default MeetingProvider: generates a Jitsi room URL locally with no external
 * call or credentials — so scheduling works out of the box and is testable.
 * The Zoom S2S provider is the production swap behind the same interface.
 */
@Component
public class JitsiMeetingProvider implements MeetingProvider {

    @Override
    public MeetingDetails createMeeting(Long offeringId, String title, String description,
                                        LocalDateTime startTime, int durationMin) {
        String room = "sri-" + offeringId + "-" + UUID.randomUUID().toString().substring(0, 8);
        String url = "https://meet.jit.si/" + room;
        return new MeetingDetails("jitsi", room, url, url);
    }
}
