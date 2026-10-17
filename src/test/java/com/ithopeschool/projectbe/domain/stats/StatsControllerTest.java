package com.ithopeschool.projectbe.domain.stats;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.domain.stats.dto.RhythmStatsResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소와 응답 모양(JSON 이름·날짜 글자)을 확인합니다.
// 진짜 로직(MissReasonService · RhythmService)은 가짜입니다.
@WebMvcTest(StatsController.class)
class StatsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MissReasonService missReasonService;

  @MockitoBean private RhythmService rhythmService;

  @Test
  @DisplayName("GET /api/v1/stats/rhythm 은 200 과 무너짐·회복, 요일·시간대, 이벤트 다음 날을 돌려준다")
  void returnsRhythm() throws Exception {
    // 응답 모양만 보려고, 작은 재료로 진짜 계산을 돌려 응답을 만듭니다.
    // 10/12(월) 60분 계획에 30분(50%), 밤 11시에 시작 / 10/10 시험 → 다음 날 10/11 은 기록 없음
    LocalDate from = LocalDate.of(2026, 9, 17);
    LocalDate to = LocalDate.of(2026, 10, 16);
    LocalDate monday = LocalDate.of(2026, 10, 12);
    List<RhythmEfficiency.Day> days = List.of(new RhythmEfficiency.Day(monday, 30, 50));
    Instant start = Instant.parse("2026-10-12T14:00:00Z"); // 23:00 (한국 시간)
    RhythmStatsResponse response =
        RhythmStatsResponse.of(
            from,
            to,
            CollapseCycle.of(List.of(new CollapseCycle.Day(monday, 50)), from, to),
            RhythmEfficiency.of(
                days,
                List.of(new RhythmEfficiency.Session(start, start.plusSeconds(30 * 60), 0)),
                from,
                to),
            AfterEventEffect.of(
                days,
                List.of(
                    new AfterEventEffect.Event(
                        LocalDate.of(2026, 10, 10), AfterEventEffect.EventType.EXAM)),
                from,
                to));
    given(rhythmService.getRhythm()).willReturn(response);

    mockMvc
        .perform(get("/api/v1/stats/rhythm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-09-17"))
        .andExpect(jsonPath("$.to").value("2026-10-16"))
        .andExpect(jsonPath("$.collapse.commonDay").doesNotExist())
        .andExpect(jsonPath("$.collapse.events").isEmpty())
        .andExpect(jsonPath("$.collapse.hypothesis").value(true))
        .andExpect(jsonPath("$.recovery.slumpSince").value("2026-10-12"))
        .andExpect(jsonPath("$.byWeekday[0].weekday").value("mon"))
        .andExpect(jsonPath("$.byWeekday[0].averageStudyMinutes").value(30))
        .andExpect(jsonPath("$.byWeekday[0].averageAchievementRate").value(50))
        .andExpect(jsonPath("$.byWeekday[0].dates[0]").value("2026-10-12"))
        .andExpect(jsonPath("$.weekdayVsWeekend.weekday.averageStudyMinutes").value(30))
        .andExpect(jsonPath("$.weekdayVsWeekend.hypothesis").value(true))
        .andExpect(jsonPath("$.byTimeOfDay[3].timeOfDay").value("night"))
        .andExpect(jsonPath("$.byTimeOfDay[3].sessionCount").value(1))
        .andExpect(jsonPath("$.byTimeOfDay[3].pauseRate").value(0))
        .andExpect(jsonPath("$.afterEvents.usual.averageStudyMinutes").value(30))
        .andExpect(jsonPath("$.afterEvents.byEvent[0].event").value("exam"))
        .andExpect(jsonPath("$.afterEvents.byEvent[0].nextDays.dates").isEmpty())
        .andExpect(jsonPath("$.afterEvents.byEvent[0].studyMinutesDiff").doesNotExist())
        .andExpect(jsonPath("$.afterEvents.byEvent[1].event").value("event"));
  }

  @Test
  @DisplayName("리듬·패턴도 로그인하지 않았으면 401")
  void rhythmUnauthorized() throws Exception {
    given(rhythmService.getRhythm())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/stats/rhythm"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("GET /api/v1/stats/miss-reasons 는 200 과 14일 · 30일 결과를 돌려준다")
  void returnsMissReasons() throws Exception {
    MissReasonStatsResponse.Period last14 =
        new MissReasonStatsResponse.Period(
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 16),
            2,
            List.of(
                new MissReasonStatsResponse.Reason(
                    "phone",
                    1,
                    List.of(0, 0, 0, 1, 0, 0, 0),
                    1.0,
                    0,
                    List.of(LocalDate.of(2026, 10, 15)))),
            List.of("phone"));
    MissReasonStatsResponse.Period last30 =
        new MissReasonStatsResponse.Period(
            LocalDate.of(2026, 9, 17), LocalDate.of(2026, 10, 16), 0, List.of(), List.of());
    given(missReasonService.getMissReasons())
        .willReturn(new MissReasonStatsResponse(last14, last30));

    mockMvc
        .perform(get("/api/v1/stats/miss-reasons"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.last14Days.from").value("2026-10-03"))
        .andExpect(jsonPath("$.last14Days.to").value("2026-10-16"))
        .andExpect(jsonPath("$.last14Days.missedDayCount").value(2))
        .andExpect(jsonPath("$.last14Days.topReasons[0]").value("phone"))
        .andExpect(jsonPath("$.last14Days.reasons[0].reason").value("phone"))
        .andExpect(jsonPath("$.last14Days.reasons[0].dayCount").value(1))
        .andExpect(jsonPath("$.last14Days.reasons[0].weekdayCounts[3]").value(1))
        .andExpect(jsonPath("$.last14Days.reasons[0].averageRecoveryDays").value(1.0))
        .andExpect(jsonPath("$.last14Days.reasons[0].notRecoveredCount").value(0))
        .andExpect(jsonPath("$.last14Days.reasons[0].dates[0]").value("2026-10-15"))
        .andExpect(jsonPath("$.last30Days.from").value("2026-09-17"))
        .andExpect(jsonPath("$.last30Days.reasons").isEmpty());
  }

  @Test
  @DisplayName("로그인하지 않았으면 401")
  void unauthorized() throws Exception {
    given(missReasonService.getMissReasons())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/stats/miss-reasons"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }
}
