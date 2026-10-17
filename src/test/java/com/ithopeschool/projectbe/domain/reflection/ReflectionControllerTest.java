package com.ithopeschool.projectbe.domain.reflection;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.reflection.dto.ReflectionCurrentResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 응답 모양과 상태 번호를 확인합니다. 진짜 로직(ReflectionService)은 가짜입니다.
@WebMvcTest(ReflectionController.class)
class ReflectionControllerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReflectionService reflectionService;

  @Test
  @DisplayName("처방이 있으면 200 과 FE 의 ReflectionPrescription 이름으로 돌려준다")
  void returnsPrescription() throws Exception {
    given(reflectionService.getCurrent())
        .willReturn(
            new ReflectionCurrentResponse(
                TODAY,
                new ReflectionCurrentResponse.Prescription(
                    "3",
                    TODAY,
                    TODAY.plusDays(4),
                    5,
                    "unwilling",
                    3,
                    "최근 일주일 동안 '하기 싫음' 때문에 못 채운 날이 3일 있었어. 5일 동안 짧게 돌아보자.",
                    List.of("질문 1?", "질문 2?", "질문 3?"))));

    mockMvc
        .perform(get("/api/v1/reflections/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-17"))
        .andExpect(jsonPath("$.prescription.id").value("3"))
        .andExpect(jsonPath("$.prescription.startDate").value("2026-10-17"))
        .andExpect(jsonPath("$.prescription.endDate").value("2026-10-21"))
        .andExpect(jsonPath("$.prescription.days").value(5))
        .andExpect(jsonPath("$.prescription.missReason").value("unwilling"))
        .andExpect(jsonPath("$.prescription.questions.length()").value(3));
  }

  @Test
  @DisplayName("처방이 없으면 404 가 아니라 200 이고, prescription 이 null 이다")
  void returnsNullWhenNoPrescription() throws Exception {
    given(reflectionService.getCurrent()).willReturn(ReflectionCurrentResponse.none(TODAY));

    mockMvc
        .perform(get("/api/v1/reflections/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-17"))
        .andExpect(jsonPath("$.prescription").isEmpty());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401")
  void rejectsWithoutLogin() throws Exception {
    given(reflectionService.getCurrent())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/reflections/current"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
