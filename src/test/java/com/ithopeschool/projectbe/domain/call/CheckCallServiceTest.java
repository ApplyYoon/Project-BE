package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.call.dto.CheckCallResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 확인 통화 문(CheckCall · CheckCallService)을 가짜 Repository 로 확인합니다.
class CheckCallServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-18 저녁 8시(한국 시간)입니다. 오늘 날짜는 2026-10-18 입니다.
  private static final Instant NOW = Instant.parse("2026-10-18T11:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  // 저녁 7시(한국 시간)
  private static final Instant SEVEN_PM = Instant.parse("2026-10-18T10:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CardRepository cardRepository = mock(CardRepository.class);

  private final CheckCallService service =
      new CheckCallService(
          currentUserProvider,
          studySessionRepository,
          cardRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  // 저녁 7시에 시작해 minutes 분 공부한 기록 (일시정지 없음)
  private static StudySession session(int minutes, SessionMode mode, PlanKind kind, Long cardId) {
    StudySession session =
        new StudySession(
            USER_ID,
            null,
            SEVEN_PM,
            SEVEN_PM.plusSeconds(minutes * 60L),
            0,
            0,
            null,
            null,
            null,
            kind);
    session.recordCard(cardId, mode, false);
    return session;
  }

  private void givenTodaySessions(StudySession... sessions) {
    given(
            studySessionRepository.findStartedBetween(
                eq(USER_ID), eq(DayRule.startOf(TODAY)), eq(DayRule.startOf(TODAY.plusDays(1)))))
        .willReturn(List.of(sessions));
  }

  @Test
  @DisplayName("오늘 10분 넘게 끝낸 공부가 있으면 열리고, 카드 제목 · 체크리스트 · 분을 같이 돌려준다")
  void opensWithStudiedCard() {
    givenTodaySessions(session(40, SessionMode.NORMAL, PlanKind.STUDY, 3L));
    Card card =
        new Card(
            USER_ID,
            "미적분 3단원",
            TODAY,
            40,
            List.of(new ChecklistItem("a", "개념 정리"), new ChecklistItem("b", "예제 10문제")),
            NOW);
    given(cardRepository.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.of(card));

    CheckCall.Gate gate = service.gateOf(USER_ID, TODAY);

    assertThat(gate.open()).isTrue();
    assertThat(gate.reason()).isNull();
    assertThat(gate.studied())
        .isEqualTo(
            new CheckCall.Studied(
                "미적분 3단원", List.of("개념 정리", "예제 10문제"), 40, SEVEN_PM.plusSeconds(2400), null));
  }

  @Test
  @DisplayName("종료 팝업에 쓴 2~3줄 요약이 있으면 학생 메모로 같이 돌려준다 (통화 수정 2)")
  void opensWithEndReportMemo() {
    StudySession session = session(11, SessionMode.NORMAL, PlanKind.STUDY, null);
    session.recordEndReport(Map.of(), null, null, null, "곱의 미분 공식 정리", Set.of());
    givenTodaySessions(session);

    CheckCall.Gate gate = service.gateOf(USER_ID, TODAY);

    assertThat(gate.studied().memo()).isEqualTo("곱의 미분 공식 정리");
  }

  @Test
  @DisplayName("10분이 안 되는 공부 · 사후 입력 · 노는 기록만 있으면 끝낸 공부 없이 열린다 (공부 조건 제거, 2026-10-22)")
  void opensWithoutCountedStudy() {
    givenTodaySessions(
        session(9, SessionMode.NORMAL, PlanKind.STUDY, null),
        session(60, SessionMode.SELF_REPORT, PlanKind.STUDY, null),
        session(60, SessionMode.NORMAL, PlanKind.PLAY, null));

    assertThat(service.gateOf(USER_ID, TODAY)).isEqualTo(CheckCall.Gate.open(null));
  }

  @Test
  @DisplayName("오프라인으로 잰 공부 · 옛 기록(mode 없음)도 끝낸 공부로 친다")
  void offlineAndOldSessionsCount() {
    givenTodaySessions(session(30, SessionMode.OFFLINE, PlanKind.STUDY, null));
    assertThat(service.gateOf(USER_ID, TODAY).open()).isTrue();

    givenTodaySessions(session(30, null, PlanKind.STUDY, null));
    CheckCall.Gate gate = service.gateOf(USER_ID, TODAY);
    assertThat(gate.open()).isTrue();
    // 카드 없이 켠 기록은 제목이 없습니다.
    assertThat(gate.studied().title()).isNull();
    assertThat(gate.studied().checklist()).isEmpty();
  }

  @Test
  @DisplayName("오늘 공부 기록이 하나도 없어도 열린다 (studied 없음, 이유도 없음)")
  void opensWithNoSessionsToday() {
    givenTodaySessions();

    CheckCall.Gate gate = service.gateOf(USER_ID, TODAY);

    assertThat(gate.open()).isTrue();
    assertThat(gate.studied()).isNull();
    assertThat(gate.reason()).isNull();
  }

  @Test
  @DisplayName("GET check-call 응답: 끝난 시각은 밀리초 숫자, 공부가 없어도 open 은 true")
  void checkResponse() {
    givenTodaySessions(session(40, SessionMode.NORMAL, PlanKind.STUDY, null));
    assertThat(service.check())
        .isEqualTo(
            new CheckCallResponse(
                true,
                new CheckCallResponse.Studied(null, 40, SEVEN_PM.plusSeconds(2400).toEpochMilli()),
                null));

    givenTodaySessions();
    assertThat(service.check()).isEqualTo(new CheckCallResponse(true, null, null));
  }

  @Test
  @DisplayName("여러 개면 가장 늦게 끝난 것을 고른다 (순공시간은 일시정지를 뺌)")
  void picksLatestCounted() {
    StudySession early = session(40, SessionMode.NORMAL, PlanKind.STUDY, null);
    StudySession late =
        new StudySession(
            USER_ID,
            null,
            SEVEN_PM.plusSeconds(3600),
            SEVEN_PM.plusSeconds(3600 + 30 * 60),
            1,
            10 * 60_000L,
            null,
            null,
            null,
            PlanKind.STUDY);

    assertThat(CheckCall.latestCounted(List.of(late, early))).contains(late);
    // 30분 중 10분 쉬어서 순공 20분
    assertThat(CheckCall.minutesOf(late)).isEqualTo(20);
  }
}
