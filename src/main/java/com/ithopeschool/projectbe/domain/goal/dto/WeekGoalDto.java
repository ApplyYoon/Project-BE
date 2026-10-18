package com.ithopeschool.projectbe.domain.goal.dto;

import com.ithopeschool.projectbe.domain.goal.WeeklyGoal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

// "이번 주 목표" 주소들이 주고받는 모양입니다. (이슈 #88, FE 의 src/lib/weeklyGoal.ts 와 같은 이름)
//   종류 kind:   completion(완주율) · startDelay(시작 지연) · planRate(계획 채우기) · keep(지난주만큼)
//   상태 status: achieved(달성) · missed(미달) · unknown(기록이 없어 알 수 없음)
//   target · baseline: 시작 지연이면 분, 나머지는 %
public final class WeekGoalDto {

  private WeekGoalDto() {}

  // 목표 후보 하나 (제안)
  //   { "kind": "completion", "target": 65, "baseline": 55,
  //     "title": "완주율 65% 넘기기", "reason": "지난주 완주율은 55% 였어. 10%p 만 올려 보자." }
  public record Candidate(String kind, int target, int baseline, String title, String reason) {

    public static Candidate from(WeeklyGoal goal) {
      return new Candidate(
          kindValue(goal.kind()), goal.target(), goal.baseline(), goal.title(), goal.reason());
    }
  }

  // 이번 주 목표 제안. candidates 의 맨 앞이 먼저 보여 줄 목표이고, "다른 걸로" 를 누르면 다음 후보입니다.
  // 지난주 기록이 모자라거나(목표가 있는 타이머 5번 미만) 안전 브레이크 · 시험 기간이면 빈 배열입니다.
  public record Proposal(LocalDate weekStart, List<Candidate> candidates) {}

  // 이번 주 목표 저장 요청. 제안의 후보 하나를 그대로 보냅니다.
  //   { "kind": "completion", "target": 65, "baseline": 55 }
  public record SaveRequest(
      @NotBlank(message = "목표 종류를 골라 주세요") String kind,
      @NotNull(message = "목표 숫자를 보내 주세요")
          @Min(value = 0, message = "목표 숫자는 0 이상이어야 해요")
          @Max(value = 600, message = "목표 숫자는 600 이하로 보내 주세요")
          Integer target,
      @NotNull(message = "지난주 숫자를 보내 주세요")
          @Min(value = 0, message = "지난주 숫자는 0 이상이어야 해요")
          @Max(value = 600, message = "지난주 숫자는 600 이하로 보내 주세요")
          Integer baseline) {}

  // 정한 목표 하나와 그 주의 상태
  //   { "weekStart": "2026-10-12", "kind": "completion", "target": 65, "baseline": 55,
  //     "title": "완주율 65% 넘기기", "status": "missed", "current": 60, "percent": 92, "judged": true }
  // - judged: 주가 끝나 판정을 적었으면 true. 아직 진행 중인 주면 false 이고, status · current · percent 는 지금까지의 기록으로 본
  // 값입니다.
  // - current: 그 주의 숫자. 계산할 수 없으면 null / percent: 진행 막대 길이(0 ~ 100)
  public record Item(
      LocalDate weekStart,
      String kind,
      int target,
      int baseline,
      String title,
      String status,
      Integer current,
      int percent,
      boolean judged) {}

  // FE 와 주고받는 종류 글자
  public static String kindValue(WeeklyGoal.Kind kind) {
    return switch (kind) {
      case COMPLETION -> "completion";
      case START_DELAY -> "startDelay";
      case PLAN_RATE -> "planRate";
      case KEEP -> "keep";
    };
  }

  // FE 가 보낸 종류 글자를 종류로 바꿉니다. 정해진 글자가 아니면 null 입니다.
  public static WeeklyGoal.Kind kindOf(String value) {
    for (WeeklyGoal.Kind kind : WeeklyGoal.Kind.values()) {
      if (kindValue(kind).equals(value)) {
        return kind;
      }
    }
    return null;
  }

  // FE 와 주고받는 상태 글자
  public static String statusValue(WeeklyGoal.Status status) {
    return switch (status) {
      case ACHIEVED -> "achieved";
      case MISSED -> "missed";
      case UNKNOWN -> "unknown";
    };
  }
}
