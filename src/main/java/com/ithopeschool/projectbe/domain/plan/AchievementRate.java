package com.ithopeschool.projectbe.domain.plan;

// "계획 대비 달성률" 계산기입니다. (이슈 #15, docs/API.md 5장)
// 체크인 질문·하루 요약표·이벤트 메시지·효과 판정이 모두 같은 숫자를 써야 해서, 식을 이 한 곳에만 둡니다.
//
//   달성률(%) = 순공시간(분) × 100 ÷ 그날 계획 목표 시간 합계(분)   (소수점 버림, 최대 100)
//
//   순공 190분, 목표 240분  →  79    (79.16… 에서 소수점 버림)
//   순공 239분, 목표 240분  →  99    (99.58… 이지만 반올림하지 않음)
//   순공  90분, 목표  60분  →  100   (100 을 넘으면 100)
//   순공  30분, 목표   0분  →  null  (계산 불가)
//
// FE 의 getDaySummary (src/lib/daySummary.ts) 규칙 5·6 과 같은 식입니다.
// 다른 점 하나: 계획이 없는 날에 FE 는 0 을 돌려주지만, 여기서는 "계산 불가"(null) 로 구분합니다.
public final class AchievementRate {

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private AchievementRate() {}

  // 달성률을 0~100 사이 정수로 돌려줍니다. 계산할 수 없으면 null 입니다.
  //
  // studyMinutes: 그날의 순공시간(분). 기록들의 순공(밀리초)을 모두 더한 뒤 마지막에 한 번만 반올림한 값
  // targetMinutes: 그날 계획들의 목표 시간(분)을 모두 더한 값
  public static Integer percentOf(int studyMinutes, int targetMinutes) {
    // 순공시간은 음수가 될 수 없습니다. 음수가 들어왔다면 앞에서 계산을 잘못한 것이라 바로 알립니다.
    if (studyMinutes < 0) {
      throw new IllegalArgumentException("순공시간은 0 이상이어야 합니다");
    }

    // 계획이 없거나 목표가 전부 0분이면 나눌 수 없습니다. 0% 와 헷갈리지 않게 "계산 불가"(null) 로 돌려줍니다.
    if (targetMinutes <= 0) {
      return null;
    }

    // 정수끼리 나누면 소수점이 버려집니다. (190 * 100 / 240 = 79)
    // 반올림하지 않는 이유: 99.6% 가 100% 로 보이면 "다 못 채운 날" 인데도 다 채운 날로 착각하게 됩니다.
    int percent = studyMinutes * 100 / targetMinutes;

    // 목표보다 더 많이 공부해도 100 까지만 돌려줍니다.
    return Math.min(100, percent);
  }
}
