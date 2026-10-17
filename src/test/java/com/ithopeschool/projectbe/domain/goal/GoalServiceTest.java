package com.ithopeschool.projectbe.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.goal.dto.GoalCreateRequest;
import com.ithopeschool.projectbe.domain.goal.dto.GoalResponse;
import com.ithopeschool.projectbe.domain.goal.dto.GoalUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 를 가짜로 바꿔서 목표 규칙(3개까지 · D-day · 이유 · 이력)을 확인합니다.
class GoalServiceTest {

  private static final Long ME = 7L;
  // 한국 시간 2026-10-17 18:00 → 하루 기준으로 오늘은 10-17, 내일은 10-18
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final Instant LATER = Instant.parse("2026-10-20T09:00:00Z");
  private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 18);
  private static final LocalDate EXAM_DAY = LocalDate.of(2026, 11, 19);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final GoalRepository repository = mock(GoalRepository.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final GoalService service =
      new GoalService(
          currentUserProvider, repository, entityManager, Clock.fixed(LATER, ZoneOffset.UTC));
  private final GoalService serviceToday =
      new GoalService(
          currentUserProvider, repository, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(repository.save(any(Goal.class)))
        .willAnswer(
            invocation -> {
              Goal goal = invocation.getArgument(0);
              ReflectionTestUtils.setField(goal, "id", 4L);
              return goal;
            });
  }

  private static GoalCreateRequest scoreRequest(LocalDate dDay, String currentScore) {
    return new GoalCreateRequest(
        "score", "수학 3등급", dDay, currentScore, 300, "쪽", "자신감", "후회", "폰을 봐요", "폰을 다른 방에");
  }

  private static GoalCreateRequest completionRequest(Integer totalAmount, String amountUnit) {
    return new GoalCreateRequest(
        "completion",
        "영단어장 1회독",
        EXAM_DAY,
        "5등급",
        totalAmount,
        amountUnit,
        "자신감",
        "후회",
        "미뤄요",
        "알람 맞추기");
  }

  // 내 점수형 진행 목표 (번호 4)
  private Goal myScoreGoal() {
    Goal goal =
        new Goal(
            ME,
            GoalKind.SCORE,
            new Goal.Target("수학 3등급", EXAM_DAY, "5등급", null, null),
            new Goal.Woop("자신감", "후회", "폰을 봐요", "폰을 다른 방에"),
            NOW);
    ReflectionTestUtils.setField(goal, "id", 4L);
    given(repository.findByIdAndUserId(4L, ME)).willReturn(Optional.of(goal));
    return goal;
  }

  private static GoalUpdateRequest update(
      String title, LocalDate dDay, String reason, String status, String givenUpReason) {
    return new GoalUpdateRequest(
        title, dDay, null, null, null, reason, null, null, null, null, status, null, givenUpReason);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  // ---- 만들기 ----

  @Test
  @DisplayName("점수형을 만들면 진행 중으로 저장하고, 유형에 맞지 않는 분량 칸은 버린다")
  void createScore() {
    GoalResponse response = service.create(scoreRequest(EXAM_DAY, "5등급"));

    ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
    verify(repository).save(captor.capture());
    Goal saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(ME);
    assertThat(saved.getTotalAmount()).isNull();
    assertThat(saved.getAmountUnit()).isNull();
    assertThat(response.id()).isEqualTo("4");
    assertThat(response.kind()).isEqualTo("score");
    assertThat(response.status()).isEqualTo("active");
    assertThat(response.currentScore()).isEqualTo("5등급");
    assertThat(response.history()).isEmpty();
    assertThat(response.createdAt()).isEqualTo(LATER.toEpochMilli());
  }

  @Test
  @DisplayName("완주형은 분량 · 단위를 남기고 지금 점수 칸은 버린다")
  void createCompletion() {
    GoalResponse response = service.create(completionRequest(300, "쪽"));

    assertThat(response.kind()).isEqualTo("completion");
    assertThat(response.totalAmount()).isEqualTo(300);
    assertThat(response.amountUnit()).isEqualTo("쪽");
    assertThat(response.currentScore()).isNull();
  }

  @Test
  @DisplayName("유형별로 꼭 있어야 하는 칸이 없으면 400 (FE 와 같은 문장)")
  void requiredByKind() {
    assertError(
        () -> service.create(scoreRequest(EXAM_DAY, null)),
        ErrorCode.VALIDATION_ERROR,
        "지금 점수나 등급을 적어 주세요");
    assertError(
        () -> service.create(completionRequest(null, "쪽")),
        ErrorCode.VALIDATION_ERROR,
        "전체 분량을 숫자로 적어 주세요");
    assertError(
        () -> service.create(completionRequest(300, null)),
        ErrorCode.VALIDATION_ERROR,
        "단위를 적어 주세요");
  }

  @Test
  @DisplayName("유형 글자가 틀리면 400")
  void badKind() {
    GoalCreateRequest request =
        new GoalCreateRequest("SCORE", "수학 3등급", EXAM_DAY, "5등급", null, null, "a", "b", "c", "d");

    assertError(
        () -> service.create(request),
        ErrorCode.VALIDATION_ERROR,
        "목표 유형은 score, completion 중에서 골라 주세요");
  }

  @Test
  @DisplayName("D-day 는 내일부터 된다 (오늘은 400)")
  void dDayFromTomorrow() {
    assertError(
        () -> serviceToday.create(scoreRequest(LocalDate.of(2026, 10, 17), "5등급")),
        ErrorCode.VALIDATION_ERROR,
        "D-day 는 내일 이후로 골라 주세요");

    assertThat(serviceToday.create(scoreRequest(TOMORROW, "5등급")).dDay()).isEqualTo(TOMORROW);
  }

  @Test
  @DisplayName("진행 중인 목표가 이미 3개면 409 이고 저장하지 않는다")
  void maxThreeActive() {
    given(repository.countByUserIdAndStatus(ME, GoalStatus.ACTIVE)).willReturn(3L);

    assertError(
        () -> service.create(scoreRequest(EXAM_DAY, "5등급")),
        ErrorCode.CONFLICT,
        "진행 중인 목표는 3개까지예요");
    verify(repository, never()).save(any());
  }

  // ---- 목표값 고치기 ----

  @Test
  @DisplayName("목표값을 고치면 고치기 전 값과 이유 · 시각이 이력에 남는다")
  void changeTargetKeepsHistory() {
    myScoreGoal();

    GoalResponse response = service.update(4L, update("수학 4등급", null, "시간이 모자라서", null, null));

    assertThat(response.title()).isEqualTo("수학 4등급");
    assertThat(response.history())
        .containsExactly(
            new GoalResponse.Snapshot(
                "수학 3등급", EXAM_DAY, "5등급", null, null, LATER.toEpochMilli(), "시간이 모자라서"));
  }

  @Test
  @DisplayName("목표값을 바꾸는데 이유가 없으면 400 이고 아무것도 안 바뀐다")
  void changeTargetNeedsReason() {
    Goal goal = myScoreGoal();

    assertError(
        () -> service.update(4L, update("수학 4등급", null, null, null, null)),
        ErrorCode.VALIDATION_ERROR,
        "목표를 바꾸는 이유를 적어 주세요");
    assertThat(goal.getTitle()).isEqualTo("수학 3등급");
    assertThat(goal.getHistory()).isEmpty();
  }

  @Test
  @DisplayName("지금과 똑같은 값을 보내면 이유가 없어도 되고 이력도 남지 않는다")
  void sameTargetIsNotAChange() {
    Goal goal = myScoreGoal();

    service.update(4L, update("수학 3등급", EXAM_DAY, null, null, null));

    assertThat(goal.getHistory()).isEmpty();
  }

  @Test
  @DisplayName("D-day 를 오늘 이전으로 당기면 400")
  void changeDDayToPast() {
    myScoreGoal();

    assertError(
        () -> service.update(4L, update(null, LocalDate.of(2026, 10, 20), "앞당김", null, null)),
        ErrorCode.VALIDATION_ERROR,
        "D-day 는 내일 이후로 골라 주세요");
  }

  @Test
  @DisplayName("점수형 목표에 분량을 보내면 400")
  void wrongKindField() {
    myScoreGoal();
    GoalUpdateRequest request =
        new GoalUpdateRequest(
            null, null, null, 300, null, "이유", null, null, null, null, null, null, null);

    assertError(
        () -> service.update(4L, request), ErrorCode.VALIDATION_ERROR, "점수형 목표에는 분량을 적지 않아요");
  }

  @Test
  @DisplayName("끝난 목표의 목표값은 고칠 수 없다 (결과를 보고 목표를 맞추는 일을 막음)")
  void finishedGoalTargetIsLocked() {
    Goal goal = myScoreGoal();
    goal.markDone();

    assertError(
        () -> service.update(4L, update("수학 5등급", null, "결과에 맞춤", null, null)),
        ErrorCode.VALIDATION_ERROR,
        "진행 중인 목표만 목표값을 바꿀 수 있어요");
  }

  // ---- 상태 ----

  @Test
  @DisplayName("포기하려면 이유가 꼭 있어야 하고, 있으면 포기 · 이유가 저장된다")
  void giveUpNeedsReason() {
    Goal goal = myScoreGoal();

    assertError(
        () -> service.update(4L, update(null, null, null, "givenUp", null)),
        ErrorCode.VALIDATION_ERROR,
        "포기하는 이유를 적어 주세요");

    GoalResponse response = service.update(4L, update(null, null, null, "givenUp", "다른 목표로"));
    assertThat(response.status()).isEqualTo("givenUp");
    assertThat(response.givenUpReason()).isEqualTo("다른 목표로");
    assertThat(goal.getStatus()).isEqualTo(GoalStatus.GIVEN_UP);
  }

  @Test
  @DisplayName("다시 진행으로 바꿀 때 이미 진행 중이 3개면 409")
  void reopenChecksSlot() {
    Goal goal = myScoreGoal();
    goal.giveUp("잠깐 쉼");
    given(repository.countByUserIdAndStatus(ME, GoalStatus.ACTIVE)).willReturn(3L);

    assertError(
        () -> service.update(4L, update(null, null, null, "active", null)),
        ErrorCode.CONFLICT,
        "진행 중인 목표는 3개까지예요");
    assertThat(goal.getStatus()).isEqualTo(GoalStatus.GIVEN_UP);
  }

  @Test
  @DisplayName("상태 글자가 틀리거나, 아무것도 안 보내면 400 · 남의 목표는 404")
  void badRequests() {
    myScoreGoal();
    given(repository.findByIdAndUserId(9L, ME)).willReturn(Optional.empty());

    assertError(
        () -> service.update(4L, update(null, null, null, "given_up", null)),
        ErrorCode.VALIDATION_ERROR,
        "상태는 active, done, givenUp 중에서 골라 주세요");
    assertError(
        () -> service.update(4L, update(null, null, "이유만", null, null)),
        ErrorCode.VALIDATION_ERROR,
        "바꿀 것을 보내 주세요");
    assertError(
        () -> service.update(9L, update("x", null, "y", null, null)),
        ErrorCode.NOT_FOUND,
        "목표를 찾을 수 없어요");
    assertError(() -> service.getHistory(9L), ErrorCode.NOT_FOUND, "목표를 찾을 수 없어요");
  }

  @Test
  @DisplayName("목록은 내 목표만, 이력은 그 목표의 고친 기록을 오래된 것부터")
  void listAndHistory() {
    Goal goal = myScoreGoal();
    goal.changeTarget(new Goal.Target("수학 4등급", EXAM_DAY, "5등급", null, null), "첫째", NOW);
    goal.changeTarget(new Goal.Target("수학 4등급", EXAM_DAY, "4등급", null, null), "둘째", LATER);
    given(repository.findByUserIdOrderById(ME)).willReturn(List.of(goal));

    assertThat(service.getGoals()).extracting(GoalResponse::id).containsExactly("4");
    assertThat(service.getHistory(4L))
        .extracting(GoalResponse.Snapshot::reason)
        .containsExactly("첫째", "둘째");
  }
}
