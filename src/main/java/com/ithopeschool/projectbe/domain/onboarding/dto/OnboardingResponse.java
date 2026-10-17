package com.ithopeschool.projectbe.domain.onboarding.dto;

import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.SubjectLevel;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// 내 설문 답을 FE 에 알려 주는 모양입니다. FE 의 OnboardingAnswers 와 같은 이름에 세 칸을 더했습니다.
//   { "id": "4", "answeredAt": 1790000000000,
//     "grade": "고2", "targetExam": "수능", "targetExamDate": "2027-11-18", ...,
//     "subjectLevels": [ { "subjectId": "3", "grade": 2, "score": null, "confidence": 4 } ],
//     "pastFailureCauses": [ "weekend", "phone" ],
//     "chronotype": "night", ..., "dailyAvailableMinutes": 180, ...,
//     "firstSelfAwareness": { "chronotype": "morning", "maxFocusMinutes": 50,
//                             "biggestDistraction": "폰·SNS", "dailyAvailableMinutes": 240 } }
// - 더한 칸: id(이 설문의 번호) · answeredAt(답한 시각, 밀리초 숫자) · firstSelfAwareness(처음 답한 자기 인식)
// - 비어 있는 답은 빼지 않고 null 로 보냅니다. 목록은 비어 있으면 [] 입니다.
// - 누구의 설문인지(userId)는 담지 않습니다.
public record OnboardingResponse(
    String id,
    long answeredAt,
    String grade,
    String targetExam,
    LocalDate targetExamDate,
    String targetUniversity,
    String targetScore,
    String wakeUpTime,
    String sleepTime,
    String schoolEndTime,
    String academyEndTime,
    String studyPlace,
    List<SubjectLevelResponse> subjectLevels,
    List<String> pastFailureCauses,
    String chronotype,
    Integer maxFocusMinutes,
    String biggestDistraction,
    int dailyAvailableMinutes,
    String motivationReason,
    String goalReward,
    SelfAwareness firstSelfAwareness) {

  // 과목 하나의 현재 수준. 등급과 점수 중 하나는 null 입니다.
  public record SubjectLevelResponse(
      String subjectId, Integer grade, Integer score, int confidence) {}

  // 자기 인식 네 가지. 아직 한 번도 답하지 않은 것은 null 입니다.
  public record SelfAwareness(
      String chronotype,
      Integer maxFocusMinutes,
      String biggestDistraction,
      Integer dailyAvailableMinutes) {}

  // LocalTime 을 그냥 글자로 바꾸면 "07:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  // answer: 지금 내 설문 / firstSelfAwareness: Service 가 찾은 처음 답한 자기 인식
  // mySubjectIds: 지금 있는 내 과목 번호들. 그 뒤에 지운 과목의 수준은 보여 주지 않습니다.
  public static OnboardingResponse from(
      OnboardingAnswer answer, SelfAwareness firstSelfAwareness, Set<Long> mySubjectIds) {
    // 과목 번호 순서로 줍니다. (과목 목록의 순서와 같습니다)
    List<SubjectLevelResponse> levels = new ArrayList<>();
    List<SubjectLevel> sorted = new ArrayList<>(answer.getSubjectLevels());
    sorted.sort(Comparator.comparing(SubjectLevel::getSubjectId));
    for (SubjectLevel level : sorted) {
      if (mySubjectIds.contains(level.getSubjectId())) {
        levels.add(
            new SubjectLevelResponse(
                String.valueOf(level.getSubjectId()),
                level.getGrade(),
                level.getScore(),
                level.getConfidence()));
      }
    }

    // EnumSet 은 enum 에 적은 순서(= FE 의 보기 순서)로 꺼내집니다.
    List<String> causes = new ArrayList<>();
    for (FailureCause cause : answer.getPastFailureCauses()) {
      causes.add(cause.getValue());
    }

    return new OnboardingResponse(
        String.valueOf(answer.getId()),
        answer.getAnsweredAt().toEpochMilli(),
        answer.getGrade().getValue(),
        answer.getTargetExam(),
        answer.getTargetExamDate(),
        answer.getTargetUniversity(),
        answer.getTargetScore(),
        format(answer.getWakeUpTime()),
        format(answer.getSleepTime()),
        format(answer.getSchoolEndTime()),
        format(answer.getAcademyEndTime()),
        answer.getStudyPlace() == null ? null : answer.getStudyPlace().getValue(),
        levels,
        causes,
        answer.getChronotype() == null ? null : answer.getChronotype().getValue(),
        answer.getMaxFocusMinutes(),
        answer.getBiggestDistraction() == null ? null : answer.getBiggestDistraction().getValue(),
        answer.getDailyAvailableMinutes(),
        answer.getMotivationReason(),
        answer.getGoalReward(),
        firstSelfAwareness);
  }

  private static String format(LocalTime time) {
    return time == null ? null : time.format(HOUR_MINUTE);
  }
}
