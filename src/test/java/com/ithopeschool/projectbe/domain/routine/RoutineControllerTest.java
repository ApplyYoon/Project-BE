package com.ithopeschool.projectbe.domain.routine;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoutineController.class)
class RoutineControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean RoutineService service;

  @ParameterizedTest
  @ValueSource(strings = {"[]", "[-1]", "[7]", "[null]"})
  void invalidWeekdaysAre400(String days) throws Exception {
    mvc.perform(
            post("/api/v1/routines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekdays\":" + days + ",\"content\":\"test\",\"targetMinutes\":30}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(delete("/api/v1/routines/3")).andExpect(status().isNoContent());
    verify(service).delete(3L);
  }
}
