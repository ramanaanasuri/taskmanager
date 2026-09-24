package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.Task;
import com.sriinfosoft.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression test: POST /api/tasks must always create a new task. If the request body
 * carries an "id", JPA's save() would otherwise merge into that existing row, letting
 * any signed-in user take over (rename, reassign) someone else's task.
 */
class TaskControllerCreateTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createIgnoresAClientSuppliedIdSoExistingTasksCannotBeTakenOver() throws Exception {
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskController controller = new TaskController();
        Field field = TaskController.class.getDeclaredField("taskRepository");
        field.setAccessible(true);
        field.set(controller, repo);

        UserDetails bob = User.withUsername("bob@example.com").password("n/a").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(bob, null, bob.getAuthorities()));

        Task body = new Task("taken over", "alice@example.com");
        body.setId(42L);                                  // someone else's task id
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");

        ResponseEntity<?> response = controller.createTask(body, request);

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(repo).save(saved.capture());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(saved.getValue().getId()).as("client-supplied id must be discarded").isNull();
        assertThat(saved.getValue().getUserEmail()).isEqualTo("bob@example.com");
    }
}
