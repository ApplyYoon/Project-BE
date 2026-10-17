package com.ithopeschool.projectbe.domain.dailysignal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(DailySignalService)은 가짜입니다.
@WebMvcTest(DailySignalController.class)
class DailySignalControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DailySignalService dailySignalService;

  private ResultActions save(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/daily-signals").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(dailySignalService);
  }

  @Test
  @DisplayName("보내면 200 과 저장된 하루치를 돌려준다. 시각은 밀리초 숫자다")
  void saves() throws Exception {
    given(dailySignalService.save(any()))
        .willReturn(
            new DailySignalResponse(
                "5",
                LocalDate.parse("2026-10-17"),
                420,
                1792161000000L,
                1792188000000L,
                8500,
                true));

    save("{\"date\":\"2026-10-17\",\"sleepMinutes\":420,\"sleptAt\":1792161000000,"
            + "\"wokeAt\":1792188000000,\"steps\":8500,\"snsOverThirtyMinutes\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("5"))
        .andExpect(jsonPath("$.date").value("2026-10-17"))
        .andExpect(jsonPath("$.sleptAt").value(1792161000000L))
        .andExpect(jsonPath("$.snsOverThirtyMinutes").value(true));
  }

  @Test
  @DisplayName("날짜만 보내도 200 이다 (다른 칸은 다 비어도 된다)")
  void savesWithOnlyDate() throws Exception {
    given(dailySignalService.save(any()))
        .willReturn(
            new DailySignalResponse(
                "5", LocalDate.parse("2026-10-17"), null, null, null, null, null));

    save("{\"date\":\"2026-10-17\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps").doesNotExist());
  }

  @Test
  @DisplayName("날짜가 없거나, 수면 시간·걸음 수가 범위 밖이면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"steps\":100}", "날짜를 보내 주세요");
    expectRejected("{\"date\":\"2026-10-17\",\"sleepMinutes\":-1}", "수면 시간은 0분 이상이어야 해요");
    expectRejected("{\"date\":\"2026-10-17\",\"sleepMinutes\":1441}", "수면 시간은 1440분(24시간) 이하여야 해요");
    expectRejected("{\"date\":\"2026-10-17\",\"steps\":-5}", "걸음 수는 0 이상이어야 해요");
    expectRejected("{\"date\":\"2026-10-17\",\"steps\":100001}", "걸음 수는 100000 이하여야 해요");
  }

  @Test
  @DisplayName("서비스가 401 을 던지면 그대로 401 이다 (로그인 필요)")
  void requiresLogin() throws Exception {
    given(dailySignalService.save(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    save("{\"date\":\"2026-10-17\"}").andExpect(status().isUnauthorized());
  }
}
