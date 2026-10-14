package com.ithopeschool.projectbe.domain.thinkingtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 저장·조회·견주기 로직만 확인합니다.
class ThinkingTestServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-15 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ThinkingTestRepository thinkingTestRepository = mock(ThinkingTestRepository.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final ThinkingTestService service =
      new ThinkingTestService(
          currentUserProvider,
          thinkingTestRepository,
          callRecordRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(thinkingTestRepository.save(any()))
        .willAnswer(
            invocation -> {
              ThinkingTest test = invocation.getArgument(0);
              ReflectionTestUtils.setField(test, "id", 3L);
              return test;
            });
  }

  // 8개 습관 점수. planning 만 따로, 나머지는 4.0
  private static List<ThinkingTestSaveRequest.Score> scores(double planning) {
    List<ThinkingTestSaveRequest.Score> scores = new ArrayList<>();
    for (CognitiveBias bias : CognitiveBias.values()) {
      scores.add(
          new ThinkingTestSaveRequest.Score(
              bias.name(), bias == CognitiveBias.PLANNING_FALLACY ? planning : 4.0));
    }
    return scores;
  }

  private static ThinkingTest savedTest(Weakness... weaknesses) {
    Map<CognitiveBias, Double> map = new EnumMap<>(CognitiveBias.class);
    for (CognitiveBias bias : CognitiveBias.values()) {
      map.put(bias, 4.0);
    }
    map.put(CognitiveBias.PLANNING_FALLACY, 6.0);
    EnumSet<Weakness> chips = EnumSet.noneOf(Weakness.class);
    chips.addAll(List.of(weaknesses));
    return new ThinkingTest(USER_ID, NOW, 2, map, chips);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  @DisplayName("저장하면 내 번호·지금 시각과 함께 쌓고, 공부 스타일과 많이 해당하는 습관을 돌려준다")
  void savesAndReturnsTheStyle() {
    ThinkingTestResponse response =
        service.save(new ThinkingTestSaveRequest(2, scores(6.0), List.of("GAME", "SLEEP")));

    ArgumentCaptor<ThinkingTest> saved = ArgumentCaptor.forClass(ThinkingTest.class);
    verify(thinkingTestRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getTakenAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getWeaknesses())
        .containsExactlyInAnyOrder(Weakness.GAME, Weakness.SLEEP);

    assertThat(response.id()).isEqualTo("3");
    // 계획할 때: (6.0 + 4.0) / 2 = 5.0 → 낙관, 나머지 균형
    assertThat(response.styleName()).isEqualTo("낙관 · 균형 · 균형 · 균형");
    assertThat(response.topHabits()).containsExactly("PLANNING_FALLACY");
    assertThat(response.weaknesses()).extracting(c -> c.label()).containsExactly("게임", "잠·늦잠");
  }

  @Test
  @DisplayName("같은 습관을 두 번 보내면 400 이고 저장하지 않는다")
  void duplicateHabit() {
    List<ThinkingTestSaveRequest.Score> scores = scores(6.0);
    scores.set(7, new ThinkingTestSaveRequest.Score("PLANNING_FALLACY", 5.0));

    assertError(
        () -> service.save(new ThinkingTestSaveRequest(2, scores, null)),
        ErrorCode.VALIDATION_ERROR,
        "습관 점수(scores)는 습관마다 하나씩 보내 주세요");
    verify(thinkingTestRepository, never()).save(any());
  }

  @Test
  @DisplayName("가장 최근 결과가 없으면 404")
  void noLatest() {
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());

    assertError(service::getLatest, ErrorCode.NOT_FOUND, "아직 생각 습관 테스트를 하지 않았어요");
  }

  @Test
  @DisplayName("생각 vs 현실: 최근 14일 통화(마무리했고 위기 표현 없는 것)로 견주고, 문장을 돌려준다")
  void gap() {
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(savedTest(Weakness.GAME)));
    CallRecord crisis = new CallRecord(USER_ID, LocalDate.of(2026, 10, 14), NOW, null);
    crisis.finishWithCrisis(NOW);
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, LocalDate.of(2026, 10, 2)))
        .willReturn(
            List.of(
                finished(MissReason.TOO_MUCH),
                finished(MissReason.TOO_MUCH),
                crisis,
                finished(MissReason.TIRED)));

    ThinkingGapResponse gap = service.getGap(null);

    assertThat(gap.days()).isEqualTo(14);
    assertThat(gap.callCount()).isEqualTo(3);
    assertThat(gap.enoughData()).isTrue();
    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.verdict())
        .containsExactly("PHONE:SMALLER", "TOO_MUCH:BIGGER");
    // 테스트에서 높았던 계획 오류는 통화에서 아직 안 보임
    assertThat(gap.habits())
        .extracting(h -> h.habit() + ":" + h.verdict())
        .containsExactly("PLANNING_FALLACY:SMALLER");
    assertThat(gap.sentences()).first().asString().startsWith("꼽지 않았던 '계획 과다'");
  }

  @Test
  @DisplayName("생각 vs 현실의 기간은 7 ~ 60일이고, 테스트를 안 했으면 404")
  void gapErrors() {
    assertError(() -> service.getGap(6), ErrorCode.VALIDATION_ERROR, "days 는 7 ~ 60 이에요");
    assertError(() -> service.getGap(61), ErrorCode.VALIDATION_ERROR, "days 는 7 ~ 60 이에요");
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());
    assertError(() -> service.getGap(14), ErrorCode.NOT_FOUND, "아직 생각 습관 테스트를 하지 않았어요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 셋 다 401")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(
        () -> service.save(new ThinkingTestSaveRequest(2, scores(4.0), null)),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
    assertError(service::getLatest, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(() -> service.getGap(14), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
  }

  private static CallRecord finished(MissReason reason) {
    CallRecord record = new CallRecord(USER_ID, LocalDate.of(2026, 10, 14), NOW, null);
    record.finish(NOW, "요약", null, null, null, reason, null, null, null);
    return record;
  }
}
