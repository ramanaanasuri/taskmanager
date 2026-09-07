package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.Question;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the InsightHub email-subject contract: unique per question id
 * (so Gmail doesn't thread every answer together), whitespace-normalized,
 * word-boundary truncated at ~60 chars with an ellipsis.
 * The helper is private by design; reflection keeps production visibility intact.
 */
class MentorSubjectTest {

    private String subjectFor(Long id, String text) {
        Question q = new Question();
        q.setId(id);
        q.setText(text);
        MentorService svc = new MentorService(); // helper touches no dependencies
        return ReflectionTestUtils.invokeMethod(svc, "subjectFor", q);
    }

    @Test
    void shortQuestion_keptVerbatim_withIdPrefix() {
        assertThat(subjectFor(7L, "What is a rollback?"))
                .isEqualTo("InsightHub #7: What is a rollback?");
    }

    @Test
    void whitespace_isNormalized() {
        assertThat(subjectFor(8L, "  what\n   is   \t a  savepoint  "))
                .isEqualTo("InsightHub #8: what is a savepoint");
    }

    @Test
    void longQuestion_truncatesAtWordBoundary_withEllipsis() {
        String text = "How does the outbox pattern guarantee delivery when the database "
                + "commit succeeds but the message broker publish fails afterwards?";
        String subject = subjectFor(9L, text);
        assertThat(subject).startsWith("InsightHub #9: How does the outbox pattern");
        assertThat(subject).endsWith("\u2026");
        // prefix + truncated text stays close to the 60-char text budget
        assertThat(subject.length()).isLessThan("InsightHub #9: ".length() + 62 + 1);
        assertThat(subject).doesNotContain("afterwards");
    }

    @Test
    void distinctIds_produceDistinctSubjects_forSameText() {
        assertThat(subjectFor(1L, "same text"))
                .isNotEqualTo(subjectFor(2L, "same text"));
    }

    @Test
    void nullText_isHandled() {
        assertThat(subjectFor(3L, null)).isEqualTo("InsightHub #3: ");
    }
}
