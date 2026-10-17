package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:safety-flow;MODE=PostgreSQL")
@AutoConfigureMockMvc
class SafetyFlowTest {
  @Autowired MockMvc mvc;
  @Autowired SafetyService safety;
  @Autowired CareSettingRepository settings;
  @Autowired MessageService messages;

  @Test
  void checkInImmediatelyProtectsOnlyItsOwner() throws Exception {
    mvc.perform(
            put("/api/v1/checkins/2026-10-01")
                .header("Authorization", "Bearer local-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"source\":\"manual\",\"missReason\":\"etc\",\"missReasonNote\":\"사라지고 싶어\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpMessage").value(SafetyService.HELP_MESSAGE))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"))
        .andExpect(jsonPath("$.helpLines[1].number").value("109"));
    assertThat(safety.isActive(1L)).isTrue();
    assertThat(safety.isActive(2L)).isFalse();
    assertThat(settings.findByUserId(1L).orElseThrow().maxCareLevelOn(LocalDate.now())).isZero();
    assertThatThrownBy(() -> messages.save(1L, MessageKind.STICK, "공부를 시작하자"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(messages.save(2L, MessageKind.STICK, "공부를 시작하자").getUserId()).isEqualTo(2L);
  }
}
