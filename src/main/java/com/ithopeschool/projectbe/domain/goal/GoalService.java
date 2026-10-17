package com.ithopeschool.projectbe.domain.goal;

import com.ithopeschool.projectbe.domain.goal.dto.GoalCreateRequest;
import com.ithopeschool.projectbe.domain.goal.dto.GoalResponse;
import com.ithopeschool.projectbe.domain.goal.dto.GoalUpdateRequest;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 목표(WOOP) 목록 · 만들기 · 고치기 · 이력 로직입니다. (이슈 #221, docs/API.md "목표")
// 서버에 두는 이유: AI 하루 계획 · 카드 제안이 "내 목표와 D-day" 를 보고 짜려면 서버가 목표를 알아야 합니다.
//
// 규칙 (FE 의 src/lib/goals.ts · goalForm.ts 와 같습니다)
//   - 진행 중인 목표는 동시에 3개까지 (넘으면 409)
//   - D-day 는 내일 이후 (오늘은 하루 기준 새벽 4시로 셉니다)
//   - 목표값을 고치면 이유가 꼭 있어야 하고, 고치기 전 값이 이력에 남습니다. 이력은 지우지 않습니다.
//   - 끝난(완료 · 포기) 목표의 목표값은 고칠 수 없습니다. 결과를 보고 목표를 맞추는 "후견지명" 을 막으려는 것입니다.
//   - 포기할 때는 이유가 꼭 있어야 합니다.
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 남의 목표 번호는 404 입니다.
@Service
@RequiredArgsConstructor
public class GoalService {

  // 동시에 진행할 수 있는 목표 수 (FE 의 MAX_ACTIVE_GOALS)
  public static final int MAX_ACTIVE_GOALS = 3;

  private final CurrentUserProvider currentUserProvider;
  private final GoalRepository goalRepository;
  private final EntityManager entityManager;
  private final Clock clock;

  // 내 목표 목록 (만든 순서). 진행 · 완료 · 포기 모두. 하나도 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<GoalResponse> getGoals() {
    Long userId = currentUserProvider.getCurrentUserId();
    return goalRepository.findByUserIdOrderById(userId).stream().map(GoalResponse::from).toList();
  }

  // 목표 하나의 목표값 이력 (오래된 것부터). 고친 적이 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<GoalResponse.Snapshot> getHistory(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    return GoalResponse.historyOf(findMyGoal(userId, id));
  }

  // 목표 만들기
  @Transactional
  public GoalResponse create(GoalCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    GoalKind kind = GoalKind.fromValue(request.kind());
    if (kind == null) {
      throw badRequest("목표 유형은 score, completion 중에서 골라 주세요");
    }
    checkDDay(request.dDay());

    // 유형에 맞는 칸만 남깁니다. (점수형이면 분량 칸, 완주형이면 점수 칸을 버림)
    Goal.Target target;
    if (kind == GoalKind.SCORE) {
      if (request.currentScore() == null) {
        throw badRequest("지금 점수나 등급을 적어 주세요");
      }
      target = new Goal.Target(request.title(), request.dDay(), request.currentScore(), null, null);
    } else {
      if (request.totalAmount() == null) {
        throw badRequest("전체 분량을 숫자로 적어 주세요");
      }
      if (request.amountUnit() == null) {
        throw badRequest("단위를 적어 주세요");
      }
      target =
          new Goal.Target(
              request.title(), request.dDay(), null, request.totalAmount(), request.amountUnit());
    }

    lockUser(userId);
    checkActiveSlot(userId);

    Goal.Woop woop =
        new Goal.Woop(request.benefit(), request.cost(), request.obstacle(), request.plan());
    Goal goal = new Goal(userId, kind, target, woop, Instant.now(clock));
    return GoalResponse.from(goalRepository.save(goal));
  }

  // 목표 고치기: 보낸 것만 바꿉니다. 검사를 모두 마친 뒤에 바꿔서, 하나라도 틀리면 아무것도 바뀌지 않습니다.
  @Transactional
  public GoalResponse update(Long id, GoalUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Goal goal = findMyGoal(userId, id);

    if (request.isEmpty()) {
      throw badRequest("바꿀 것을 보내 주세요");
    }

    // ---- 1) 검사 ----

    // 목표값: 보낸 칸만 바꾼 "새 목표값" 을 만들어 지금 값과 비교합니다.
    Goal.Target newTarget = null;
    if (request.hasTarget()) {
      newTarget = mergeTarget(goal, request);
      if (newTarget.equals(goal.target())) {
        newTarget = null; // 지금과 똑같은 값을 보냈으면 바꿀 것이 없습니다. (이력도 남기지 않음)
      } else {
        if (!goal.isActive()) {
          throw badRequest("진행 중인 목표만 목표값을 바꿀 수 있어요");
        }
        if (request.reason() == null) {
          throw badRequest("목표를 바꾸는 이유를 적어 주세요");
        }
        if (!newTarget.dDay().equals(goal.getDDay())) {
          checkDDay(newTarget.dDay());
        }
      }
    }

    // 상태
    GoalStatus newStatus = null;
    if (request.status() != null) {
      newStatus = GoalStatus.fromValue(request.status());
      if (newStatus == null) {
        throw badRequest("상태는 active, done, givenUp 중에서 골라 주세요");
      }
    }
    if (newStatus == GoalStatus.GIVEN_UP && request.givenUpReason() == null) {
      throw badRequest("포기하는 이유를 적어 주세요");
    }
    if (newStatus != GoalStatus.GIVEN_UP && request.givenUpReason() != null) {
      throw badRequest("포기하는 이유는 포기할 때만 보내 주세요");
    }
    if (newStatus == GoalStatus.ACTIVE && !goal.isActive()) {
      lockUser(userId);
      checkActiveSlot(userId);
    }

    // ---- 2) 바꾸기 ---- 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    if (newTarget != null) {
      goal.changeTarget(newTarget, request.reason(), Instant.now(clock));
    }
    if (request.hasWoop()) {
      goal.changeWoop(mergeWoop(goal, request));
    }
    if (newStatus == GoalStatus.DONE) {
      goal.markDone();
    } else if (newStatus == GoalStatus.GIVEN_UP) {
      goal.giveUp(request.givenUpReason());
    } else if (newStatus == GoalStatus.ACTIVE) {
      goal.reopen();
    }
    if (request.result() != null) {
      goal.changeResult(request.result());
    }
    return GoalResponse.from(goal);
  }

  // 보낸 목표값 칸만 바꾼 새 목표값. 유형에 맞지 않는 칸을 보내면 400 입니다.
  private Goal.Target mergeTarget(Goal goal, GoalUpdateRequest request) {
    if (goal.getKind() == GoalKind.SCORE
        && (request.totalAmount() != null || request.amountUnit() != null)) {
      throw badRequest("점수형 목표에는 분량을 적지 않아요");
    }
    if (goal.getKind() == GoalKind.COMPLETION && request.currentScore() != null) {
      throw badRequest("완주형 목표에는 지금 점수를 적지 않아요");
    }
    Goal.Target now = goal.target();
    return new Goal.Target(
        request.title() != null ? request.title() : now.title(),
        request.dDay() != null ? request.dDay() : now.dDay(),
        request.currentScore() != null ? request.currentScore() : now.currentScore(),
        request.totalAmount() != null ? request.totalAmount() : now.totalAmount(),
        request.amountUnit() != null ? request.amountUnit() : now.amountUnit());
  }

  // 보낸 WOOP 칸만 바꾼 새 WOOP
  private Goal.Woop mergeWoop(Goal goal, GoalUpdateRequest request) {
    Goal.Woop now = goal.woop();
    return new Goal.Woop(
        request.benefit() != null ? request.benefit() : now.benefit(),
        request.cost() != null ? request.cost() : now.cost(),
        request.obstacle() != null ? request.obstacle() : now.obstacle(),
        request.plan() != null ? request.plan() : now.plan());
  }

  // D-day 는 내일 이후여야 합니다. "오늘" 은 하루 기준(새벽 4시)으로 셉니다. (FE 와 같은 문장)
  private void checkDDay(LocalDate dDay) {
    LocalDate today = DayRule.dateOf(Instant.now(clock));
    if (dDay.isBefore(today.plusDays(1))) {
      throw badRequest("D-day 는 내일 이후로 골라 주세요");
    }
  }

  // 진행 중인 목표가 이미 3개면 409 입니다.
  private void checkActiveSlot(Long userId) {
    if (goalRepository.countByUserIdAndStatus(userId, GoalStatus.ACTIVE) >= MAX_ACTIVE_GOALS) {
      throw new ApiException(ErrorCode.CONFLICT, "진행 중인 목표는 3개까지예요");
    }
  }

  // 같은 사람의 요청이 동시에 두 번 들어와도(만들기 버튼을 빠르게 두 번 누름 등) "3개까지" 를 넘지 않게,
  // 개수를 세기 전에 그 사람의 users 줄을 잠급니다. 먼저 온 요청이 끝날 때까지 다음 요청이 기다립니다. (SubjectService 와 같은 방법)
  private void lockUser(Long userId) {
    entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
  }

  // 내 목표 중에서 번호로 찾습니다. 없는 번호이거나 남의 목표면 똑같이 404 입니다.
  private Goal findMyGoal(Long userId, Long id) {
    return goalRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "목표를 찾을 수 없어요"));
  }

  private ApiException badRequest(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, message);
  }
}
