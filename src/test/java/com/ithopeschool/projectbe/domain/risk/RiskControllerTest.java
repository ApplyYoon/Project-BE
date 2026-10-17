package com.ithopeschool.projectbe.domain.risk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastListResponse;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastResponse;
import com.ithopeschool.projectbe.domain.risk.dto.RiskForecastResponse.Reason;
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

// Controller 만 띄워서 요청 모양 검사와 응답 모양·상태 번호를 확인합니다. 진짜 로직(RiskService)은 가짜입니다.
@WebMvcTest(RiskController.class)
class RiskControllerTest {

  private static final RiskForecastResponse HIGH =
      new RiskForecastResponse(
          LocalDate.parse("2026-10-13"),
          RiskLevel.HIGH,
          List.of(
              new Reason(RiskReason.WEAK_WEEKDAY, "약한 요일"),
              new Reason(RiskReason.LATE_SLEEP, "늦게 잠")),
          null,
          null);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RiskService riskService;

  private ResultActions forecast(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/risk-forecasts").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(riskService);
  }

  @Test
  @DisplayName("예측하면 200 과 위험 수준 · 이유(코드와 한국어 이름) · 비어 있는 결과 칸을 돌려준다")
  void forecasts() throws Exception {
    given(riskService.forecast(any())).willReturn(HIGH);

    forecast("{\"date\":\"2026-10-13\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-13"))
        .andExpect(jsonPath("$.riskLevel").value("HIGH"))
        .andExpect(jsonPath("$.reasons[0].code").value("WEAK_WEEKDAY"))
        .andExpect(jsonPath("$.reasons[0].label").value("약한 요일"))
        .andExpect(jsonPath("$.reasons[1].code").value("LATE_SLEEP"))
        .andExpect(jsonPath("$.reasons[1].label").value("늦게 잠"))
        .andExpect(jsonPath("$.actualAchievementRate").isEmpty())
        .andExpect(jsonPath("$.wasCorrect").isEmpty())
        .andExpect(jsonPath("$.length()").value(5));
  }

  @Test
  @DisplayName("날짜를 안 보내거나, 모양이 틀리거나, 날짜가 아닌 값이면 400 이고 로직까지 가지 않는다")
  void rejectsBadDate() throws Exception {
    expectRejected(forecast("{}"), "예측할 날짜를 보내 주세요");
    expectRejected(forecast("{\"date\":null}"), "예측할 날짜를 보내 주세요");
    expectRejected(forecast("{\"date\":\"2026/10/13\"}"), "보낸 값을 다시 확인해 주세요");
    expectRejected(forecast("{\"date\":\"내일\"}"), "보낸 값을 다시 확인해 주세요");
  }

  @Test
  @DisplayName("서비스가 던진 400(오늘·내일이 아님)과 409(동시 요청)는 에러 모양으로 나간다")
  void errorsFromService() throws Exception {
    given(riskService.forecast(any()))
        .willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "위험 예측은 오늘이나 내일만 할 수 있어요"));
    forecast("{\"date\":\"2026-01-01\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("위험 예측은 오늘이나 내일만 할 수 있어요"));

    // 이미 던지도록 만든 가짜를 다시 바꿀 때는 given(...) 대신 이 모양을 씁니다. (given 안의 호출이 먼저 예외를 던지기 때문)
    willThrow(new ApiException(ErrorCode.CONFLICT, "같은 날의 예측이 동시에 들어왔어요. 잠시 후 다시 불러 주세요"))
        .given(riskService)
        .forecast(any());
    forecast("{\"date\":\"2026-10-13\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  @DisplayName("날짜로 하나를 조회하면 200, 기록이 없으면 404 이다")
  void getsOne() throws Exception {
    given(riskService.get(LocalDate.parse("2026-10-13"))).willReturn(HIGH);

    mockMvc
        .perform(get("/api/v1/risk-forecasts/2026-10-13"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.riskLevel").value("HIGH"));

    given(riskService.get(LocalDate.parse("2026-10-14")))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "그날의 위험 예측이 없어요"));
    mockMvc
        .perform(get("/api/v1/risk-forecasts/2026-10-14"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("그날의 위험 예측이 없어요"));
  }

  @Test
  @DisplayName("조회 주소의 날짜 모양이 틀리면 400 이다")
  void rejectsBadDateInPath() throws Exception {
    expectRejected(mockMvc.perform(get("/api/v1/risk-forecasts/abc")), "보낸 값을 다시 확인해 주세요");
    expectRejected(mockMvc.perform(get("/api/v1/risk-forecasts/2026-13-40")), "보낸 값을 다시 확인해 주세요");
  }

  @Test
  @DisplayName("기간으로 조회하면 200 과 목록 · 확인된 개수 · 맞은 개수를 돌려준다")
  void listsAPeriod() throws Exception {
    given(riskService.list(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        .willReturn(
            new RiskForecastListResponse(
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"),
                List.of(HIGH),
                12,
                9));

    mockMvc
        .perform(
            get("/api/v1/risk-forecasts").param("from", "2026-10-01").param("to", "2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-10-01"))
        .andExpect(jsonPath("$.to").value("2026-10-31"))
        .andExpect(jsonPath("$.forecasts.length()").value(1))
        .andExpect(jsonPath("$.confirmedCount").value(12))
        .andExpect(jsonPath("$.correctCount").value(9))
        .andExpect(jsonPath("$.length()").value(5));
  }

  @Test
  @DisplayName("기간 조회에서 from 이나 to 를 빼거나 모양이 틀리면 400 이다")
  void rejectsBadPeriod() throws Exception {
    expectRejected(
        mockMvc.perform(get("/api/v1/risk-forecasts").param("from", "2026-10-01")),
        "보낸 값을 다시 확인해 주세요");
    expectRejected(
        mockMvc.perform(get("/api/v1/risk-forecasts").param("to", "2026-10-31")),
        "보낸 값을 다시 확인해 주세요");
    expectRejected(
        mockMvc.perform(
            get("/api/v1/risk-forecasts").param("from", "10/01").param("to", "2026-10-31")),
        "보낸 값을 다시 확인해 주세요");
  }
}
