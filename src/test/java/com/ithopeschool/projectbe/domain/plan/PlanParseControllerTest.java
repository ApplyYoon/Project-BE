package com.ithopeschool.projectbe.domain.plan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.plan.dto.PlanParseResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(PlanParseService)은 가짜입니다.
@WebMvcTest(PlanParseController.class)
class PlanParseControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlanParseService planParseService;

  private ResultActions parse(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/study-plans/parse").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    parse(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(planParseService);
  }

  @Test
  @DisplayName("보내면 200 과 초안 목록이다 (저장하지 않으므로 201 이 아니다)")
  void parses() throws Exception {
    given(planParseService.parse(any()))
        .willReturn(
            new PlanParseResponse(
                false,
                List.of(
                    new PlanParseResponse.Draft(
                        LocalDate.parse("2026-10-11"), "3", "문제집", 120, "15:00", "study"),
                    new PlanParseResponse.Draft(
                        LocalDate.parse("2026-10-11"), null, "게임", 60, null, "play"))));

    parse("{\"date\":\"2026-10-11\",\"text\":\"3시부터 수학 문제집 2시간, 게임 1시간\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.drafts[0].date").value("2026-10-11"))
        .andExpect(jsonPath("$.drafts[0].subjectId").value("3"))
        .andExpect(jsonPath("$.drafts[0].startTime").value("15:00"))
        .andExpect(jsonPath("$.drafts[1].subjectId").doesNotExist())
        .andExpect(jsonPath("$.drafts[1].kind").value("play"));
  }

  @Test
  @DisplayName("문장이 비었거나 200자를 넘거나, 날짜가 없으면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"date\":\"2026-10-11\",\"text\":\"\"}", "계획을 한 문장으로 적어 주세요");
    expectRejected("{\"date\":\"2026-10-11\",\"text\":\"   \"}", "계획을 한 문장으로 적어 주세요");
    expectRejected("{\"date\":\"2026-10-11\"}", "계획을 한 문장으로 적어 주세요");
    expectRejected(
        "{\"date\":\"2026-10-11\",\"text\":\"" + "가".repeat(201) + "\"}", "문장은 200자 이하로 적어 주세요");
    expectRejected("{\"text\":\"수학 2시간\"}", "날짜를 보내 주세요");
  }

  @Test
  @DisplayName("200자는 받는다")
  void acceptsTwoHundredChars() throws Exception {
    given(planParseService.parse(any())).willReturn(new PlanParseResponse(false, List.of()));

    parse("{\"date\":\"2026-10-11\",\"text\":\"" + "가".repeat(200) + "\"}")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GPT 가 실패하면 빈 목록이 아니라 503 이고, 에러 모양(#5)으로 답한다")
  void gptFailureIs503() throws Exception {
    given(planParseService.parse(any()))
        .willThrow(
            new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "계획을 만들지 못했어요. 잠시 후 다시 시도해 주세요"));
    parse("{\"date\":\"2026-10-11\",\"text\":\"수학 2시간\"}")
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  @DisplayName("위기 표현이면 200 과 showHelp: true, 빈 초안이다")
  void crisisShowsHelp() throws Exception {
    given(planParseService.parse(any())).willReturn(PlanParseResponse.help());

    parse("{\"date\":\"2026-10-11\",\"text\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.drafts").isEmpty());
  }
}
