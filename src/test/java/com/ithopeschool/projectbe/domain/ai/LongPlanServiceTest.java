package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanReviseRequest;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: AI 를 가짜로 바꿔서 날짜 검사 · 위기 표현 · 못 만듦 · 만든 계획 · 고친 계획을 봅니다.
class LongPlanServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-19(월) 12:00 (한국 시간)
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
  // 1x1 크기의 아주 작은 jpeg 대신 base64 로 읽히는 글자
  private static final String IMAGE = "data:image/jpeg;base64,AAAA";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final LongPlanService service = new LongPlanService(currentUserProvider, aiClient, CLOCK);

  private static LongPlanRequest request(String goal, LocalDate endDate, String image) {
    return new LongPlanRequest(goal, endDate, null, null, null, null, image, null);
  }

  private static LongPlan.Answer answer(String reply) {
    return new LongPlan.Answer(
        false,
        false,
        reply,
        List.of(
            new LongPlan.TopicItem("미적분 1단원 개념", 60, List.of("교과서 읽기")),
            new LongPlan.TopicItem("미적분 2단원 개념", 60, List.of())));
  }

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("시험 날이 오늘이거나 30일보다 멀거나, 시작 날이 지났거나, 공부할 날이 없으면 400")
  void rejectsBadDates() {
    assertThatThrownBy(() -> service.make(request("중간고사", TODAY, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("시험 · 마감 날짜는 내일부터 30일 안으로 보내 주세요");
    assertThatThrownBy(() -> service.make(request("중간고사", TODAY.plusDays(31), null)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.make(
                    new LongPlanRequest(
                        "중간고사",
                        TODAY.plusDays(5),
                        TODAY.minusDays(1),
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("시작 날짜는 오늘부터 시험 · 마감 전날까지예요");
    // 토 · 일만 있는 기간인데 주말을 쉼
    assertThatThrownBy(
            () ->
                service.make(
                    new LongPlanRequest(
                        "중간고사",
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 10, 24),
                        null,
                        null,
                        List.of("SAT", "SUN"),
                        null,
                        null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("공부할 날이 없어요. 쉬는 요일을 줄여 주세요");
    assertThatThrownBy(
            () -> service.make(request("중간고사", TODAY.plusDays(5), "data:text/plain;base64,AAAA")))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("목표에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void crisisSkipsAi() {
    assertThat(service.make(request("시험 다 망하면 죽고 싶어", TODAY.plusDays(5), null)))
        .isEqualTo(LongPlanResponse.help());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("사진 없이 만들기: 오래 기다리는 AI 로 할 일을 받고, 코드가 날짜별로 나눈다 (기본 하루 60분 · 19:00)")
  void makesWithoutImage() {
    given(aiClient.askLong(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(LongPlan.Answer.class)))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(answer("개념부터 나눴어.")));

    assertThat(service.make(request("중간고사 미적분", TODAY.plusDays(3), null)))
        .isEqualTo(LongPlanResponse.notMade());
    LongPlanResponse made = service.make(request("중간고사 미적분", TODAY.plusDays(3), null));

    assertThat(made.reply()).isEqualTo("개념부터 나눴어.");
    assertThat(made.plan().startDate()).isEqualTo(TODAY);
    assertThat(made.plan().dailyMinutes()).isEqualTo(60);
    assertThat(made.plan().startTime()).isEqualTo("19:00");
    assertThat(made.plan().topics()).hasSize(2);
    // 공부 날 3일 → 할 일 2일 + 총복습 1일
    assertThat(made.drafts())
        .extracting(DayPlanRevise.Card::title)
        .containsExactly("미적분 1단원 개념", "미적분 2단원 개념", "총복습");
    assertThat(made.overflow()).isFalse();
    verify(aiClient, never()).askWithImage(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("사진과 만들기: 사진과 함께 AI 를 부르고, 공부 자료가 아니면 plan: null, AI 가 위기라고 하면 showHelp")
  void makesWithImage() {
    given(
            aiClient.askWithImage(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(IMAGE), eq(LongPlan.Answer.class)))
        .willReturn(Optional.of(new LongPlan.Answer(false, true, "공부 자료가 아니야", List.of())))
        .willReturn(Optional.of(new LongPlan.Answer(true, false, null, List.of())))
        .willReturn(Optional.of(answer("목차대로 나눴어.")));

    assertThat(service.make(request("중간고사", TODAY.plusDays(3), IMAGE)))
        .isEqualTo(LongPlanResponse.notMade());
    assertThat(service.make(request("중간고사", TODAY.plusDays(3), IMAGE)))
        .isEqualTo(LongPlanResponse.help());
    assertThat(service.make(request("중간고사", TODAY.plusDays(3), IMAGE)).reply())
        .isEqualTo("목차대로 나눴어.");
  }

  @Test
  @DisplayName("고치기: 지난 계획은 400, AI 가 못 고치면 plan: null, 고치면 다시 나눈 카드")
  void revises() {
    LongPlanOutline plan =
        new LongPlanOutline(
            "중간고사",
            TODAY,
            TODAY.plusDays(7),
            60,
            "19:00",
            List.of(),
            List.of(new LongPlanOutline.Topic("미적분 1단원", 120, List.of())));
    LongPlanOutline past =
        new LongPlanOutline(
            "중간고사", TODAY.minusDays(5), TODAY, 60, "19:00", List.of(), plan.topics());
    assertThatThrownBy(() -> service.revise(new LongPlanReviseRequest(past, "줄여 줘")))
        .isInstanceOf(ApiException.class)
        .hasMessage("이미 지난 계획이에요. 새로 만들어 주세요");

    given(
            aiClient.askLong(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(LongPlan.ReviseAnswer.class)))
        .willReturn(Optional.empty())
        .willReturn(
            Optional.of(
                new LongPlan.ReviseAnswer(
                    "주말은 뺐어.", null, null, List.of("SAT", "SUN"), null, null)));

    assertThat(service.revise(new LongPlanReviseRequest(plan, "주말은 빼 줘")))
        .isEqualTo(LongPlanResponse.notMade());
    LongPlanResponse revised = service.revise(new LongPlanReviseRequest(plan, "주말은 빼 줘"));
    assertThat(revised.reply()).isEqualTo("주말은 뺐어.");
    assertThat(revised.plan().restDays()).containsExactly("SAT", "SUN");
    assertThat(revised.drafts())
        .extracting(card -> LongPlanScheduler.dayCodeOf(card.date()))
        .doesNotContain("SAT", "SUN");
  }

  @Test
  @DisplayName("고치기: 요청에 위기 표현이 있으면 AI 를 부르지 않고 showHelp")
  void reviseCrisisSkipsAi() {
    LongPlanOutline plan =
        new LongPlanOutline(
            "중간고사",
            TODAY,
            TODAY.plusDays(7),
            60,
            "19:00",
            List.of(),
            List.of(new LongPlanOutline.Topic("미적분 1단원", 120, List.of())));

    assertThat(service.revise(new LongPlanReviseRequest(plan, "그냥 죽고 싶어")))
        .isEqualTo(LongPlanResponse.help());
    verifyNoInteractions(aiClient);
  }
}
