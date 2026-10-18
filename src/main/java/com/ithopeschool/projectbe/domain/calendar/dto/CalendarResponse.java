package com.ithopeschool.projectbe.domain.calendar.dto;

import java.time.LocalDate;
import java.util.List;

// 달력 한 달치의 모양입니다. (이슈 #235, 기획서 4.6 「분석」)
//   { "month": "2026-10",
//     "days": [ { "date": "2026-10-01", "done": 2, "late": 1, "missed": 1, "rest": 0,
//                 "emotions": ["CALM"],
//                 "misses": [ { "cardId": "7", "title": "밤 수학 오답 정리", "result": "failed",
//                               "cause": "피곤·수면", "note": null } ] } ],
//     "recent": { "from": "2026-09-21", "to": "2026-10-18", "done": 40, "total": 52, "rate": 77 } }
// - days 는 그 달의 1일부터 마지막 날까지 하루도 빠짐없이 들어 있습니다. 카드도 체크인도 없는 날은 0 과 빈 목록입니다.
// - done · late · missed · rest 는 같은 무게로 셉니다. (성공한 날만 크게 보이지 않게, 기획서의 "선택적 기억")
//     done: 실행 · 중도 종료 · 사후 입력 / late: 지연 실행 / missed: 미루기 · 실패 · 미처리 / rest: 휴식
// - emotions 는 그날 체크인의 감정 태그(STUCK · ANXIOUS · TIRED · ANNOYED · CALM · MOTIVATED). 체크인을 안 했으면 []
// - misses 는 그날 못 한 카드와 고른 사유입니다. 날짜를 누르면 보여 줍니다. 사유를 안 골랐으면 cause 가 null
// - recent 는 최근 4주(오늘 포함 28일)의 실행률입니다. 연속 일수(스트릭) 대신 씁니다. 휴식은 빼고 셉니다.
public record CalendarResponse(String month, List<Day> days, Recent recent) {

  // 하루 한 칸
  public record Day(
      LocalDate date,
      int done,
      int late,
      int missed,
      int rest,
      List<String> emotions,
      List<Miss> misses) {}

  // 못 한 카드 하나. result: postponed · failed · unhandled / cause: 고른 원인 글자 / note: 미루거나 실패로 기록할 때 적은
  // 사유
  public record Miss(String cardId, String title, String result, String cause, String note) {}

  // 최근 4주 실행률. done: 실행 · 지연 · 중도 종료 · 사후 입력 / total: 휴식을 뺀 카드 수
  // rate: done ÷ total × 100 (반올림). 셀 카드가 없으면 null
  public record Recent(LocalDate from, LocalDate to, int done, int total, Integer rate) {}
}
