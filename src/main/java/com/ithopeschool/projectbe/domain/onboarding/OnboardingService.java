package com.ithopeschool.projectbe.domain.onboarding;

import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingSaveRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingUpdateRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.SubjectLevelRequest;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 온보딩 설문 답을 저장·조회하는 로직입니다. (이슈 #21, docs/API.md "온보딩")
//
// 저장할 때마다 새 줄을 더합니다(학생이 설계). 원래 줄은 바꾸지 않아서 처음 답한 자기 인식이 남습니다.
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 설문은 보이지 않습니다.
@Service
@RequiredArgsConstructor
public class OnboardingService {

  // 한 번에 집중 가능한 시간(분)으로 고를 수 있는 값 (FE onboardingSteps.ts 의 maxFocusMinutes 보기)
  public static final List<Integer> MAX_FOCUS_MINUTES_OPTIONS = List.of(30, 50, 90, 120);

  // 하루 공부 가능 시간(분)으로 고를 수 있는 값 (FE onboardingSteps.ts 의 dailyAvailableMinutes 보기, 300 = 5시간 이상)
  public static final List<Integer> DAILY_AVAILABLE_MINUTES_OPTIONS =
      List.of(60, 120, 180, 240, 300);

  private final CurrentUserProvider currentUserProvider;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final SubjectRepository subjectRepository;
  private final Clock clock;

  // 설문 답 저장(PUT): 보낸 답으로 새 줄을 만듭니다. 보내지 않은 항목은 비어 있는 채로 저장됩니다.
  @Transactional
  public OnboardingResponse save(OnboardingSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    // 빈 상자에서 시작해서, 보낸 답만 채웁니다.
    OnboardingAnswer.Values values = new OnboardingAnswer.Values();
    fill(userId, values, request.toUpdateRequest());
    return saveNewAnswer(userId, values);
  }

  // 설문 답 일부만 고치기(PATCH): 바로 전 답을 옮겨 적은 뒤 보낸 항목만 바꿔서, 새 줄로 저장합니다.
  // 설문을 아직 안 했으면 고칠 것이 없어서 404 입니다.
  @Transactional
  public OnboardingResponse update(OnboardingUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (request.nothingToChange()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 답을 하나 이상 보내 주세요");
    }
    OnboardingAnswer.Values values = findLatest(userId).copyValues();
    fill(userId, values, request);
    return saveNewAnswer(userId, values);
  }

  // 지금 내 설문과 처음 답한 자기 인식. 설문을 아직 안 했으면 404 입니다.
  @Transactional(readOnly = true)
  public OnboardingResponse get() {
    Long userId = currentUserProvider.getCurrentUserId();
    return responseOf(userId, findLatest(userId));
  }

  private OnboardingResponse saveNewAnswer(Long userId, OnboardingAnswer.Values values) {
    OnboardingAnswer saved =
        onboardingAnswerRepository.save(new OnboardingAnswer(userId, clock.instant(), values));
    return responseOf(userId, saved);
  }

  private OnboardingResponse responseOf(Long userId, OnboardingAnswer latest) {
    List<OnboardingAnswer> all =
        onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(userId);
    return OnboardingResponse.from(latest, firstSelfAwarenessOf(all), mySubjectIds(userId));
  }

  // 처음 답한 자기 인식: 항목마다 "처음으로 답이 있는 줄" 의 값을 꺼냅니다. (answers 는 처음 것부터)
  // 항목마다 따로 찾는 이유: 온보딩이 짧아져서, 아침형/밤형 같은 것은 나중에 "내 정보 더 알려주기" 에서 처음 답하기 때문입니다.
  //   1번째 줄: 밤형 없음 · 하루 180분      2번째 줄: 아침형 · 하루 180분      3번째 줄: 밤형 · 하루 120분
  //   → 처음 답한 자기 인식은 아침형 · 하루 180분
  public static OnboardingResponse.SelfAwareness firstSelfAwarenessOf(
      List<OnboardingAnswer> answers) {
    String chronotype = null;
    Integer maxFocusMinutes = null;
    String biggestDistraction = null;
    Integer dailyAvailableMinutes = null;
    for (OnboardingAnswer answer : answers) {
      if (chronotype == null && answer.getChronotype() != null) {
        chronotype = answer.getChronotype().getValue();
      }
      if (maxFocusMinutes == null) {
        maxFocusMinutes = answer.getMaxFocusMinutes();
      }
      if (biggestDistraction == null && answer.getBiggestDistraction() != null) {
        biggestDistraction = answer.getBiggestDistraction().getValue();
      }
      if (dailyAvailableMinutes == null) {
        dailyAvailableMinutes = answer.getDailyAvailableMinutes();
      }
    }
    return new OnboardingResponse.SelfAwareness(
        chronotype, maxFocusMinutes, biggestDistraction, dailyAvailableMinutes);
  }

  // 보낸(null 이 아닌) 항목만 검사해서 values 에 채웁니다. 하나라도 틀리면 400 이고, 아무것도 저장되지 않습니다.
  private void fill(Long userId, OnboardingAnswer.Values values, OnboardingUpdateRequest request) {
    if (request.grade() != null) {
      values.grade = Grade.fromValue(request.grade());
      if (values.grade == null) {
        throw badChoice("학년은 " + Grade.allowedValues());
      }
    }
    if (request.targetExam() != null) {
      values.targetExam = request.targetExam();
    }
    if (request.targetExamDate() != null) {
      // 화면에서도 오늘보다 앞의 날은 고를 수 없습니다. D-day 가 음수가 되지 않게 서버에서도 막습니다.
      LocalDate today = DayRule.dateOf(clock.instant());
      if (request.targetExamDate().isBefore(today)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 날짜는 오늘이나 그 뒤로 골라 주세요");
      }
      values.targetExamDate = request.targetExamDate();
    }
    if (request.targetUniversity() != null) {
      values.targetUniversity = request.targetUniversity();
    }
    if (request.targetScore() != null) {
      values.targetScore = request.targetScore();
    }
    // 시각의 모양("07:00")은 요청 DTO 의 @Pattern 이 이미 검사했습니다.
    if (request.wakeUpTime() != null) {
      values.wakeUpTime = LocalTime.parse(request.wakeUpTime());
    }
    if (request.sleepTime() != null) {
      values.sleepTime = LocalTime.parse(request.sleepTime());
    }
    if (request.schoolEndTime() != null) {
      values.schoolEndTime = LocalTime.parse(request.schoolEndTime());
    }
    if (request.academyEndTime() != null) {
      values.academyEndTime = LocalTime.parse(request.academyEndTime());
    }
    if (request.studyPlace() != null) {
      values.studyPlace = StudyPlace.fromValue(request.studyPlace());
      if (values.studyPlace == null) {
        throw badChoice("공부 장소는 " + StudyPlace.allowedValues());
      }
    }
    if (request.subjectLevels() != null) {
      values.subjectLevels = subjectLevelsOf(userId, request.subjectLevels());
    }
    if (request.pastFailureCauses() != null) {
      values.pastFailureCauses = failureCausesOf(request.pastFailureCauses());
    }
    if (request.chronotype() != null) {
      values.chronotype = Chronotype.fromValue(request.chronotype());
      if (values.chronotype == null) {
        throw badChoice("아침형/밤형은 " + Chronotype.allowedValues());
      }
    }
    if (request.maxFocusMinutes() != null) {
      if (!MAX_FOCUS_MINUTES_OPTIONS.contains(request.maxFocusMinutes())) {
        throw badChoice("한 번에 집중 가능한 시간은 " + join(MAX_FOCUS_MINUTES_OPTIONS));
      }
      values.maxFocusMinutes = request.maxFocusMinutes();
    }
    if (request.biggestDistraction() != null) {
      values.biggestDistraction = Distraction.fromValue(request.biggestDistraction());
      if (values.biggestDistraction == null) {
        throw badChoice("가장 큰 방해 요소는 " + Distraction.allowedValues());
      }
    }
    if (request.dailyAvailableMinutes() != null) {
      if (!DAILY_AVAILABLE_MINUTES_OPTIONS.contains(request.dailyAvailableMinutes())) {
        throw badChoice("하루 공부 가능 시간은 " + join(DAILY_AVAILABLE_MINUTES_OPTIONS));
      }
      values.dailyAvailableMinutes = request.dailyAvailableMinutes();
    }
    if (request.motivationReason() != null) {
      values.motivationReason = request.motivationReason();
    }
    if (request.goalReward() != null) {
      values.goalReward = request.goalReward();
    }
  }

  // 보낸 글자들을 "계획이 무너진 때" 로 바꿉니다. 같은 것을 두 번 보내면 하나로 칩니다.
  private Set<FailureCause> failureCausesOf(List<String> names) {
    Set<FailureCause> causes = EnumSet.noneOf(FailureCause.class);
    for (String name : names) {
      FailureCause cause = FailureCause.fromValue(name);
      if (cause == null) {
        throw badChoice("계획이 무너진 때는 " + FailureCause.allowedValues());
      }
      causes.add(cause);
    }
    return causes;
  }

  // 과목별 수준을 검사해서 바꿉니다.
  //   - 같은 과목을 두 번 보내면 400 (어느 쪽이 맞는지 알 수 없어서)
  //   - 등급과 점수는 둘 중 하나만 (둘 다 / 둘 다 없음 → 400)
  //   - 내 과목이 아니면 404 (없는 과목과 남의 과목은 구별하지 않습니다. 계획 API 와 같은 방식)
  private List<SubjectLevel> subjectLevelsOf(Long userId, List<SubjectLevelRequest> requests) {
    Set<Long> mySubjectIds = mySubjectIds(userId);
    Set<Long> seen = new HashSet<>();
    List<SubjectLevel> levels = new ArrayList<>();
    for (SubjectLevelRequest request : requests) {
      // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
      Long subjectId = Long.valueOf(request.subjectId());
      if (!seen.add(subjectId)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 과목의 수준을 두 번 보냈어요");
      }
      if ((request.grade() == null) == (request.score() == null)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "과목마다 등급과 점수 중 하나만 보내 주세요");
      }
      if (!mySubjectIds.contains(subjectId)) {
        throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
      }
      levels.add(
          new SubjectLevel(subjectId, request.grade(), request.score(), request.confidence()));
    }
    return levels;
  }

  // 지금 있는 내 과목 번호들
  private Set<Long> mySubjectIds(Long userId) {
    Set<Long> ids = new HashSet<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      ids.add(subject.getId());
    }
    return ids;
  }

  private OnboardingAnswer findLatest(Long userId) {
    return onboardingAnswerRepository
        .findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "아직 온보딩 설문을 하지 않았어요"));
  }

  // "학년은 고1, 고2, … 중에서 골라 주세요" 같은 400 에러를 만듭니다.
  private static ApiException badChoice(String allowed) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, allowed + " 중에서 골라 주세요");
  }

  private static String join(List<Integer> numbers) {
    List<String> texts = new ArrayList<>();
    for (Integer number : numbers) {
      texts.add(String.valueOf(number));
    }
    return String.join(", ", texts);
  }
}
