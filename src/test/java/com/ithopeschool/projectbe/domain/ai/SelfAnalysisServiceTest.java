package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.AnalysisRequest;
import com.ithopeschool.projectbe.domain.ai.dto.AnalysisResponse;
import com.ithopeschool.projectbe.domain.call.CallRecordService;
import com.ithopeschool.projectbe.domain.call.dto.BiasHabitResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: 통화 기록(CallRecordService)과 AI(AiClient)를 가짜로 바꿔서
// "서버 DB 의 생각 습관을 더하는지" 와 "AI 에게 무엇을 주는지" 를 확인합니다.
class SelfAnalysisServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CallRecordService callRecordService = mock(CallRecordService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final SelfAnalysisService service =
      new SelfAnalysisService(currentUserProvider, callRecordService, aiClient);

  private static final AnalysisRequest.Fact SLOT =
      new AnalysisRequest.Fact("slotRate", "tendency", "시간대별 실행률", "아침 95% · 밤 63%", "카드 64번");

  private static BiasHabitResponse habit(String label, int days) {
    return new BiasHabitResponse(
        "PLANNING_FALLACY", label, "설명", "해결", days, LocalDate.parse("2026-10-16"), "근거 문장");
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(callRecordService.getHabits()).willReturn(List.of());
  }

  @Test
  @DisplayName("통화 생각 습관을 2개까지 더해 AI 에게 주고, 검사한 분석을 돌려준다 (근거 문장은 주지 않는다)")
  void analyzesWithServerFacts() {
    given(callRecordService.getHabits())
        .willReturn(List.of(habit("계획 오류", 4), habit("상황 탓하기", 3), habit("미래의 나 믿기", 3)));
    given(
            aiClient.askLong(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(SelfAnalysis.Answer.class)))
        .willReturn(
            Optional.of(
                new SelfAnalysis.Answer(
                    "아침에 더 잘 되는 편이야",
                    List.of(
                        new SelfAnalysis.Answer.Section(
                            "distortion",
                            "계획 오류가 자주 보여",
                            "최근 14일 중 4일이야. 금방 끝날 거라 볼 수 있어.",
                            List.of("callHabit1"))),
                    null)));

    AnalysisResponse response = service.analyze(new AnalysisRequest(List.of(SLOT, SLOT)));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.serverFacts())
        .extracting(AnalysisRequest.Fact::key)
        .containsExactly("callHabit1", "callHabit2");
    assertThat(response.serverFacts().get(0).value()).isEqualTo("계획 오류 · 최근 14일 중 4일");
    assertThat(response.analysis().sections().get(0).evidenceKeys()).containsExactly("callHabit1");
    assertThat(response.analysis().nextExperiment()).isNull();

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient).askLong(eq(USER_ID), eq(GptUsageKind.ASSIST), question.capture(), any());
    String input = question.getValue().input();
    assertThat(input).contains("[distortion] callHabit2 | 통화에서 자주 보인 생각 습관: 상황 탓하기");
    assertThat(input).doesNotContain("미래의 나 믿기");
    assertThat(input).doesNotContain("근거 문장");
    // 같은 key 는 한 번만
    assertThat(input.split("slotRate", -1)).hasSize(2);
  }

  @Test
  @DisplayName("AI 가 못 쓰면 analysis 는 null 이지만 서버가 더한 숫자는 온다")
  void nullAnalysisKeepsServerFacts() {
    given(callRecordService.getHabits()).willReturn(List.of(habit("계획 오류", 3)));
    given(aiClient.askLong(any(), any(), any(), eq(SelfAnalysis.Answer.class)))
        .willReturn(Optional.empty());

    AnalysisResponse response = service.analyze(new AnalysisRequest(List.of(SLOT)));

    assertThat(response.analysis()).isNull();
    assertThat(response.serverFacts()).hasSize(1);
  }

  @Test
  @DisplayName("값 안에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisShowsHelp() {
    AnalysisRequest request =
        new AnalysisRequest(
            List.of(
                new AnalysisRequest.Fact(
                    "cardRate", "tendency", "카드별 실행률", "죽고 싶다 카드 10%", "카드 3번")));

    AnalysisResponse response = service.analyze(request);

    assertThat(response.showHelp()).isTrue();
    assertThat(response.analysis()).isNull();
    verifyNoInteractions(aiClient);
  }
}
