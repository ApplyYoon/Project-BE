package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.message.MessageService;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:safety-flow;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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

  // 세 입력 칸 중 하나라도 위기 표현이면 응답 즉시 보호하고 원문을 로그에 남기지 않습니다.
  @ParameterizedTest
  @ValueSource(strings = {"missReasonNote", "promiseIf", "promiseThen"})
  void everyFreeTextFieldTriggersProtectionWithoutLogging(String field, CapturedOutput output)
      throws Exception {
    String privateText = "안전검증전용원문 사라지고 싶어 " + field;
    String note = field.equals("missReasonNote") ? privateText : "일정이 바뀌었다";
    String promiseIf = field.equals("promiseIf") ? privateText : "집에 오면";
    String promiseThen = field.equals("promiseThen") ? privateText : "물을 마신다";
    String body =
        """
        {"source":"manual","missReason":"etc","missReasonNote":"%s",
         "promiseIf":"%s","promiseThen":"%s"}
        """
            .formatted(note, promiseIf, promiseThen);
    mvc.perform(
            put("/api/v1/checkins/2026-10-02")
                .header("Authorization", "Bearer local-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpMessage").value(SafetyService.HELP_MESSAGE))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"))
        .andExpect(jsonPath("$.helpLines[1].number").value("109"));
    assertThat(safety.isActive(1L)).isTrue();
    assertThat(safety.isActive(2L)).isFalse();
    assertThat(output.getAll()).doesNotContain(privateText);
  }
}
