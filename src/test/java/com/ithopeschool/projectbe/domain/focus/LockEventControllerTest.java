package com.ithopeschool.projectbe.domain.focus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ithopeschool.projectbe.domain.focus.dto.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LockEventController.class)
class LockEventControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean LockEventService service;

  @Test
  void validatesBodyAndReturnsCreated() throws Exception {
    mvc.perform(post("/api/v1/lock-events").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
    when(service.create(any()))
        .thenReturn(
            new LockEventResponse(1L, Instant.parse("2026-10-17T10:00:00Z"), "app", "reason"));
    mvc.perform(
            post("/api/v1/lock-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"occurredAt\":\"2026-10-17T10:00:00Z\",\"appName\":\"app\",\"reason\":\"reason\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  void rejectsMissingDates() throws Exception {
    mvc.perform(get("/api/v1/lock-events/stats")).andExpect(status().isBadRequest());
  }
}
