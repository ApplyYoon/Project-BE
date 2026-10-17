package com.ithopeschool.projectbe.domain.goal.dto;

import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalTargetSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 목표 하나를 FE 에 알려 주는 모양입니다. FE 의 Goal 과 같은 이름을 씁니다.
//   { "id": "4", "kind": "score", "title": "11월 학평 수학 4등급", "dDay": "2026-11-19",
//     "currentScore": "5등급", "totalAmount": null, "amountUnit": null,
//     "benefit": "…", "cost": "…", "obstacle": "…", "plan": "…",
//     "status": "active", "result": null, "givenUpReason": null,
//     "history": [ { "title": "11월 학평 수학 3등급", "dDay": "2026-11-19",
//                    "currentScore": "5등급", "totalAmount": null, "amountUnit": null,
//                    "changedAt": 1790000000000, "reason": "시간이 모자라서" } ],
//     "createdAt": 1789000000000 }
// - id 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다. 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - FE 의 progressLog(완주형 진척) · lastCheckedAt(2주 점검)은 아직 서버에 없습니다. (FE#403)
// - 누구의 목표인지(userId)는 담지 않습니다.
public record GoalResponse(
    String id,
    String kind,
    String title,
    LocalDate dDay,
    String currentScore,
    Integer totalAmount,
    String amountUnit,
    String benefit,
    String cost,
    String obstacle,
    String plan,
    String status,
    String result,
    String givenUpReason,
    List<Snapshot> history,
    long createdAt) {

  // 고치기 전의 목표값 한 줄 (FE 의 GoalTargetSnapshot)
  public record Snapshot(
      String title,
      LocalDate dDay,
      String currentScore,
      Integer totalAmount,
      String amountUnit,
      long changedAt,
      String reason) {

    public static Snapshot from(GoalTargetSnapshot snapshot) {
      return new Snapshot(
          snapshot.getTitle(),
          snapshot.getDDay(),
          snapshot.getCurrentScore(),
          snapshot.getTotalAmount(),
          snapshot.getAmountUnit(),
          snapshot.getChangedAt().toEpochMilli(),
          snapshot.getReason());
    }
  }

  // 목표의 고친 기록을 오래된 것부터 응답 모양으로 바꿉니다. (이력 주소도 같이 씁니다)
  public static List<Snapshot> historyOf(Goal goal) {
    List<Snapshot> history = new ArrayList<>();
    for (GoalTargetSnapshot snapshot : goal.getHistory()) {
      history.add(Snapshot.from(snapshot));
    }
    return history;
  }

  public static GoalResponse from(Goal goal) {
    return new GoalResponse(
        String.valueOf(goal.getId()),
        goal.getKind().toValue(),
        goal.getTitle(),
        goal.getDDay(),
        goal.getCurrentScore(),
        goal.getTotalAmount(),
        goal.getAmountUnit(),
        goal.getBenefit(),
        goal.getCost(),
        goal.getObstacle(),
        goal.getPlan(),
        goal.getStatus().toValue(),
        goal.getResult(),
        goal.getGivenUpReason(),
        historyOf(goal),
        goal.getCreatedAt().toEpochMilli());
  }
}
