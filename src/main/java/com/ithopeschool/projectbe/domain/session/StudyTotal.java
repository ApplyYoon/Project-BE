package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 공부 기록들의 "순공시간 합계" 와 "과목별 합계" 를 계산합니다. (이슈 #14 "오늘 순공시간")
// 타이머 화면의 "오늘 총 공부 시간" 이 이 숫자를 씁니다.
//
// 정해 둔 규칙
//   1. 기록 하나의 순공 = 끝 − 시작 − 일시정지한 시간. 계산은 StudyTime.studyMillis 가 합니다.
//   2. 노는 기록(PLAY)은 공부가 아니라서 합계 어디에도 넣지 않습니다. (Project#12 규칙 2)
//   3. 분은 기록들의 순공(밀리초)을 모두 더한 뒤 "마지막에 한 번만" 반올림합니다. 기록마다 반올림하지 않습니다.
//      과목별 분도 그 과목의 밀리초를 더한 뒤 한 번만 반올림합니다.
//      그래서 과목별 분을 더한 값이 전체 분과 1~2분 다를 수 있습니다. (아래 예시)
//   4. 과목별 목록은 그 과목으로 공부한 기록이 있는 과목만 담고, 과목 번호가 작은 순서입니다.
//
// 예시 (입력 → 결과)
//   기록 없음                                          →  0분, 과목 없음
//   수학 19:00 ~ 20:10 (일시정지 40분)                  →  30분, 수학 30분
//   수학 50분 + 영어 30분 + 수학 20분                    →  100분, 수학 70분, 영어 30분
//   수학 60분 + 노는 기록 30분                           →  60분, 수학 60분 (노는 30분은 빠짐)
//   수학 20분 24초 + 영어 20분 24초 + 국어 20분 24초       →  61분, 수학 20분, 영어 20분, 국어 20분
//     (전체는 61.2분 → 61. 과목별은 각각 20.4분 → 20 이라서 더하면 60)
//
// DaySummary.of(...).studyMinutes() 와 같은 규칙이라 같은 기록으로 계산하면 같은 값이 나옵니다.
// "어느 날의 기록인지" 는 이 계산기가 가리지 않습니다. 부르는 쪽에서 그날 시작한 기록만 골라 넘깁니다. (DayRule)
//
// studyMinutes: 순공시간 합계(분)
// subjects: 과목별 순공시간(분). 과목 번호가 작은 순서
public record StudyTotal(int studyMinutes, List<SubjectTotal> subjects) {

  // subjectId: 과목 번호(subjects.id) / studyMinutes: 그 과목의 순공시간(분)
  public record SubjectTotal(Long subjectId, int studyMinutes) {}

  public static StudyTotal of(List<StudySession> sessions) {
    long totalMillis = 0;
    // 과목 번호가 작은 순서로 꺼내려고 TreeMap 을 씁니다. (과목 번호 → 그 과목의 순공 밀리초)
    Map<Long, Long> millisBySubject = new TreeMap<>();

    for (StudySession session : sessions) {
      // 노는 기록은 공부가 아니라서 건너뜁니다. (규칙 2)
      if (session.getKind() == PlanKind.PLAY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      totalMillis += millis;

      // 공부 기록은 과목이 꼭 있습니다. (DB 검사 규칙 ck_study_sessions_study_has_subject)
      // 그래도 혹시 비어 있으면 전체 시간에만 넣고 과목별 목록에는 넣지 않습니다.
      if (session.getSubjectId() != null) {
        millisBySubject.merge(session.getSubjectId(), millis, Long::sum);
      }
    }

    // 마지막에 한 번만 반올림합니다. (규칙 3)
    List<SubjectTotal> subjects = new ArrayList<>();
    for (Map.Entry<Long, Long> entry : millisBySubject.entrySet()) {
      subjects.add(new SubjectTotal(entry.getKey(), StudyTime.toMinutes(entry.getValue())));
    }
    return new StudyTotal(StudyTime.toMinutes(totalMillis), subjects);
  }
}
