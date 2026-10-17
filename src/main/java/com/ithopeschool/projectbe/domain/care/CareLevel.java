package com.ithopeschool.projectbe.domain.care;

// 오늘의 관리 강도(AI 가 얼마나 세게 말하는지)를 정합니다. (이슈 #62, 기획서 v2 5장 「원칙」·「강도 단계」)
//
//   0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍
//
// 메시지·통화·나무·잠금이 모두 이 단계를 보고 움직입니다.
// 어제 결과에 따른 대응은 CareDecision.decide (이슈 #55) 가 정한 CareAction 을 그대로 받습니다.
//
// 위에서부터 차례로 따집니다.
//
//   1) 안전 브레이크가 켜졌다           →  무조건 0 (회복). 아래는 보지 않습니다.
//   2) 대응이 회복 우선(RECOVERY_FIRST)  →  어제가 몇 단계였든 0
//   3) 그 외 대응은 어제 단계에서 칸 수만큼 움직입니다.
//        채찍(WHIP)                →  한 칸 올림  (하루에 한 단계까지만 오릅니다)
//        당근 3종(CARROT_*)        →  두 칸 내림  (성공하면 바로 내려갑니다)
//        목표 하향(GOAL_DOWN)       →  한 칸 내림
//        난이도만 낮춤(KEEP_EASIER) →  그대로
//   4) 사용자가 정한 최대 한도를 넘으면 한도로 낮춥니다. (한도는 절대 넘지 않습니다)
//
//   [어제 단계, 대응, 한도, 브레이크] → 오늘 단계
//   [3, 채찍, 4, 켜짐]         → 0   (브레이크가 가장 먼저)
//   [3, 회복 우선, 4, 꺼짐]    → 0
//   [2, 채찍, 4, 꺼짐]         → 3   (4 가 되지 않습니다. 하루에 한 칸만)
//   [4, 당근·목표 상향, 4, 꺼짐] → 2   (두 칸 내림)
//   [1, 당근·목표 상향, 4, 꺼짐] → 0   (0 에서 멈춥니다)
//   [4, 목표 하향, 4, 꺼짐]     → 3
//   [2, 난이도만 낮춤, 4, 꺼짐]  → 2
//   [2, 채찍, 2, 꺼짐]         → 2   (3 이 되지만 한도 때문에 2)
//   [4, 난이도만 낮춤, 1, 꺼짐]  → 1   (한도를 낮춘 날은 바로 한도로 내려갑니다)
//
// 처음 쓰는 날(어제 단계가 없음)은 firstDay 를 부릅니다. 1단계(당근)에서 시작합니다.
//
// 칸 수(당근 두 칸·목표 하향 한 칸·난이도만 낮추면 그대로)와 처음 쓰는 날의 단계는 2026-10-11 에 확정한 값입니다.
// FE 의 getCareLevel (src/lib/careLevel.ts) 과 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 단계가 어긋나지 않습니다.
public final class CareLevel {

  // 가장 낮은 단계 (회복)
  public static final int MIN_LEVEL = 0;

  // 가장 높은 단계 (강제적 채찍)
  public static final int MAX_LEVEL = 4;

  // 처음 쓰는 날의 단계 (당근)
  public static final int FIRST_DAY_LEVEL = 1;

  // 채찍일 때 하루에 올라가는 칸 수. "하루에 한 단계까지만 오른다" 는 규칙 때문에 1 입니다.
  public static final int STEPS_UP_ON_WHIP = 1;

  // 당근(잘 해냄)일 때 한 번에 내려가는 칸 수
  public static final int STEPS_DOWN_ON_CARROT = 2;

  // 목표를 낮춘 날에 내려가는 칸 수
  public static final int STEPS_DOWN_ON_GOAL_DOWN = 1;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CareLevel() {}

  // 오늘의 관리 강도(0~4)를 정합니다.
  //
  // yesterdayLevel: 어제의 관리 강도 (0~4)
  // action: 어제 결과에 따라 정한 대응 (CareDecision.decide 결과의 action)
  // maxLevel: 사용자가 정한 최대 한도 (0~4). 이 단계를 넘는 강도는 절대 쓰지 않습니다.
  // isSafetyBrakeOn: 안전 브레이크가 켜졌는지 (위기 표현·시험 기간·기분 저하 등)
  public static int of(
      int yesterdayLevel, CareAction action, int maxLevel, boolean isSafetyBrakeOn) {
    checkLevel("어제 단계", yesterdayLevel);
    checkLevel("최대 한도", maxLevel);
    if (action == null) {
      throw new IllegalArgumentException("대응(action)이 있어야 오늘의 단계를 정할 수 있어요");
    }

    // 1) 안전 브레이크: 대응·한도보다 먼저 봅니다.
    if (isSafetyBrakeOn) {
      return MIN_LEVEL;
    }

    // 2) 회복 우선이면 어제 단계와 상관없이 0
    if (action == CareAction.RECOVERY_FIRST) {
      return MIN_LEVEL;
    }

    // 3) 어제 단계에서 칸 수만큼 움직이고, 0~4 밖으로 나가지 않게 합니다.
    int moved = yesterdayLevel + stepsOf(action);
    int todayLevel = Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, moved));

    // 4) 최대 한도를 넘지 않게 자릅니다.
    return Math.min(todayLevel, maxLevel);
  }

  // 처음 쓰는 날의 관리 강도를 정합니다. 어제 단계도, 어제 결과도 없는 날입니다.
  // 1단계(당근)에서 시작하고, 안전 브레이크와 최대 한도는 똑같이 지킵니다.
  //
  //   [한도 4, 브레이크 꺼짐] → 1
  //   [한도 0, 브레이크 꺼짐] → 0   (한도 0 이면 항상 회복)
  //   [한도 4, 브레이크 켜짐] → 0
  public static int firstDay(int maxLevel, boolean isSafetyBrakeOn) {
    checkLevel("최대 한도", maxLevel);

    if (isSafetyBrakeOn) {
      return MIN_LEVEL;
    }
    return Math.min(FIRST_DAY_LEVEL, maxLevel);
  }

  // 대응별로 어제 단계에서 몇 칸 움직이는지 돌려줍니다. 양수는 올림, 음수는 내림입니다.
  // 회복 우선(RECOVERY_FIRST)은 칸 수가 아니라 무조건 0단계라서 여기까지 오지 않습니다.
  private static int stepsOf(CareAction action) {
    return switch (action) {
      case WHIP -> STEPS_UP_ON_WHIP;
      case CARROT_RAISE, CARROT_CHANGE_STRATEGY, CARROT_KEEP -> -STEPS_DOWN_ON_CARROT;
      case GOAL_DOWN -> -STEPS_DOWN_ON_GOAL_DOWN;
      case KEEP_EASIER, RECOVERY_FIRST -> 0;
    };
  }

  // 단계가 0~4 안인지 확인합니다. 밖이면 조용히 고치지 않고 에러를 냅니다. (잘못된 값이 들어온 곳을 바로 찾을 수 있게)
  private static void checkLevel(String name, int level) {
    if (level < MIN_LEVEL || level > MAX_LEVEL) {
      throw new IllegalArgumentException(
          name + "는 " + MIN_LEVEL + " ~ " + MAX_LEVEL + " 사이여야 해요. 받은 값: " + level);
    }
  }
}
