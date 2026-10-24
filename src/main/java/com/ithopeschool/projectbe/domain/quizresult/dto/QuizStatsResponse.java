package com.ithopeschool.projectbe.domain.quizresult.dto;

import java.time.LocalDate;
import java.util.List;

// 통계 탭이 그리는 데 쓰는 기간 통계입니다. 비율(%)은 FE 가 맞은 개수 ÷ 전체 개수로 계산합니다.
//   { "from": "2026-10-19", "to": "2026-10-25",
//     "subjects": [ { "subjectId": 3, "name": "수학", "color": "blue",
//       "days": [ { "date": "2026-10-19", "correctCount": 8, "totalCount": 10,
//                   "studyMinutes": 120 }, … ],
//       "units": [ { "unit": "함수", "correctCount": 30, "totalCount": 40, "averageSeconds": 48,
//                    "days": [ { "date": "2026-10-19", "correctCount": 8, "totalCount": 10 } ] } ]
//     } ] }
// - days 는 기간의 모든 날짜를 빠짐없이 담습니다. (퀴즈를 안 푼 날은 0, 0)
// - 단원별 공부 시간은 없습니다. 타이머 기록에는 단원이 없어서 과목별로만 셉니다.
public record QuizStatsResponse(LocalDate from, LocalDate to, List<SubjectStats> subjects) {

  // 과목 하나의 기간 통계
  public record SubjectStats(
      Long subjectId, String name, String color, List<DayStats> days, List<UnitStats> units) {}

  // 과목의 하루. studyMinutes 는 그날 시작한 이 과목 공부 기록의 순공시간(분)입니다.
  public record DayStats(LocalDate date, int correctCount, int totalCount, int studyMinutes) {}

  // 단원 하나의 기간 통계. averageSeconds 는 한 문제를 푸는 데 걸린 평균 시간(초, 반올림)입니다.
  public record UnitStats(
      String unit, int correctCount, int totalCount, int averageSeconds, List<UnitDayStats> days) {}

  // 단원의 하루
  public record UnitDayStats(LocalDate date, int correctCount, int totalCount) {}
}
