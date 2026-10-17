package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.FailCauseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse.Option;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.stats.MissReasonService;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: 실패 원인 분석(MissReasonService)과 AI(AiClient)를 가짜로 바꿔서
// "DB 의 최근 30일 원인으로 고르는지" 와 "AI 에게 무엇을 주는지" 를 확인합니다.
class FailCauseServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate FROM = LocalDate.parse("2026-09-17");
  private static final LocalDate TO = LocalDate.parse("2026-10-16");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final MissReasonService missReasonService = mock(MissReasonService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final FailCauseService service =
      new FailCauseService(currentUserProvider, missReasonService, aiClient);

  // 최근 30일 원인. pairs: "tired", 5, "event", 3 … (날 수가 많은 것부터)
  private void last30Days(Object... pairs) {
    List<MissReasonStatsResponse.Reason> reasons = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      reasons.add(
          new MissReasonStatsResponse.Reason(
              (String) pairs[i],
              (Integer) pairs[i + 1],
              List.of(0, 0, 0, 0, 0, 0, 0),
              null,
              0,
              List.of()));
    }
    MissReasonStatsResponse.Period empty =
        new MissReasonStatsResponse.Period(FROM, TO, 0, List.of(), List.of());
    MissReasonStatsResponse.Period period =
        new MissReasonStatsResponse.Period(FROM, TO, 10, reasons, List.of());
    given(missReasonService.getMissReasons())
        .willReturn(new MissReasonStatsResponse(empty, period));
  }

  private void aiAnswers(String... texts) {
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(FailCauseOptions.Answer.class)))
        .willReturn(Optional.of(new FailCauseOptions.Answer(List.of(texts))));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("최근 30일 원인으로 3개를 고르고, AI 가 쓴 장면을 붙여 돌려준다. 내 행동 원인은 inner: true")
  void suggests() {
    last30Days("tired", 5, "event", 3, "tooMuch", 2, "phone", 1);
    aiAnswers("학원 갔다 와서 졸림", "친구 연락에 나가기", "문제 막힐 때 폰부터 집기");

    FailCauseResponse response = service.suggest(new FailCauseRequest("수학 문제집"));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.options())
        .containsExactly(
            new Option("tired", "학원 갔다 와서 졸림", false),
            new Option("event", "친구 연락에 나가기", false),
            new Option("phone", "문제 막힐 때 폰부터 집기", true));
    // AI 에게 간 입력 글 전체 (카드 제목과 원인 이름 · 날 수뿐)
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(FailCauseOptions.Answer.class));
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            카드 제목: 수학 문제집
            원인 (이 순서대로 하나씩):
            1. 피곤·수면 (최근 30일 중 5일)
            2. 약속·행사 (최근 30일 중 3일)
            3. 폰·SNS (최근 30일 중 1일)""");
  }

  @Test
  @DisplayName("기록이 모자라면 AI 를 부르지 않고 options: null 이다 (FE 는 주관식)")
  void notEnoughHistory() {
    last30Days("tired", 2);

    assertThat(service.suggest(new FailCauseRequest("수학 문제집")))
        .isEqualTo(FailCauseResponse.of(null));
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 쓸 수 없는 답이면 options: null 이다")
  void aiUnusable() {
    last30Days("phone", 4);
    given(aiClient.ask(any(), any(), any(), eq(FailCauseOptions.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest(new FailCauseRequest("수학 문제집")).options()).isNull();

    aiAnswers("폰부터 집기", "졸림");
    assertThat(service.suggest(new FailCauseRequest("수학 문제집")).options()).isNull();
  }

  @Test
  @DisplayName("카드 제목에 위기 표현이 있으면 DB 와 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    assertThat(service.suggest(new FailCauseRequest("그냥 죽고 싶다")))
        .isEqualTo(FailCauseResponse.help());
    verifyNoInteractions(missReasonService, aiClient);
  }
}
