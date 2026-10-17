package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

// "설문 답 일부만 고치기"(PATCH) 요청의 모양입니다. 나중에 "내 정보 더 알려주기" 에서 답한 항목만 보냅니다.
//   { "chronotype": "night", "maxFocusMinutes": 50 }
//
// - 보내지 않은(null) 항목은 바로 전 답을 그대로 씁니다. 빈 글자("")도 보내지 않은 것으로 봅니다.
// - 목록(subjectLevels, pastFailureCauses)은 보내면 통째로 바뀝니다. 빈 목록 [] 을 보내면 "없음" 으로 바뀝니다.
// - 정해진 값인지(학년·장소 등)와 내 과목인지는 Service 에서 검사합니다.
// 글자 수·모양 검사는 PUT 요청(OnboardingSaveRequest)과 똑같습니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
public record OnboardingUpdateRequest(
    @Size(min = 1, message = "학년을 골라 주세요") String grade,
    @Size(min = 1, max = 30, message = "목표 시험은 1 ~ 30자로 적어 주세요") String targetExam,
    LocalDate targetExamDate,
    @Size(max = 30, message = "목표 대학은 30자 이하로 적어 주세요") String targetUniversity,
    @Size(max = 30, message = "목표 점수는 30자 이하로 적어 주세요") String targetScore,
    @Pattern(regexp = OnboardingSaveRequest.TIME, message = "기상 시각은 07:00 처럼 보내 주세요")
        String wakeUpTime,
    @Pattern(regexp = OnboardingSaveRequest.TIME, message = "취침 시각은 23:30 처럼 보내 주세요")
        String sleepTime,
    @Pattern(regexp = OnboardingSaveRequest.TIME, message = "학교 끝나는 시각은 16:30 처럼 보내 주세요")
        String schoolEndTime,
    @Pattern(regexp = OnboardingSaveRequest.TIME, message = "학원 끝나는 시각은 22:00 처럼 보내 주세요")
        String academyEndTime,
    String studyPlace,
    // @Valid 를 목록 안쪽(<> 안)에 붙여야 목록의 한 칸 한 칸을 검사합니다.
    @Size(max = OnboardingSaveRequest.MAX_SUBJECT_LEVELS, message = "과목별 수준은 50개까지 보낼 수 있어요")
        List<@Valid @NotNull(message = "subjectLevels 에 빈 값이 있어요") SubjectLevelRequest>
            subjectLevels,
    @Size(max = 5, message = "계획이 무너진 때는 5개까지 고를 수 있어요")
        List<@NotNull(message = "pastFailureCauses 에 빈 값이 있어요") String> pastFailureCauses,
    String chronotype,
    @JsonDeserialize(using = WholeNumberDeserializer.class) Integer maxFocusMinutes,
    String biggestDistraction,
    @JsonDeserialize(using = WholeNumberDeserializer.class) Integer dailyAvailableMinutes,
    @Size(max = 50, message = "공부하는 이유는 50자 이하로 적어 주세요") String motivationReason,
    @Size(max = 50, message = "목표를 이루면 하고 싶은 것은 50자 이하로 적어 주세요") String goalReward) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  // 학년·목표 시험은 꼭 있어야 하는 답이라, 빈 글자를 "안 보냄" 으로 바꾸지 않고 검사에 걸리게 둡니다.
  public OnboardingUpdateRequest {
    grade = OnboardingSaveRequest.trim(grade);
    targetExam = OnboardingSaveRequest.trim(targetExam);
    targetUniversity = OnboardingSaveRequest.blankToNull(targetUniversity);
    targetScore = OnboardingSaveRequest.blankToNull(targetScore);
    wakeUpTime = OnboardingSaveRequest.blankToNull(wakeUpTime);
    sleepTime = OnboardingSaveRequest.blankToNull(sleepTime);
    schoolEndTime = OnboardingSaveRequest.blankToNull(schoolEndTime);
    academyEndTime = OnboardingSaveRequest.blankToNull(academyEndTime);
    studyPlace = OnboardingSaveRequest.blankToNull(studyPlace);
    chronotype = OnboardingSaveRequest.blankToNull(chronotype);
    biggestDistraction = OnboardingSaveRequest.blankToNull(biggestDistraction);
    motivationReason = OnboardingSaveRequest.blankToNull(motivationReason);
    goalReward = OnboardingSaveRequest.blankToNull(goalReward);
  }

  // 아무 항목도 보내지 않았는지 알려줍니다. (바꿀 것이 없는 요청은 Service 가 400 으로 돌려보냅니다)
  public boolean nothingToChange() {
    return grade == null
        && targetExam == null
        && targetExamDate == null
        && targetUniversity == null
        && targetScore == null
        && wakeUpTime == null
        && sleepTime == null
        && schoolEndTime == null
        && academyEndTime == null
        && studyPlace == null
        && subjectLevels == null
        && pastFailureCauses == null
        && chronotype == null
        && maxFocusMinutes == null
        && biggestDistraction == null
        && dailyAvailableMinutes == null
        && motivationReason == null
        && goalReward == null;
  }
}
