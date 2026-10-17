package com.ithopeschool.projectbe.domain.onboarding;

import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanResponse;
import com.ithopeschool.projectbe.domain.routine.InitialPlan;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 처음 계획을 세우고·다시 세우고·보는 로직입니다. (이슈 #21, FE #240, docs/API.md "온보딩")
//
// 한 사람에 한 줄입니다(학생이 설계). 한 번 세우면 온보딩을 다시 해도 덮어쓰지 않습니다.
//   - 세우기(POST): 처음 한 번만 됩니다. 이미 있으면 409
//   - 다시 세우기(PUT): 사용자가 "다시 세우기" 를 눌렀을 때만 부릅니다. 아직 없으면 404
// 고를 수 있는 값은 첫 주 루틴 초안을 만드는 routine/InitialPlan 의 목록을 그대로 씁니다. (FE 와 같은 목록)
@Service
@RequiredArgsConstructor
public class InitialPlanService {

  private final CurrentUserProvider currentUserProvider;
  private final UserInitialPlanRepository userInitialPlanRepository;
  private final Clock clock;

  // 내 처음 계획. 아직 안 세웠으면 404 입니다.
  @Transactional(readOnly = true)
  public InitialPlanResponse get() {
    Long userId = currentUserProvider.getCurrentUserId();
    return InitialPlanResponse.from(findMine(userId));
  }

  // 처음 계획 세우기. 이미 세웠으면 덮어쓰지 않고 409 입니다.
  @Transactional
  public InitialPlanResponse create(InitialPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkOptions(request);
    if (userInitialPlanRepository.existsByUserId(userId)) {
      throw alreadyMade();
    }
    try {
      UserInitialPlan saved =
          userInitialPlanRepository.saveAndFlush(
              new UserInitialPlan(userId, today(), request.dailyMinutes(), request.daysPerWeek()));
      return InitialPlanResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
      // 위에서 확인한 직후에 같은 사람의 계획이 한 번 더 들어온 경우입니다. (완료 버튼을 빠르게 두 번 누름 등)
      // DB 의 "한 사람에 한 줄" 규칙(uk_initial_plans_user_id)이 막아 준 것이라 같은 답을 줍니다.
      throw alreadyMade();
    }
  }

  // 다시 세우기: 세운 날짜를 오늘로, 약속을 새 값으로 바꿉니다. 아직 안 세웠으면 404 입니다.
  @Transactional
  public InitialPlanResponse remake(InitialPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkOptions(request);
    UserInitialPlan plan = findMine(userId);
    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    plan.remake(today(), request.dailyMinutes(), request.daysPerWeek());
    return InitialPlanResponse.from(plan);
  }

  // 하루 목표는 1 ~ 5시간(60 · 120 · 180 · 240 · 300분), 일주일에 며칠은 3 · 5 · 6 · 7 중 하나여야 합니다.
  private void checkOptions(InitialPlanRequest request) {
    List<Integer> minuteOptions = new ArrayList<>();
    for (int hours : InitialPlan.DAILY_HOUR_OPTIONS) {
      minuteOptions.add(hours * 60);
    }
    if (!minuteOptions.contains(request.dailyMinutes())) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "하루 목표 시간은 " + join(minuteOptions) + "분 중에서 골라 주세요");
    }
    if (!InitialPlan.DAYS_PER_WEEK_OPTIONS.contains(request.daysPerWeek())) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "일주일에 며칠은 " + join(InitialPlan.DAYS_PER_WEEK_OPTIONS) + " 중에서 골라 주세요");
    }
  }

  private UserInitialPlan findMine(Long userId) {
    return userInitialPlanRepository
        .findByUserId(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "아직 처음 계획을 세우지 않았어요"));
  }

  // 오늘 날짜 (새벽 4시에 바뀜)
  private LocalDate today() {
    return DayRule.dateOf(clock.instant());
  }

  private static ApiException alreadyMade() {
    return new ApiException(ErrorCode.CONFLICT, "처음 계획은 이미 세웠어요. 바꾸려면 다시 세우기를 해 주세요");
  }

  private static String join(List<Integer> numbers) {
    List<String> texts = new ArrayList<>();
    for (Integer number : numbers) {
      texts.add(String.valueOf(number));
    }
    return String.join(", ", texts);
  }
}
