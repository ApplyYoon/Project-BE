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
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ExamThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingGapResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestResponse;
import com.ithopeschool.projectbe.domain.thinkingtest.dto.ThinkingTestSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
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
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final ThinkingTestService service =
      new ThinkingTestService(
          currentUserProvider,
          thinkingTestRepository,
          callRecordRepository,
          studySessionRepository,
          studyPlanRepository,
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
  @DisplayName("로그인하지 않았으면 넷 다 401")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(
        () -> service.save(new ThinkingTestSaveRequest(2, scores(4.0), null)),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
    assertError(service::getLatest, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(() -> service.getGap(14), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> service.getExamGap(LocalDate.of(2026, 10, 20)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
  }

  @Test
  @DisplayName("시험 전 2주: 타이머의 그만둔 이유 + 통화의 '어제' 이유를 날짜별로 모으고, 노는 기록·노는 계획·위기 통화는 뺀다")
  void examGap() {
    // 테스트: 약점 '게임', 계획 오류 6.0 → 계획할 때 (6.0 + 4.0) / 2 = 5.0 → 낙관
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(savedTest(Weakness.GAME)));
    LocalDate exam = LocalDate.of(2026, 10, 20);
    // 기간 10월 6일 ~ 19일
    given(
            studySessionRepository.findStartedBetween(
                USER_ID,
                DayRule.startOf(LocalDate.of(2026, 10, 6)),
                DayRule.startOf(LocalDate.of(2026, 10, 20))))
        .willReturn(
            List.of(
                session("2026-10-07T10:00:00Z", 20, MissReason.TOO_MUCH, PlanKind.STUDY),
                session("2026-10-08T10:00:00Z", 90, MissReason.PHONE, PlanKind.PLAY),
                session("2026-10-09T10:00:00Z", 30, MissReason.TOO_MUCH, PlanKind.STUDY)));
    CallRecord crisis = new CallRecord(USER_ID, LocalDate.of(2026, 10, 12), NOW, null);
    crisis.finishWithCrisis(NOW);
    // 통화는 기간 첫날의 다음 날(7일) ~ 시험 날(20일)
    given(
            callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
                USER_ID, LocalDate.of(2026, 10, 7), exam))
        .willReturn(
            List.of(
                finishedOn(LocalDate.of(2026, 10, 10), MissReason.TOO_MUCH),
                crisis,
                finishedOn(exam, MissReason.TIRED)));
    List<StudyPlan> plans = new ArrayList<>();
    for (int day = 6; day <= 10; day++) {
      plans.add(new StudyPlan(USER_ID, 1L, LocalDate.of(2026, 10, day), "수학", 60));
    }
    plans.add(
        new StudyPlan(USER_ID, null, LocalDate.of(2026, 10, 11), "게임", 600, null, PlanKind.PLAY));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 19)))
        .willReturn(plans);

    ExamThinkingGapResponse gap = service.getExamGap(exam);

    // 이유가 나온 날: 7일(타이머) · 9일(타이머 + 10일 통화의 어제 = 하루로) · 19일(20일 통화의 어제)
    assertThat(gap.reasonDays()).isEqualTo(3);
    assertThat(gap.enoughData()).isTrue();
    assertThat(gap.reasons())
        .extracting(r -> r.reason() + ":" + r.actualCount() + ":" + r.verdict())
        .containsExactly("PHONE:0:SMALLER", "TOO_MUCH:2:BIGGER");
    // 공부 계획 5개 300분, 공부 기록 20 + 30 = 50분 → 16% (노는 계획 600분·노는 기록 90분은 빼고)
    assertThat(gap.plan().style()).isEqualTo("낙관");
    assertThat(gap.plan().planRate()).isEqualTo(16);
    assertThat(gap.plan().planCount()).isEqualTo(5);
    assertThat(gap.plan().verdict()).isEqualTo("MATCHED");
  }

  @Test
  @DisplayName("시험 날짜가 1970-01-01 ~ 9999-12-31 밖이면 400, 테스트를 안 했으면 404")
  void examGapErrors() {
    String message = "examDate 는 1970-01-01 부터 9999-12-31 사이로 보내 주세요";
    assertError(
        () -> service.getExamGap(LocalDate.of(1969, 12, 31)), ErrorCode.VALIDATION_ERROR, message);
    assertError(
        () -> service.getExamGap(LocalDate.of(10000, 1, 1)), ErrorCode.VALIDATION_ERROR, message);
    given(thinkingTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());
    assertError(
        () -> service.getExamGap(LocalDate.of(2026, 10, 20)),
        ErrorCode.NOT_FOUND,
        "아직 생각 습관 테스트를 하지 않았어요");
  }

  // startedAt 에 시작해서 minutes 분 동안 한 기록 (일시정지 없음, 목표 50분)
  private static StudySession session(
      String startedAt, int minutes, MissReason stopReason, PlanKind kind) {
    Instant start = Instant.parse(startedAt);
    return new StudySession(
        USER_ID,
        kind == PlanKind.STUDY ? 1L : null,
        start,
        start.plusSeconds(minutes * 60L),
        0,
        0,
        null,
        50,
        stopReason,
        kind);
  }

  private static CallRecord finishedOn(LocalDate callDate, MissReason reason) {
    CallRecord record = new CallRecord(USER_ID, callDate, NOW, null);
    record.finish(NOW, "요약", null, null, null, reason, null, null, null);
    return record;
  }

  private static CallRecord finished(MissReason reason) {
    CallRecord record = new CallRecord(USER_ID, LocalDate.of(2026, 10, 14), NOW, null);
    record.finish(NOW, "요약", null, null, null, reason, null, null, null);
    return record;
  }
}
