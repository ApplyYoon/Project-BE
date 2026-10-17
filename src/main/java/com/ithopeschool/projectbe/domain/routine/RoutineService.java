package com.ithopeschool.projectbe.domain.routine;

import com.ithopeschool.projectbe.domain.plan.*;
import com.ithopeschool.projectbe.domain.routine.dto.*;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.*;
import com.ithopeschool.projectbe.global.time.DayRule;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoutineService {
  private final CurrentUserProvider currentUserProvider;
  private final RoutineRepository routines;
  private final RoutineApplicationRepository applications;
  private final StudyPlanRepository plans;
  private final SubjectRepository subjects;
  private final EntityManager entityManager;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<RoutineResponse> list() {
    return routines.findByUserIdOrderById(currentUserProvider.getCurrentUserId()).stream()
        .map(RoutineResponse::of)
        .toList();
  }

  @Transactional
  public RoutineResponse create(RoutineRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    lockUser(userId);
    Routine routine = new Routine(userId);
    change(routine, request, true);
    return RoutineResponse.of(routines.save(routine));
  }

  @Transactional
  public RoutineResponse update(Long id, RoutineRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    lockUser(userId);
    Routine routine = find(id, userId);
    change(routine, request, false);
    return RoutineResponse.of(routine);
  }

  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    lockUser(userId);
    routines.delete(find(id, userId));
  }

  // 같은 사용자 요청을 DB에서 직렬 처리해서 동시에 조회해도 한 번만 채웁니다.
  @Transactional
  public void fill(Long userId, LocalDate date) {
    LocalDate today = DayRule.dateOf(clock.instant());
    if (date.isBefore(today)) return;
    lockUser(userId);
    List<Routine> mine = routines.findByUserIdOrderById(userId);
    List<Long> filled =
        applications.findByUserIdAndPlanDate(userId, date).stream()
            .map(RoutineApplication::getRoutineId)
            .toList();
    List<Long> ids =
        RoutineFill.routineIdsToFill(
            mine.stream().map(r -> new RoutineFill.RoutineDays(r.getId(), r.days())).toList(),
            filled,
            date,
            today);
    // 동의가 만료되었어도 기존 계획 조회는 허용하되, 새 계획은 저장하지 않습니다.
    if (!ids.isEmpty() && !currentUserProvider.canSaveForCurrentUser()) {
      return;
    }
    for (Routine routine : mine) {
      if (ids.contains(routine.getId())) {
        plans.save(routine.planFor(date));
        applications.save(new RoutineApplication(userId, routine.getId(), date));
      }
    }
  }

  private void lockUser(Long userId) {
    if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null)
      throw new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
  }

  private Routine find(Long id, Long userId) {
    return routines
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "찾을 수 없어요"));
  }

  // 수정 때 보내지 않은 칸은 원래 값을 유지합니다.
  private void change(Routine routine, RoutineRequest request, boolean creating) {
    List<Integer> days = request.weekdays();
    String content = request.content();
    Integer minutes = request.targetMinutes();
    if (!creating) {
      if (days == null) days = routine.days();
      if (content == null) content = routine.getContent();
      if (minutes == null) minutes = routine.getTargetMinutes();
    }
    if (days == null || days.isEmpty() || content == null || content.isBlank() || minutes == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "요일, 할 일, 목표 시간을 보내 주세요");
    }
    PlanKind kind = creating ? PlanKind.STUDY : routine.getKind();
    if (request.kind() != null) {
      kind = PlanKind.fromValue(request.kind());
    }
    Long subject = routine.getSubjectId();
    if (request.subjectId() != null) {
      subject = request.subjectId().isEmpty() ? null : Long.valueOf(request.subjectId());
    }
    if (kind == PlanKind.PLAY) {
      subject = null;
    } else {
      if (subject == null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "공부 루틴은 과목을 골라 주세요");
      }
      if (subjects.findByIdAndUserId(subject, routine.getUserId()).isEmpty()) {
        throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
      }
    }
    LocalTime start = routine.getStartTime();
    if (request.startTime() != null) {
      start = request.startTime().isEmpty() ? null : LocalTime.parse(request.startTime());
    }
    routine.change(days, kind, subject, content.strip(), minutes, start);
  }
}
