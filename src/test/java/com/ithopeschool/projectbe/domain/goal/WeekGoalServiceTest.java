package com.ithopeschool.projectbe.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.goal.dto.WeekGoalDto;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: 기록 · 저장소를 가짜로 두고, 제안 · 저장 · 목록 · 판정 규칙만 확인합니다. (이슈 #88)
class WeekGoalServiceTest {

  private static final Long ME = 7L;
  // 지금은 2026-10-21(수) 정오(한국 시간). 이번 주 월요일은 10-19, 지난주는 10-12 ~ 10-18 입니다.
  private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
  private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 10, 19);
  private static final LocalDate LAST_MONDAY = LocalDate.of(2026, 10, 12);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final WeekGoalRepository weekGoalRepository = mock(WeekGoalRepository.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final WeekGoalService service =
      new WeekGoalService(
          currentUserProvider,
          weekGoalRepository,
          studyPlanRepository,
          studySessionRepository,
          checkInRepository,
          examRepository,
          safetyService,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(weekGoalRepository.save(any()))
        .willAnswer(
            invocation -> {
              WeekGoal goal = invocation.getArgument(0);
              ReflectionTestUtils.setField(goal, "id", 1L);
              return goal;
            });
  }

  // 그 날 정오(한국 시간)에 시작한 목표 targetMinutes 분짜리 타이머. studyMinutes 만큼 공부함
  private static StudySession session(LocalDate date, int targetMinutes, int studyMinutes) {
    Instant start = date.atTime(3, 0).toInstant(ZoneOffset.UTC);
    return new StudySession(
        ME,
        1L,
        start,
        start.plusSeconds(studyMinutes * 60L),
        0,
        0,
        null,
        targetMinutes,
        null,
        PlanKind.STUDY);
  }

  // 지난주 목표가 있는 타이머 n 번 중 done 번 완주
  private void givenSessions(int n, int done) {
    List<StudySession> sessions = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      sessions.add(session(LAST_MONDAY.plusDays(i % 7), 30, i < done ? 30 : 10));
    }
    given(studySessionRepository.findStartedBetween(any(), any(), any())).willReturn(sessions);
  }

  @Test
  @DisplayName("지난주 완주율 50%(10번 중 5번)면 '완주율 60% 넘기기' 를 먼저, '지난주만큼' 을 마지막 후보로 제안한다")
  void proposesFromLastWeek() {
    givenSessions(10, 5);

    WeekGoalDto.Proposal proposal = service.propose();

    assertThat(proposal.weekStart()).isEqualTo(THIS_MONDAY);
    assertThat(proposal.candidates())
        .extracting(WeekGoalDto.Candidate::kind, WeekGoalDto.Candidate::target)
        .containsExactly(tuple("completion", 60), tuple("keep", 50));
    assertThat(proposal.candidates().get(0).title()).isEqualTo("완주율 60% 넘기기");
  }

  @Test
  @DisplayName("지난주 목표가 있는 타이머가 5번 미만이거나, 보호 조치 · 시험 기간이면 제안하지 않는다")
  void noProposal() {
    givenSessions(4, 1);
    assertThat(service.propose().candidates()).isEmpty();

    givenSessions(10, 5);
    given(safetyService.isActive(ME)).willReturn(true);
    assertThat(service.propose().candidates()).isEmpty();

    given(safetyService.isActive(ME)).willReturn(false);
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(ME))
        .willReturn(List.of(new Exam(ME, "중간고사", ExamType.SCHOOL, LocalDate.of(2026, 10, 22))));
    assertThat(service.propose().candidates()).isEmpty();
  }

  @Test
  @DisplayName("이번 주 목표를 처음 저장하면 새 줄, 다시 보내면 같은 줄을 덮어쓴다 (한 주에 하나)")
  void saveAndOverwrite() {
    given(weekGoalRepository.findByUserIdAndWeekStart(ME, THIS_MONDAY))
        .willReturn(Optional.empty());
    WeekGoalDto.Item created =
        service.save(THIS_MONDAY, new WeekGoalDto.SaveRequest("completion", 60, 50));
    assertThat(created.kind()).isEqualTo("completion");
    assertThat(created.judged()).isFalse();
    verify(weekGoalRepository).save(any());

    WeekGoal existing =
        new WeekGoal(ME, THIS_MONDAY, new WeeklyGoal(WeeklyGoal.Kind.KEEP, 50, 50), NOW);
    given(weekGoalRepository.findByUserIdAndWeekStart(ME, THIS_MONDAY))
        .willReturn(Optional.of(existing));
    WeekGoalDto.Item changed =
        service.save(THIS_MONDAY, new WeekGoalDto.SaveRequest("startDelay", 18, 23));
    assertThat(changed.kind()).isEqualTo("startDelay");
    assertThat(existing.getKind()).isEqualTo(WeeklyGoal.Kind.START_DELAY);
  }

  @Test
  @DisplayName("월요일이 아니거나 · 지난 주이거나 · 다음 주보다 뒤이거나 · 모르는 종류 · 100 넘는 비율이면 400 이고 저장하지 않는다")
  void saveRejects() {
    WeekGoalDto.SaveRequest ok = new WeekGoalDto.SaveRequest("completion", 60, 50);
    assertRejected(() -> service.save(THIS_MONDAY.plusDays(1), ok), "주의 시작(월요일) 날짜를 보내 주세요");
    assertRejected(() -> service.save(LAST_MONDAY, ok), "지난 주의 목표는 바꿀 수 없어요");
    assertRejected(() -> service.save(THIS_MONDAY.plusDays(14), ok), "이번 주나 다음 주 목표만 정할 수 있어요");
    assertRejected(
        () -> service.save(THIS_MONDAY, new WeekGoalDto.SaveRequest("more", 60, 50)),
        "목표 종류는 completion, startDelay, planRate, keep 중에서 골라 주세요");
    assertRejected(
        () -> service.save(THIS_MONDAY, new WeekGoalDto.SaveRequest("completion", 110, 50)),
        "비율 목표는 100 이하로 보내 주세요");
    verify(weekGoalRepository, never()).save(any());
  }

  @Test
  @DisplayName("끝난 주의 판정 전 목표만 그 주 기록으로 판정해 적고, 두 번 돌려도 이미 판정한 것은 다시 세지 않는다")
  void judgesEndedWeeks() {
    WeekGoal lastWeek =
        new WeekGoal(ME, LAST_MONDAY, new WeeklyGoal(WeeklyGoal.Kind.COMPLETION, 60, 50), NOW);
    given(weekGoalRepository.findByStatusIsNullAndWeekStartLessThanEqualOrderById(LAST_MONDAY))
        .willReturn(List.of(lastWeek))
        .willReturn(List.of());
    // 지난주 10번 중 7번 완주 → 70% ≥ 60% 달성
    givenSessions(10, 7);

    assertThat(service.judgeEndedWeeks(LocalDate.of(2026, 10, 21))).isEqualTo(1);
    assertThat(lastWeek.getStatus()).isEqualTo(WeeklyGoal.Status.ACHIEVED);
    assertThat(lastWeek.getResultValue()).isEqualTo(70);
    assertThat(service.judgeEndedWeeks(LocalDate.of(2026, 10, 21))).isZero();
  }

  @Test
  @DisplayName("목록: 판정한 주는 적어 둔 결과, 진행 중인 주는 지금까지의 기록으로 본 상태를 돌려준다")
  void listJudgedAndLive() {
    WeekGoal judged =
        new WeekGoal(ME, LAST_MONDAY, new WeeklyGoal(WeeklyGoal.Kind.COMPLETION, 65, 55), NOW);
    judged.judge(new WeeklyGoal.Progress(60, WeeklyGoal.Status.MISSED, 92), NOW);
    WeekGoal live =
        new WeekGoal(ME, THIS_MONDAY, new WeeklyGoal(WeeklyGoal.Kind.KEEP, 50, 50), NOW);
    given(
            weekGoalRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(
                any(), any(), any()))
        .willReturn(List.of(judged, live));

    List<WeekGoalDto.Item> items = service.list(LAST_MONDAY, THIS_MONDAY);

    assertThat(items.get(0))
        .isEqualTo(
            new WeekGoalDto.Item(
                LAST_MONDAY, "completion", 65, 55, "완주율 65% 넘기기", "missed", 60, 92, true));
    // 이번 주 기록이 없어서 완주율을 알 수 없음
    assertThat(items.get(1).status()).isEqualTo("unknown");
    assertThat(items.get(1).judged()).isFalse();
  }

  private static void assertRejected(Runnable action, String message) {
    assertThatThrownBy(action::run).isInstanceOf(ApiException.class).hasMessage(message);
  }
}
