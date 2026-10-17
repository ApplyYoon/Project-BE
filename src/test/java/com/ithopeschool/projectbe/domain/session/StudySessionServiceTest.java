package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

// 공부 기록 저장 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에서 겹치는 기록을 찾는지는 StudySessionRepositoryTest 가 확인합니다.
class StudySessionServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;
  // 내 과목·내 계획 번호 (가짜 DB 에 "내 것" 으로 들어 있음)
  private static final Long MY_SUBJECT = 3L;
  private static final Long MY_PLAN = 7L;

  // 시계를 고정해 둔 "지금". 기록은 모두 이보다 앞선 시각으로 만듭니다.
  private static final Instant NOW = Instant.ofEpochMilli(1_790_010_000_000L);
  // 50분짜리 기록: 지금보다 2시간 전에 시작
  private static final long START = NOW.minusSeconds(2 * 3600).toEpochMilli();
  private static final long END = START + 50 * 60_000L;

  private CurrentUserProvider currentUserProvider;
  private StudySessionRepository studySessionRepository;
  private SubjectRepository subjectRepository;
  private StudyPlanRepository studyPlanRepository;
  private CardRepository cardRepository;
  private StudySessionService studySessionService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    studySessionRepository = mock(StudySessionRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    studyPlanRepository = mock(StudyPlanRepository.class);
    cardRepository = mock(CardRepository.class);
    studySessionService =
        new StudySessionService(
            currentUserProvider,
            studySessionRepository,
            subjectRepository,
            studyPlanRepository,
            cardRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 3번 과목·7번 계획만 내 것이고, 그 밖의 번호는 없거나 남의 것입니다.
    given(subjectRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(subjectRepository.findByIdAndUserId(MY_SUBJECT, ME))
        .willReturn(Optional.of(new Subject(ME, "수학", SubjectColor.BLUE)));
    given(studyPlanRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(studyPlanRepository.findByIdAndUserId(MY_PLAN, ME))
        .willReturn(
            Optional.of(new StudyPlan(ME, MY_SUBJECT, LocalDate.of(2026, 10, 5), "수학", 50)));
    // 처음에는 겹치는 기록이 없습니다.
    given(
            studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
                any(), any(), any()))
        .willReturn(false);
    // 저장하면 DB 가 번호를 매겨 준 것처럼 돌려줍니다.
    given(studySessionRepository.save(any()))
        .willAnswer(
            invocation -> {
              StudySession session = invocation.getArgument(0);
              ReflectionTestUtils.setField(session, "id", 100L);
              return session;
            });
  }

  // 기본 요청: 내 과목으로 50분 공부, 일시정지 없음. 필요한 칸만 바꿔 쓰려고 칸을 모두 받습니다.
  private static StudySessionCreateRequest request(
      String subjectId,
      long startedAt,
      long endedAt,
      Long pausedMs,
      String planId,
      Integer targetMinutes,
      String stopReason,
      String kind) {
    return new StudySessionCreateRequest(
        subjectId, startedAt, endedAt, 2, pausedMs, planId, targetMinutes, stopReason, kind);
  }

  private static StudySessionCreateRequest simple(long startedAt, long endedAt, Long pausedMs) {
    return request("3", startedAt, endedAt, pausedMs, null, null, null, null);
  }

  private void expectError(StudySessionCreateRequest request, ErrorCode code, String message) {
    assertThatThrownBy(() -> studySessionService.create(request))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
    verify(studySessionRepository, never()).save(any());
  }

  private StudySession savedSession() {
    ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
    verify(studySessionRepository).save(captor.capture());
    return captor.getValue();
  }

  // ---- 잘 저장되는 경우 ----

  @Test
  @DisplayName("모든 칸을 보내면 그대로 저장하고 FE StudySession 모양으로 돌려준다")
  void savesAllValues() {
    StudySessionResponse response =
        studySessionService.create(request("3", START, END, 300_000L, "7", 50, "tooMuch", "study"));

    StudySession saved = savedSession();
    assertThat(saved.getUserId()).isEqualTo(ME);
    assertThat(saved.getSubjectId()).isEqualTo(MY_SUBJECT);
    assertThat(saved.getStartedAt()).isEqualTo(Instant.ofEpochMilli(START));
    assertThat(saved.getEndedAt()).isEqualTo(Instant.ofEpochMilli(END));
    assertThat(saved.getPauseCount()).isEqualTo(2);
    assertThat(saved.getPausedMs()).isEqualTo(300_000L);
    assertThat(saved.getPlanId()).isEqualTo(MY_PLAN);
    assertThat(saved.getTargetMinutes()).isEqualTo(50);
    assertThat(saved.getStopReason()).isEqualTo(MissReason.TOO_MUCH);
    assertThat(saved.getKind()).isEqualTo(PlanKind.STUDY);

    assertThat(response)
        .isEqualTo(
            new StudySessionResponse(
                "100", "3", START, END, 2, 300_000L, "7", 50, "tooMuch", "study"));
  }

  @Test
  @DisplayName("선택 칸을 안 보내면 공부 기록 · 일시정지 0 · 계획·목표·이유 없음으로 저장한다")
  void optionalValuesDefault() {
    StudySessionResponse response = studySessionService.create(simple(START, END, null));

    assertThat(response)
        .isEqualTo(
            new StudySessionResponse("100", "3", START, END, 2, 0, null, null, null, "study"));
  }

  @Test
  @DisplayName("planId 를 빈 글자로 보내면 계획 없이 켠 타이머로 본다")
  void emptyPlanId() {
    studySessionService.create(request("3", START, END, null, "", null, null, null));

    assertThat(savedSession().getPlanId()).isNull();
  }

  @Test
  @DisplayName("노는 기록은 과목 없이 저장되고, 과목을 보냈더라도 비워서 저장한다")
  void playWithoutSubject() {
    StudySessionResponse noSubject =
        studySessionService.create(request(null, START, END, null, null, null, null, "play"));
    assertThat(noSubject.subjectId()).isEmpty();
    assertThat(noSubject.kind()).isEqualTo("play");

    // 남의 과목 번호를 보내도 노는 기록이면 과목을 보지 않고 비웁니다.
    studySessionService.create(request("99", START, END, null, null, null, null, "play"));
    verify(subjectRepository, never()).findByIdAndUserId(99L, ME);
  }

  // ---- 1분 미만 (순공 기준) ----

  @Test
  @DisplayName("순공이 딱 1분이면 저장한다")
  void exactlyOneMinute() {
    studySessionService.create(simple(START, START + 60_000, null));

    verify(studySessionRepository).save(any());
  }

  @Test
  @DisplayName("1분 미만 기록은 저장하지 않는다 (400)")
  void underOneMinute() {
    expectError(
        simple(START, START + 59_999, null), ErrorCode.VALIDATION_ERROR, "1분 미만의 기록은 저장하지 않아요");
  }

  @Test
  @DisplayName("끝 − 시작은 10분이어도 일시정지를 빼고 1분 미만이면 저장하지 않는다")
  void underOneMinuteAfterPause() {
    // 10분 중 9분 30초 일시정지 → 순공 30초
    expectError(
        simple(START, START + 600_000, 570_000L),
        ErrorCode.VALIDATION_ERROR,
        "1분 미만의 기록은 저장하지 않아요");
  }

  // ---- 시각이 이상한 기록 ----

  @Test
  @DisplayName("끝 시각이 시작보다 앞서면 400")
  void endBeforeStart() {
    expectError(simple(END, START, null), ErrorCode.VALIDATION_ERROR, "끝 시각은 시작 시각보다 뒤여야 해요");
  }

  @Test
  @DisplayName("끝 시각과 시작 시각이 같으면 400")
  void endEqualsStart() {
    expectError(simple(START, START, null), ErrorCode.VALIDATION_ERROR, "끝 시각은 시작 시각보다 뒤여야 해요");
  }

  @Test
  @DisplayName("끝 시각이 미래면 400")
  void endInFuture() {
    long now = NOW.toEpochMilli();
    expectError(
        simple(now - 600_000, now + 1, null), ErrorCode.VALIDATION_ERROR, "미래 시각은 저장할 수 없어요");
  }

  @Test
  @DisplayName("끝 시각이 정확히 지금이면 저장한다")
  void endIsNow() {
    long now = NOW.toEpochMilli();
    studySessionService.create(simple(now - 600_000, now, null));

    verify(studySessionRepository).save(any());
  }

  @Test
  @DisplayName("시작 시각이 2020년보다 앞서면 400 (폰 시계가 초기화된 1970년 기록 등)")
  void startBeforeEarliest() {
    long earliest = StudySessionService.EARLIEST_START.toEpochMilli();
    // 1970년에 시작해서 지금 끝난 기록: 저장되면 그 뒤 모든 기록이 "겹침" 으로 막힙니다
    expectError(simple(0, NOW.toEpochMilli(), null), ErrorCode.VALIDATION_ERROR, "시작 시각이 올바르지 않아요");
    // 아주 먼 과거 (운영 DB 가 담지 못하는 시각)
    expectError(
        simple(-100_000_000_000_000_000L, START, null),
        ErrorCode.VALIDATION_ERROR,
        "시작 시각이 올바르지 않아요");
    expectError(simple(Long.MIN_VALUE, START, null), ErrorCode.VALIDATION_ERROR, "시작 시각이 올바르지 않아요");
    expectError(
        simple(earliest - 1, earliest + 600_000, null),
        ErrorCode.VALIDATION_ERROR,
        "시작 시각이 올바르지 않아요");
  }

  @Test
  @DisplayName("시작 시각이 딱 2020-01-01 00:00(UTC)이면 저장한다")
  void startAtEarliest() {
    long earliest = StudySessionService.EARLIEST_START.toEpochMilli();
    studySessionService.create(simple(earliest, earliest + 600_000, null));

    verify(studySessionRepository).save(any());
  }

  // ---- 일시정지 시간 ----

  @Test
  @DisplayName("일시정지 시간이 끝 − 시작과 같거나 크면 400")
  void pausedTooLong() {
    long total = END - START;
    expectError(simple(START, END, total), ErrorCode.VALIDATION_ERROR, "일시정지 시간은 기록 길이보다 짧아야 해요");
    expectError(
        simple(START, END, total + 1), ErrorCode.VALIDATION_ERROR, "일시정지 시간은 기록 길이보다 짧아야 해요");
  }

  // ---- 종류 · 그만둔 이유 ----

  @Test
  @DisplayName("종류가 study·play 가 아니면 400")
  void wrongKind() {
    expectError(
        request("3", START, END, null, null, null, null, "rest"),
        ErrorCode.VALIDATION_ERROR,
        "기록 종류는 study, play 중에서 골라 주세요");
  }

  @Test
  @DisplayName("그만둔 이유가 7가지 중 하나가 아니면 400")
  void wrongStopReason() {
    expectError(
        request("3", START, END, null, null, null, "bored", null),
        ErrorCode.VALIDATION_ERROR,
        "그만둔 이유는 tired, event, phone, tooMuch, unwilling, sick, procrastinated, etc 중에서 골라 주세요");
  }

  // ---- 과목 · 계획 ----

  @Test
  @DisplayName("공부 기록인데 과목도 카드도 없으면 400")
  void studyWithoutSubject() {
    expectError(
        request(null, START, END, null, null, null, null, null),
        ErrorCode.VALIDATION_ERROR,
        "공부 기록은 과목이나 카드를 골라 주세요");
    expectError(
        request("", START, END, null, null, null, null, null),
        ErrorCode.VALIDATION_ERROR,
        "공부 기록은 과목이나 카드를 골라 주세요");
  }

  @Test
  @DisplayName("남의 과목이거나 없는 과목이면 404")
  void notMySubject() {
    expectError(
        request("99", START, END, null, null, null, null, null),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
  }

  @Test
  @DisplayName("남의 계획이거나 없는 계획이면 404")
  void notMyPlan() {
    expectError(
        request("3", START, END, null, "99", null, null, null),
        ErrorCode.NOT_FOUND,
        "계획을 찾을 수 없어요");
  }

  // ---- 겹치는 기록 ----

  @Test
  @DisplayName("겹침을 보기 전에 내 줄을 먼저 잠그고, 그다음 저장한다")
  void locksBeforeCheckingOverlap() {
    studySessionService.create(simple(START, END, null));

    InOrder order = inOrder(studySessionRepository);
    order.verify(studySessionRepository).lockUser(ME);
    order
        .verify(studySessionRepository)
        .existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(any(), any(), any());
    order.verify(studySessionRepository).save(any());
  }

  @Test
  @DisplayName("이미 저장된 내 기록과 시간이 겹치면 409")
  void overlapping() {
    given(
            studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
                ME, Instant.ofEpochMilli(END), Instant.ofEpochMilli(START)))
        .willReturn(true);

    expectError(simple(START, END, null), ErrorCode.CONFLICT, "이미 저장된 기록과 시간이 겹쳐요");
  }
}
