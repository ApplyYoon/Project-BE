package com.ithopeschool.projectbe.domain.health;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
class PingControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private DataSource dataSource;
  @MockitoBean private HealthService healthService;

  @Test
  void pingWorksWithoutAuthenticationOrDatabaseAccess() throws Exception {
    mockMvc
        .perform(get("/api/v1/ping"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json("{\"status\":\"ok\"}"));

    verifyNoInteractions(dataSource, healthService);
  }
}
