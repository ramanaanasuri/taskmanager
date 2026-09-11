package com.sriinfosoft.taskmanager.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the schedule_notification tool's sms branch — the
 * scenario behind the Sep 10 trust bug, where the agent confirmed enabling
 * SMS on a task that had no phone number while the tool had refused.
 * The contract under test: a supplied E.164 number is stored and sms enabled
 * in one call; anything invalid or missing yields an ERROR result and NO save.
 * (The companion prompt rule — never narrate an ERROR as success — is LLM
 * behavior and is not unit-testable here.)
 */
@ExtendWith(MockitoExtension.class)
class AgentSmsToolTest {

    @Mock TaskRepository repo;
    @InjectMocks TaskAgentTools.ScheduleNotification tool;

    private static final String USER = "owner@example.com";
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentTool.AgentContext ctx =
            new AgentTool.AgentContext(USER, ZoneId.of("America/Los_Angeles"));

    private Task task;

    @BeforeEach
    void baseTask() {
        task = new Task();
        task.setId(162L);
        task.setTitle("dental appointment");
        task.setUserEmail(USER);
        task.setSmsEnabled(false);
        task.setPhoneNumber(null);
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void enableSms_withValidNumber_storesNumberAndEnables() throws Exception {
        when(repo.findById(162L)).thenReturn(Optional.of(task));
        when(repo.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":true,\"phoneNumber\":\"+15105791057\"}"));

        assertThat(result).doesNotStartWith("ERROR");
        assertThat(task.getPhoneNumber()).isEqualTo("+15105791057");
        assertThat(task.getSmsEnabled()).isTrue();
        verify(repo).save(task);
    }

    @Test
    void enableSms_noNumberAnywhere_returnsErrorAndSavesNothing() throws Exception {
        // The exact Sep 10 scenario: task 162, no phone on file, no phone in the call.
        when(repo.findById(162L)).thenReturn(Optional.of(task));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":true}"));

        assertThat(result).startsWith("ERROR");
        assertThat(task.getSmsEnabled()).isFalse();
        assertThat(task.getPhoneNumber()).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    void enableSms_malformedNumber_returnsErrorAndSavesNothing() throws Exception {
        when(repo.findById(162L)).thenReturn(Optional.of(task));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":true,\"phoneNumber\":\"510-579-1057\"}"));

        assertThat(result).startsWith("ERROR");
        assertThat(result).contains("NOT enabled");
        assertThat(task.getSmsEnabled()).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    void enableSms_numberAlreadyOnFile_enablesWithoutNewNumber() throws Exception {
        task.setPhoneNumber("+15105791057");
        when(repo.findById(162L)).thenReturn(Optional.of(task));
        when(repo.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":true}"));

        assertThat(result).doesNotStartWith("ERROR");
        assertThat(task.getSmsEnabled()).isTrue();
        verify(repo).save(task);
    }

    @Test
    void disableSms_worksWithoutPhoneNumber() throws Exception {
        task.setSmsEnabled(true);
        when(repo.findById(162L)).thenReturn(Optional.of(task));
        when(repo.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":false}"));

        assertThat(result).doesNotStartWith("ERROR");
        assertThat(task.getSmsEnabled()).isFalse();
    }

    @Test
    void otherUsersTask_returnsErrorAndSavesNothing() throws Exception {
        task.setUserEmail("someone-else@example.com");
        when(repo.findById(162L)).thenReturn(Optional.of(task));

        String result = tool.execute(ctx, args(
                "{\"id\":162,\"channel\":\"sms\",\"enabled\":true,\"phoneNumber\":\"+15105791057\"}"));

        assertThat(result).startsWith("ERROR");
        verify(repo, never()).save(any());
    }
}
