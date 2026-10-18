package com.ithopeschool.projectbe.domain.school.dto;

import java.time.LocalDate;
import java.util.List;

// 오늘 학교 정보 응답입니다. 홈의 "오늘" 칸과 AI 코치 채팅이 씁니다.
//   { "date": "2026-10-19",
//     "school": { "code": "7530560", "name": "한국디지털미디어고등학교" },
//     "timetable": [ { "period": 1, "subject": "미적분Ⅰ" }, { "period": 2, "subject": "문학" } ],
//     "timetablePartial": false,
//     "meals": [ { "date": "2026-10-19", "kind": "중식", "dishes": ["흑미밥", "나주곰탕"] } ],
//     "events": [ { "date": "2026-12-09", "name": "2학기 2차 정기시험",
//                   "grades": [1, 2], "exam": true, "dDay": 51 } ] }
// - school 이 null 이면 가입 때 학교를 안 고른 것입니다. timetable 은 null, meals · events 는 빈 목록입니다.
// - timetable: 오늘 우리 반 시간표(교시 순서). 학년 · 반을 안 보냈으면 null, 수업이 없는 날은 빈 목록입니다.
// - timetablePartial: true 면 뒤 교시가 잘렸을 수 있습니다. (서버에 NEIS 키가 없으면 NEIS 가 5교시까지만 줌)
// - meals: 오늘과 내일의 급식. 급식이 없는 날(주말 · 방학)이나 NEIS 가 멈췄을 때는 그날 것이 빠집니다.
// - events: 오늘부터 30일 안의 학사일정, 앞에서부터 10개까지. grades 가 빈 목록이면 학년 구분이 없는 행사입니다.
//   dDay 는 오늘부터 며칠 남았는지 (오늘이면 0)
public record SchoolTodayResponse(
    LocalDate date,
    SchoolEventsResponse.School school,
    List<Period> timetable,
    boolean timetablePartial,
    List<Meal> meals,
    List<Event> events) {

  public record Period(int period, String subject) {}

  public record Meal(LocalDate date, String kind, List<String> dishes) {}

  public record Event(LocalDate date, String name, List<Integer> grades, boolean exam, long dDay) {}

  public static SchoolTodayResponse noSchool(LocalDate date) {
    return new SchoolTodayResponse(date, null, null, false, List.of(), List.of());
  }

  public static SchoolTodayResponse of(
      LocalDate date,
      String code,
      String name,
      List<Period> timetable,
      boolean timetablePartial,
      List<Meal> meals,
      List<Event> events) {
    return new SchoolTodayResponse(
        date,
        new SchoolEventsResponse.School(code, name),
        timetable,
        timetablePartial,
        meals,
        events);
  }
}
