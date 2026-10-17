package com.ithopeschool.projectbe.domain.school.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 내 학교 학사일정 응답입니다. (이슈 #282)
//   { "school": { "code": "7530560", "name": "한국디지털미디어고등학교" },
//     "fetchedAt": 1792233122907,
//     "events": [ { "date": "2026-12-09", "name": "2학기 2차 정기시험",
//                   "content": null, "grades": [], "exam": true } ] }
// - school 이 null 이면 가입 때 학교를 안 고른 것입니다. events 는 빈 목록입니다.
// - fetchedAt: NEIS 에서 마지막으로 잘 받은 시각(밀리초). 한 번도 못 받았으면 null
// - grades: 해당 학년들. 빈 목록이면 학년 구분이 없는 행사입니다. (FE 가 내 학년으로 거릅니다)
// - exam: 시험인지 (서버 코드가 이름으로 판정: 정기시험 · 중간고사 · 기말고사 · 학력평가 · 모의평가 등)
public record SchoolEventsResponse(School school, Long fetchedAt, List<Event> events) {

  public record School(String code, String name) {}

  public record Event(
      LocalDate date, String name, String content, List<Integer> grades, boolean exam) {}

  public static SchoolEventsResponse noSchool() {
    return new SchoolEventsResponse(null, null, List.of());
  }

  public static SchoolEventsResponse of(
      String code, String name, Instant fetchedAt, List<Event> events) {
    return new SchoolEventsResponse(
        new School(code, name), fetchedAt == null ? null : fetchedAt.toEpochMilli(), events);
  }
}
