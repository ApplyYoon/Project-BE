package com.ithopeschool.projectbe.domain.cardresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.cardday.CardDayMark;
import com.ithopeschool.projectbe.domain.cardday.CardDayMarkRepository;
import com.ithopeschool.projectbe.domain.cardresult.dto.CardDayResultResponse;
import com.ithopeschool.projectbe.domain.cardresult.dto.PlanPatternResponse;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.restday.RestDay;
import com.ithopeschool.projectbe.domain.restday.RestDayRepository;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 카드 결과 조회가 카드 · 공부 기록 · 표시 · 휴식 날을 카드 하루치로 잘 묶는지 확인합니다. DB 는 가짜로 바꿉니다. (이슈 #229)
// 판정 규칙 자체는 CardResultJudgeTest 가 확인합니다.
class CardResultServiceTest {

  private static final Long ME = 1L;
  // 2026-10-12 는 월요일입니다.
  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
  // 지금 = 10-12(월) 21:00 (한국 시간)
  private static final Instant NOW = kst(12, 21, 0);

  private CardRepository cardRepository;
  private StudySessionRepository studySessionRepository;
  private CardDayMarkRepository cardDayMarkRepository;
  private RestDayRepository restDayRepository;
  private CardResultService service;

  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  // 체크리스트 i1 · i2 두 개짜리 60분 카드
  private static Card card(long id, LocalDate date, List<Integer> repeatDays, LocalTime start) {
    Card card =
        new Card(
            ME,
            "카드 " + id,
            date,
            60,
            List.of(new ChecklistItem("i1", "하나"), new ChecklistItem("i2", "둘")),
            kst(1, 9, 0));
    card.changeRepeatDays(repeatDays);
    card.changeStartTime(start);
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }

  // 카드로 켠 타이머 기록. checkedKeys 는 종료 팝업에서 체크한 항목
  private static StudySession session(
      Long cardId, Instant from, Instant to, boolean endedEarly, String... checkedKeys) {
    StudySession session =
        new StudySession(ME, null, from, to, 0, 0, null, 60, null, PlanKind.STUDY);
    session.recordCard(cardId, SessionMode.NORMAL, endedEarly);
    Map<String, Instant> checked = new LinkedHashMap<>();
    for (String key : checkedKeys) {
      checked.put(key, to);
    }
    session.recordEndReport(checked, null, null, null, null, Set.of());
    return session;
  }

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    cardRepository = mock(CardRepository.class);
    studySessionRepository = mock(StudySessionRepository.class);
    cardDayMarkRepository = mock(CardDayMarkRepository.class);
    restDayRepository = mock(RestDayRepository.class);
    service =
        new CardResultService(
            currentUserProvider,
            cardRepository,
            studySessionRepository,
            cardDayMarkRepository,
            restDayRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 처음에는 아무것도 없습니다. 테스트마다 필요한 것만 채웁니다.
    given(cardRepository.findCandidatesBetween(any(), any(), any())).willReturn(List.of());
    given(cardRepository.findByUserIdAndIdIn(any(), any())).willReturn(List.of());
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(List.of());
    given(
            cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of());
    given(restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(any(), any(), any()))
        .willReturn(List.of());
  }

  private static void assertError(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("10-12 의 카드 하루치: 실행 · 미처리 · 미룸(옮겨진 1회성 카드) · 아직 모름 을 시작 시각 순서로 돌려준다")
  void results() {
    Card repeat = card(7L, LocalDate.of(2026, 10, 1), List.of(1, 3), LocalTime.of(19, 0));
    Card untouched = card(8L, OCT_12, List.of(), LocalTime.of(19, 0));
    Card later = card(9L, OCT_12, List.of(), LocalTime.of(20, 30));
    // 10-12 에서 10-13 20:00 으로 미룬 1회성 카드 — 카드 자체가 10-13 으로 옮겨져서 후보에 없습니다.
    Card moved = card(10L, LocalDate.of(2026, 10, 13), List.of(), LocalTime.of(20, 0));
    given(cardRepository.findCandidatesBetween(ME, OCT_12, OCT_12))
        .willReturn(List.of(repeat, untouched, later));
    given(cardRepository.findByUserIdAndIdIn(ME, Set.of(10L))).willReturn(List.of(moved));
    given(
            cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
                ME, OCT_12, OCT_12))
        .willReturn(
            List.of(
                CardDayMark.postponed(
                    ME,
                    10L,
                    OCT_12,
                    "이유",
                    LocalDate.of(2026, 10, 13),
                    LocalTime.of(20, 0),
                    false,
                    kst(12, 18, 0))));

    StudySession old = new StudySession(ME, 3L, kst(12, 10, 0), kst(12, 11, 0), 0);
    given(
            studySessionRepository.findStartedBetween(
                ME, DayRule.startOf(OCT_12), DayRule.startOf(OCT_12.plusDays(1))))
        .willReturn(
            List.of(
                // 카드 7: 19:05 ~ 19:50 (45분), i1 만 체크
                session(7L, kst(12, 19, 5), kst(12, 19, 50), false, "i1"),
                // 카드 없이 켠 옛 기록은 카드 판정에 들어가지 않습니다.
                old));

    List<CardDayResultResponse> results = service.getResults(OCT_12, OCT_12);

    // 시작 시각 순: 7(19:00) · 8(19:00) · 10(20:00, 옮긴 뒤 시각) · 9(20:30)
    assertThat(results)
        .extracting(CardDayResultResponse::cardId)
        .containsExactly("7", "8", "10", "9");
    assertThat(results.get(0))
        .isEqualTo(
            new CardDayResultResponse("7", OCT_12, "done", 0.75, "execution", 0.5, -1, true));
    assertThat(results.get(1))
        .isEqualTo(new CardDayResultResponse("8", OCT_12, "unhandled", 0, null, null, null, null));
    assertThat(results.get(2))
        .isEqualTo(new CardDayResultResponse("10", OCT_12, "postponed", 0, null, null, null, null));
    // 20:30 ~ 21:30 카드는 지금(21:00) 아직 끝나지 않았고 세션도 없어서 판정 전입니다.
    assertThat(results.get(3))
        .isEqualTo(new CardDayResultResponse("9", OCT_12, null, 0, null, null, null, null));
  }

  @Test
  @DisplayName("반복 카드는 기간 안의 그 요일마다 하루치가 생기고, 기록은 시작한 날(새벽 4시 기준)에 붙는다")
  void repeatCardAndDayRule() {
    // 월(10-05) · 화(10-06) · 수(10-07) 중 월 · 수 에만 하는 23:30 카드
    Card night = card(7L, LocalDate.of(2026, 10, 1), List.of(1, 3), LocalTime.of(23, 30));
    LocalDate oct5 = LocalDate.of(2026, 10, 5);
    LocalDate oct7 = LocalDate.of(2026, 10, 7);
    given(cardRepository.findCandidatesBetween(ME, oct5, oct7)).willReturn(List.of(night));
    given(
            studySessionRepository.findStartedBetween(
                ME, DayRule.startOf(oct5), DayRule.startOf(oct7.plusDays(1))))
        .willReturn(
            // 10-06 00:10 에 시작한 기록은 10-05(월) 의 기록입니다. (새벽 4시 전)
            List.of(session(7L, kst(6, 0, 10), kst(6, 1, 10), false, "i1", "i2")));

    List<CardDayResultResponse> results = service.getResults(oct5, oct7);

    // 10-06(화) 은 하는 날이 아니라서 없습니다.
    assertThat(results).extracting(CardDayResultResponse::date).containsExactly(oct5, oct7);
    // 23:30 예정인데 00:10 에 시작 → 40분 늦음
    assertThat(results.get(0).result()).isEqualTo("late");
    assertThat(results.get(0).planAxis()).isEqualTo("complete");
    assertThat(results.get(0).startedOnTime()).isFalse();
    // 10-07 은 기록이 없고 카드 시간이 지났습니다.
    assertThat(results.get(1).result()).isEqualTo("unhandled");
  }

  @Test
  @DisplayName("휴식 날은 그날 모든 카드가 rest 다 (실패 표시가 있어도)")
  void restDay() {
    Card first = card(7L, OCT_12, List.of(), LocalTime.of(9, 0));
    Card second = card(8L, OCT_12, List.of(), LocalTime.of(10, 0));
    given(cardRepository.findCandidatesBetween(ME, OCT_12, OCT_12))
        .willReturn(List.of(first, second));
    given(
            cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
                ME, OCT_12, OCT_12))
        .willReturn(List.of(CardDayMark.failed(ME, 8L, OCT_12, MissReason.SICK, null, false, NOW)));
    given(restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(ME, OCT_12, OCT_12))
        .willReturn(List.of(new RestDay(ME, OCT_12, NOW)));

    assertThat(service.getResults(OCT_12, OCT_12))
        .extracting(CardDayResultResponse::result)
        .containsExactly("rest", "rest");
  }

  @Test
  @DisplayName("노는 기록은 카드 판정에 넣지 않는다")
  void playSessionIgnored() {
    Card card = card(7L, OCT_12, List.of(), LocalTime.of(19, 0));
    given(cardRepository.findCandidatesBetween(ME, OCT_12, OCT_12)).willReturn(List.of(card));
    StudySession play =
        new StudySession(
            ME, null, kst(12, 19, 0), kst(12, 20, 0), 0, 0, null, null, null, PlanKind.PLAY);
    play.recordCard(7L, SessionMode.NORMAL, false);
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(List.of(play));

    assertThat(service.getResults(OCT_12, OCT_12).get(0).result()).isEqualTo("unhandled");
  }

  @Test
  @DisplayName("시작이 끝보다 늦거나 31일을 넘으면 400")
  void range() {
    assertError(() -> service.getResults(OCT_12, OCT_12.minusDays(1)), "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertError(
        () -> service.getResults(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
        "한 번에 31일까지만 조회할 수 있어요");
    assertThat(service.getResults(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isEmpty();
  }

  @Test
  @DisplayName("계획 패턴: 목표 12 카드의 최근 4주 중 세션이 있던 날만 세고, 5번이면 판정한다")
  void planPattern() {
    LocalDate from = OCT_12.minusDays(27);
    // 목표 12 에 연결한 매일 카드, 목표 없는 매일 카드
    Card goalCard = card(7L, from, List.of(0, 1, 2, 3, 4, 5, 6), LocalTime.of(19, 0));
    goalCard.changeGoalId(12L);
    Card otherCard = card(8L, from, List.of(0, 1, 2, 3, 4, 5, 6), LocalTime.of(19, 0));
    given(cardRepository.findCandidatesBetween(ME, from, OCT_12))
        .willReturn(List.of(goalCard, otherCard));

    List<StudySession> sessions = new ArrayList<>();
    // 목표 카드: 10-07 ~ 10-11 다섯 날, 체크 2/2 · 2/2 · 1/2 · 1/2 · 2/2 → 평균 0.8
    String[][] checks = {{"i1", "i2"}, {"i1", "i2"}, {"i1"}, {"i2"}, {"i1", "i2"}};
    for (int i = 0; i < checks.length; i++) {
      int day = 7 + i;
      sessions.add(session(7L, kst(day, 19, 0), kst(day, 20, 0), false, checks[i]));
    }
    // 목표 없는 카드의 기록은 세지 않습니다.
    sessions.add(session(8L, kst(10, 19, 0), kst(10, 20, 0), false));
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);

    PlanPatternResponse response = service.getPlanPattern(12L);

    assertThat(response.goalId()).isEqualTo("12");
    assertThat(response.from()).isEqualTo(from);
    assertThat(response.to()).isEqualTo(OCT_12);
    assertThat(response.sampleCount()).isEqualTo(5);
    assertThat(response.checklistRate()).isEqualTo(0.8);
    assertThat(response.pattern()).isEqualTo("fit");
  }

  @Test
  @DisplayName("계획 패턴: 휴식 날은 빼고, 5번보다 적으면 pattern 은 null · 하나도 없으면 평균도 null")
  void planPatternTooFew() {
    LocalDate from = OCT_12.minusDays(27);
    Card goalCard = card(7L, from, List.of(0, 1, 2, 3, 4, 5, 6), LocalTime.of(19, 0));
    goalCard.changeGoalId(12L);
    given(cardRepository.findCandidatesBetween(ME, from, OCT_12)).willReturn(List.of(goalCard));
    List<StudySession> sessions = new ArrayList<>();
    for (int day = 7; day <= 11; day++) {
      sessions.add(session(7L, kst(day, 19, 0), kst(day, 20, 0), false, "i1", "i2"));
    }
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);
    // 10-09 는 휴식 날 → 4번만 남음
    given(restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(ME, from, OCT_12))
        .willReturn(List.of(new RestDay(ME, LocalDate.of(2026, 10, 9), NOW)));

    PlanPatternResponse response = service.getPlanPattern(12L);
    assertThat(response.sampleCount()).isEqualTo(4);
    assertThat(response.checklistRate()).isEqualTo(1.0);
    assertThat(response.pattern()).isNull();

    PlanPatternResponse none = service.getPlanPattern(99L);
    assertThat(none.sampleCount()).isZero();
    assertThat(none.checklistRate()).isNull();
    assertThat(none.pattern()).isNull();
  }
}
