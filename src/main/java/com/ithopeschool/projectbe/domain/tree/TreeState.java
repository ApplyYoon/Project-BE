package com.ithopeschool.projectbe.domain.tree;

import java.util.List;

// 홈 화면 "내 나무" 의 상태를 계산합니다. (이슈 #64, 기획서 v2 5장 「당근 상세」·「채찍 상세」)
// 나무는 시들 수는 있어도 절대 죽지 않습니다. 다시 살아나는 경험 자체가 당근이 됩니다.
//
// 규칙
//   - 줄기(단계): 누적 순공시간 10시간마다 한 단계씩 자랍니다.
//       씨앗(0시간~) → 새싹(10시간~) → 작은 나무(20시간~) → 큰 나무(30시간~)
//   - 잎: 쓴 회고 수 + 받은 인사이트 수
//   - 열매: 나 사용설명서 항목 수 (효과가 확인된 나만의 방법)
//   - 시듦: 관리 강도가 3(심리적 채찍) 이상인데 오늘 아직 공부를 안 했으면 시듭니다.
//           오늘 1분이라도 공부하면 바로 살아납니다. 시들어도 단계·잎·열매는 그대로입니다.
//
//   누적 0분                  →  씨앗,      다음 단계까지 600분
//   누적 1,500분(25시간)       →  작은 나무,  다음 단계까지 300분, 진행 50%
//   누적 2,000분(33시간)       →  큰 나무,    다 자람
//   관리 강도 3, 오늘 0분      →  시듦
//   관리 강도 3, 오늘 25분     →  안 시듦 (다시 공부해서 살아남)
//
// FE 의 getTreeState (src/lib/tree.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 나무가 어긋나지 않습니다.
//
// stage: 단계 번호. 0 씨앗 ~ 3 큰 나무 / stageName: 단계 이름. 예) "작은 나무"
// leafCount: 잎 개수 / fruitCount: 열매 개수
// isWilted: 시들었으면 true / isFullGrown: 큰 나무까지 다 자랐으면 true (다음 단계가 없음)
// minutesToNextStage: 다음 단계까지 남은 순공시간(분). 다 자랐으면 0
// progressPercent: 지금 단계 안에서 얼마나 자랐는지(0~100). 진행 막대에 씁니다. 다 자랐으면 100
public record TreeState(
    int stage,
    String stageName,
    int leafCount,
    int fruitCount,
    boolean isWilted,
    boolean isFullGrown,
    int minutesToNextStage,
    double progressPercent) {

  // 나무 단계 이름. 목록의 순서가 곧 단계입니다. (0번 = 씨앗)
  public static final List<String> STAGE_NAMES = List.of("씨앗", "새싹", "작은 나무", "큰 나무");

  // 한 단계 자라는 데 필요한 순공시간(분). 규칙을 바꾸고 싶으면 이 숫자만 고치면 됩니다.
  public static final int MINUTES_PER_STAGE = 10 * 60;

  // 이 관리 강도부터 나무가 시듭니다. (3 = 심리적 채찍)
  public static final int WILT_CARE_LEVEL = 3;

  // 마지막 단계 번호 (큰 나무 = 3)
  private static final int LAST_STAGE = STAGE_NAMES.size() - 1;

  // 공부 기록으로 나무 상태를 계산합니다.
  //
  // totalStudyMinutes: 지금까지 공부한 순공시간 합계(분)
  // todayStudyMinutes: 오늘 공부한 순공시간(분). 시든 나무를 살릴 때 씁니다.
  // careLevel: 오늘의 관리 강도 (0 회복 ~ 4 강제적 채찍, CareLevel)
  // reflectionCount: 쓴 회고 수 / insightCount: 받은 인사이트 수 / manualItemCount: 나 사용설명서 항목 수
  public static TreeState of(
      int totalStudyMinutes,
      int todayStudyMinutes,
      int careLevel,
      int reflectionCount,
      int insightCount,
      int manualItemCount) {
    int totalMinutes = toSafeCount(totalStudyMinutes);
    int todayMinutes = toSafeCount(todayStudyMinutes);

    // 10시간마다 한 단계. 큰 나무보다 더 커지지는 않습니다. (정수끼리 나누면 소수점이 버려집니다)
    int stage = Math.min(LAST_STAGE, totalMinutes / MINUTES_PER_STAGE);
    boolean isFullGrown = stage == LAST_STAGE;

    // 지금 단계에서 이미 자란 양(분)
    int minutesInStage = totalMinutes - stage * MINUTES_PER_STAGE;

    return new TreeState(
        stage,
        STAGE_NAMES.get(stage),
        toSafeCount(reflectionCount) + toSafeCount(insightCount),
        toSafeCount(manualItemCount),
        careLevel >= WILT_CARE_LEVEL && todayMinutes == 0,
        isFullGrown,
        isFullGrown ? 0 : MINUTES_PER_STAGE - minutesInStage,
        // 정수끼리 나누면 소수점이 버려져서, 한쪽을 소수(double)로 바꿔서 나눕니다. (FE 와 같은 순서)
        isFullGrown ? 100 : (double) minutesInStage / MINUTES_PER_STAGE * 100);
  }

  // 음수 같은 이상한 값을 0 으로 바꿉니다. (깨진 기록 때문에 나무가 이상해지지 않게)
  private static int toSafeCount(int value) {
    return Math.max(0, value);
  }
}
