package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanBatchCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanUpdateRequest;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 공부 계획 목록·추가·수정·삭제 로직입니다. (이슈 #11 · #12, docs/API.md "공부 계획")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 모든 조회·수정에 "내 userId" 조건이 붙어서 남의 계획은 보이지도, 바뀌지도 않습니다.
// (남의 계획 번호를 보내면 "그런 번호가 없다" 는 것과 똑같이 404 로 답합니다)
@Service
@RequiredArgsConstructor
public class StudyPlanService {

  // 정렬 기준: 시작 시각이 있는 것 먼저 시각 순 → 시작 시각이 없는 것(null)은 뒤로 → 같으면 만든 순서(번호 순)
  private static final Comparator<StudyPlan> PLAN_ORDER =
      Comparator.comparing(
              StudyPlan::getStartTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
          .thenComparing(StudyPlan::getId);

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  // 계획에 붙일 과목이 "내 과목" 인지 확인할 때 씁니다.
  private final SubjectRepository subjectRepository;

  private final com.ithopeschool.projectbe.domain.routine.RoutineService routineService;

  // 그날의 내 계획 목록. 계획이 없는 날은 빈 목록입니다.
  @Transactional
  public List<StudyPlanResponse> getPlans(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    routineService.fill(userId, date);
    return studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date).stream()
        .sorted(PLAN_ORDER)
        .map(StudyPlanResponse::from)
        .toList();
  }

  // 계획 하나 추가.
  @Transactional
  public StudyPlanResponse create(StudyPlanCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    StudyPlan saved = studyPlanRepository.save(newPlan(userId, request));
    return StudyPlanResponse.from(saved);
  }

  // 계획 여러 개를 한 번에 추가. 하나라도 틀리면 하나도 저장하지 않습니다.
  //
  // 어떻게 "전부 아니면 전무" 가 되나요?
  // 1) 저장하기 전에 모든 계획을 먼저 검사합니다. (newPlan 이 틀린 계획에서 에러를 던짐)
  // 2) 혹시 저장하는 도중에 에러가 나도, @Transactional 이 그때까지 저장한 것을 모두 되돌립니다.
  @Transactional
  public List<StudyPlanResponse> createAll(StudyPlanBatchCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    List<StudyPlan> plans = new ArrayList<>();
    for (StudyPlanCreateRequest planRequest : request.plans()) {
      plans.add(newPlan(userId, planRequest));
    }
    return studyPlanRepository.saveAll(plans).stream().map(StudyPlanResponse::from).toList();
  }

  // 계획 바꾸기: 보낸 것만 바꿉니다.
  @Transactional
  public StudyPlanResponse update(Long id, StudyPlanUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    StudyPlan plan = findMyPlan(userId, id);

    if (request.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요");
    }

    // 종류·과목을 먼저 검사합니다. 여기서 에러가 나면 다른 값도 바뀌지 않습니다.
    // 둘 중 하나만 보내도, 바뀐 뒤의 종류와 과목이 서로 맞는지 함께 봅니다.
    if (request.kind() != null || request.subjectId() != null) {
      PlanKind kind = request.kind() != null ? parseKind(request.kind()) : plan.getKind();
      String subjectId =
          request.subjectId() != null ? request.subjectId() : toText(plan.getSubjectId());
      plan.changeKindAndSubject(kind, checkSubject(userId, kind, subjectId));
    }
    if (request.content() != null) {
      plan.changeContent(request.content());
    }
    if (request.targetMinutes() != null) {
      plan.changeTargetMinutes(request.targetMinutes());
    }
    if (request.done() != null) {
      plan.changeDone(request.done());
    }
    if (request.startTime() != null) {
      // 빈 글자("")면 parseStartTime 이 null 을 돌려줘서 "시간 미정" 이 됩니다.
      plan.changeStartTime(parseStartTime(request.startTime()));
    }

    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    return StudyPlanResponse.from(plan);
  }

  // 계획 삭제. 없는 번호이거나 남의 계획이면 404 입니다.
  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    studyPlanRepository.delete(findMyPlan(userId, id));
  }

  // 요청 하나를 검사해서 저장할 계획을 만듭니다. (추가와 여러 개 추가가 같이 씁니다)
  private StudyPlan newPlan(Long userId, StudyPlanCreateRequest request) {
    // kind 를 보내지 않으면 공부 계획입니다.
    PlanKind kind = request.kind() == null ? PlanKind.STUDY : parseKind(request.kind());
    Long subjectId = checkSubject(userId, kind, request.subjectId());
    return new StudyPlan(
        userId,
        subjectId,
        request.date(),
        request.content(),
        request.targetMinutes(),
        parseStartTime(request.startTime()),
        kind);
  }

  // 종류에 맞는 과목 번호를 돌려줍니다.
  // - 노는 계획: 과목이 없습니다. 보냈더라도 null 로 저장합니다.
  // - 공부 계획: 과목이 꼭 있어야 하고(400), 내 과목이어야 합니다(404).
  private Long checkSubject(Long userId, PlanKind kind, String subjectId) {
    if (kind == PlanKind.PLAY) {
      return null;
    }
    if (subjectId == null || subjectId.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "공부 계획은 과목을 골라 주세요");
    }
    // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
    Long id = Long.valueOf(subjectId);
    // 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (과목 API 와 같은 방식)
    if (subjectRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }
    return id;
  }

  // 내 계획 중에서 번호로 찾습니다. 없는 번호이거나 남의 계획이면 똑같이 404 입니다.
  private StudyPlan findMyPlan(Long userId, Long id) {
    return studyPlanRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요"));
  }

  // FE 가 보낸 글자("study")를 종류로 바꿉니다. 두 가지가 아니면 400 입니다.
  private PlanKind parseKind(String value) {
    PlanKind kind = PlanKind.fromValue(value);
    if (kind == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "계획 종류는 study, play 중에서 골라 주세요");
    }
    return kind;
  }

  // "19:00" 을 시각으로 바꿉니다. 보내지 않았거나(null) 빈 글자("")면 "시간 미정"(null) 입니다.
  // 모양("HH:mm")은 요청 DTO 의 @Pattern 이 이미 검사했습니다.
  private LocalTime parseStartTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalTime.parse(value);
  }

  // 과목 번호를 요청과 같은 글자 모양으로 바꿉니다. (비어 있으면 null)
  private String toText(Long subjectId) {
    return subjectId == null ? null : String.valueOf(subjectId);
  }
}
