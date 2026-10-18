package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.global.json.WholeNumberDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

// "설문 답 저장"(PUT) 요청의 모양입니다. FE 의 OnboardingAnswers 와 같은 이름을 씁니다.
//   { "grade": "고2", "targetExam": "수능", "targetExamDate": "2027-11-18",
//     "dailyAvailableMinutes": 180 }
//
// - 꼭 보내야 하는 것은 학년 · 하루 공부 가능 시간 두 개뿐입니다.
//   목표 시험 · 시험 날짜는 2026-10-17 부터 선택입니다. 새 온보딩(FE #417 정리 뒤)이 더 묻지 않아서,
//   필수로 두면 새 학생이 설문(취침 · 기상 · 아침형/저녁형 등)을 서버에 저장할 길이 없었습니다.
// - 나머지는 보내지 않으면 비어 있는 채로 저장됩니다. (PUT 은 "설문 전체를 이 답으로" 라는 뜻입니다)
// - 정해진 값인지(학년·장소 등)와 내 과목인지는 Service 에서 검사합니다.
// - 숫자 칸은 정수만 받습니다. 180.5 처럼 소수를 보내면 400 입니다. (WholeNumberDeserializer)
// 글자 수·모양 검사는 PATCH 요청(OnboardingUpdateRequest)과 똑같습니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
public record OnboardingSaveRequest(
    @NotBlank(message = "학년을 골라 주세요") String grade,
    @Size(max = 30, message = "목표 시험은 30자 이하로 적어 주세요") String targetExam,
    LocalDate targetExamDate,
    @Size(max = 30, message = "목표 대학은 30자 이하로 적어 주세요") String targetUniversity,
    @Size(max = 30, message = "목표 점수는 30자 이하로 적어 주세요") String targetScore,
    @Pattern(regexp = TIME, message = "기상 시각은 07:00 처럼 보내 주세요") String wakeUpTime,
    @Pattern(regexp = TIME, message = "취침 시각은 23:30 처럼 보내 주세요") String sleepTime,
    @Pattern(regexp = TIME, message = "학교 끝나는 시각은 16:30 처럼 보내 주세요") String schoolEndTime,
    @Pattern(regexp = TIME, message = "학원 끝나는 시각은 22:00 처럼 보내 주세요") String academyEndTime,
    String studyPlace,
    // @Valid 를 목록 안쪽(<> 안)에 붙여야 목록의 한 칸 한 칸을 검사합니다.
    @Size(max = MAX_SUBJECT_LEVELS, message = "과목별 수준은 50개까지 보낼 수 있어요")
        List<@Valid @NotNull(message = "subjectLevels 에 빈 값이 있어요") SubjectLevelRequest>
            subjectLevels,
    @Size(max = 5, message = "계획이 무너진 때는 5개까지 고를 수 있어요")
        List<@NotNull(message = "pastFailureCauses 에 빈 값이 있어요") String> pastFailureCauses,
    String chronotype,
    @JsonDeserialize(using = WholeNumberDeserializer.class) Integer maxFocusMinutes,
    String biggestDistraction,
    @NotNull(message = "하루 공부 가능 시간을 골라 주세요")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer dailyAvailableMinutes,
    @Size(max = 50, message = "공부하는 이유는 50자 이하로 적어 주세요") String motivationReason,
    @Size(max = 50, message = "목표를 이루면 하고 싶은 것은 50자 이하로 적어 주세요") String goalReward) {

  // "07:00" 처럼 두 자리:두 자리. 00:00 ~ 23:59 만 됩니다. (계획의 시작 시각과 같은 모양)
  public static final String TIME = "([01]\\d|2[0-3]):[0-5]\\d";

  // 과목별 수준을 한 번에 보낼 수 있는 개수. 아주 긴 목록으로 서버를 괴롭히지 못하게 막습니다.
  public static final int MAX_SUBJECT_LEVELS = 50;

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 비어 있어도 되는 글자는 빈 글자("")를 "안 보냄"(null)으로 바꿉니다.
  public OnboardingSaveRequest {
    grade = trim(grade);
    targetExam = blankToNull(targetExam);
    targetUniversity = blankToNull(targetUniversity);
    targetScore = blankToNull(targetScore);
    wakeUpTime = blankToNull(wakeUpTime);
    sleepTime = blankToNull(sleepTime);
    schoolEndTime = blankToNull(schoolEndTime);
    academyEndTime = blankToNull(academyEndTime);
    studyPlace = blankToNull(studyPlace);
    chronotype = blankToNull(chronotype);
    biggestDistraction = blankToNull(biggestDistraction);
    motivationReason = blankToNull(motivationReason);
    goalReward = blankToNull(goalReward);
  }

  // 같은 답을 PATCH 요청 모양으로 옮깁니다. Service 는 두 요청을 같은 방법(PATCH 모양)으로 처리합니다.
  public OnboardingUpdateRequest toUpdateRequest() {
    return new OnboardingUpdateRequest(
        grade,
        targetExam,
        targetExamDate,
        targetUniversity,
        targetScore,
        wakeUpTime,
        sleepTime,
        schoolEndTime,
        academyEndTime,
        studyPlace,
        subjectLevels,
        pastFailureCauses,
        chronotype,
        maxFocusMinutes,
        biggestDistraction,
        dailyAvailableMinutes,
        motivationReason,
        goalReward);
  }

  // 앞뒤 공백을 지웁니다. null 은 그대로 null 입니다.
  static String trim(String value) {
    return value == null ? null : value.trim();
  }

  // 앞뒤 공백을 지우고, 남은 것이 없으면 null 로 바꿉니다.
  static String blankToNull(String value) {
    String trimmed = trim(value);
    return trimmed == null || trimmed.isEmpty() ? null : trimmed;
  }
}
