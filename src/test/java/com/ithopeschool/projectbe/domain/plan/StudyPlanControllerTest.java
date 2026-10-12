package com.ithopeschool.projectbe.domain.plan;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 "날짜 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(StudyPlanService)은 가짜로 바꿉니다.
@WebMvcTest(StudyPlanController.class)
class StudyPlanControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudyPlanService studyPlanService;

  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

  @Test
  @DisplayName("목록은 200 이고, 계획마다 FE StudyPlan 의 아홉 칸이 있다")
  void getPlans() throws Exception {
    given(studyPlanService.getPlans(OCT_5))
        .willReturn(
            List.of(
                new StudyPlanResponse(
                    "7", OCT_5, "3", "수학 문제집 3단원 풀기", 90, false, null, "study", null)));

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("7"))
        .andExpect(jsonPath("$[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$[0].subjectId").value("3"))
        .andExpect(jsonPath("$[0].content").value("수학 문제집 3단원 풀기"))
        .andExpect(jsonPath("$[0].targetMinutes").value(90))
        .andExpect(jsonPath("$[0].done").value(false))
        .andExpect(jsonPath("$[0].kind").value("study"))
        // 값이 없는 칸은 빼지 않고 null 로 보냅니다. (docs/API.md 4장)
        .andExpect(jsonPath("$[0].startTime").isEmpty())
        .andExpect(jsonPath("$[0].routineId").isEmpty())
        .andExpect(jsonPath("$[0].length()").value(9))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("계획이 없는 날은 404 가 아니라 빈 배열 []")
  void emptyDay() throws Exception {
    given(studyPlanService.getPlans(OCT_5)).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("날짜를 안 보내면 400 이고, Service 는 부르지 않는다")
  void missingDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studyPlanService);
  }

  @ParameterizedTest(name = "date={0} 이면 400")
  @ValueSource(strings = {"2026/10/05", "2026-10-5", "2026-13-01", "10월 5일", "", "abc"})
  @DisplayName("날짜 모양이 틀리면 400 이고, Service 는 부르지 않는다")
  void wrongDateFormat(String date) throws Exception {
    mockMvc
        .perform(get("/api/v1/study-plans").param("date", date))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(studyPlanService);
  }

  @Test
  @DisplayName("로그인하지 않았으면 Service 가 던진 401 이 그대로 나간다")
  void unauthorized() throws Exception {
    given(studyPlanService.getPlans(OCT_5))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/study-plans").param("date", "2026-10-05"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }
}
