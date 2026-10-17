package com.ithopeschool.projectbe.domain.session.dto;

import com.ithopeschool.projectbe.domain.session.StudyTotal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// "오늘 순공시간" 을 FE 에 알려 주는 모양입니다. (타이머 화면의 "오늘 총 공부 시간")
//   { "date": "2026-10-05", "studyMinutes": 100,
//     "subjects": [ { "subjectId": "3", "studyMinutes": 70 },
//                   { "subjectId": "4", "studyMinutes": 30 } ] }
// - date: 서버가 "오늘" 로 본 날짜입니다. 하루는 새벽 4시에 바뀝니다. (docs/API.md 2-4)
// - studyMinutes: 순공시간(분). 노는 기록은 들어 있지 않습니다.
// - subjects: 오늘 공부한 과목별 순공시간(분). 공부한 과목이 없으면 빈 배열입니다.
// - subjectId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
public record StudyTotalResponse(
    LocalDate date, int studyMinutes, List<SubjectTotalResponse> subjects) {

  // 과목 하나의 순공시간입니다.
  public record SubjectTotalResponse(String subjectId, int studyMinutes) {}

  public static StudyTotalResponse of(LocalDate date, StudyTotal total) {
    List<SubjectTotalResponse> subjects = new ArrayList<>();
    for (StudyTotal.SubjectTotal subject : total.subjects()) {
      subjects.add(
          new SubjectTotalResponse(String.valueOf(subject.subjectId()), subject.studyMinutes()));
    }
    return new StudyTotalResponse(date, total.studyMinutes(), subjects);
  }
}
