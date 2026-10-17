package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.bias.BiasHabit;
import java.time.LocalDate;

// 자주 보이는 생각 습관 하나를 기록 화면에 보여 주는 모양입니다. (BiasHabit)
//   { "key": "PLANNING_FALLACY", "label": "계획 오류",
//     "description": "\"이 정도는 금방 하지\" 하고 걸리는 시간을 짧게 잡는 생각",
//     "solution": "다음 계획은 처음 예상한 시간의 1.5배로 잡아 보기",
//     "days": 3, "lastDate": "2026-10-13", "lastEvidence": "금방 할 줄 알았다고 함" }
// - days: 최근 14일 중 이 습관이 보인 서로 다른 날의 수 (3 이상)
// - lastEvidence: 가장 최근 근거 한 줄. 없으면 null
public record BiasHabitResponse(
    String key,
    String label,
    String description,
    String solution,
    int days,
    LocalDate lastDate,
    String lastEvidence) {

  public static BiasHabitResponse from(BiasHabit habit) {
    return new BiasHabitResponse(
        habit.bias().name(),
        habit.bias().getLabel(),
        habit.bias().getDescription(),
        habit.bias().getSolution(),
        habit.days(),
        habit.lastDate(),
        habit.lastEvidence());
  }
}
