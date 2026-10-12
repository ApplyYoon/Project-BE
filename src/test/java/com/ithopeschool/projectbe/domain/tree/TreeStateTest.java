package com.ithopeschool.projectbe.domain.tree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// "내 나무" 상태 계산 규칙(이슈 #64)을 확인합니다.
// feExample 로 시작하는 5개는 FE 함수(getTreeState) 주석의 예시를 그대로 옮긴 것입니다.
class TreeStateTest {

  // 누적 순공시간만 넣고 나머지는 평범한 값(오늘 30분 공부, 관리 강도 1, 잎·열매 없음)으로 채우는 도우미입니다.
  private static TreeState treeOf(int totalStudyMinutes) {
    return TreeState.of(totalStudyMinutes, 30, 1, 0, 0, 0);
  }

  // 관리 강도와 오늘 공부 시간만 넣는 도우미입니다. 누적은 1,500분(작은 나무)으로 고정합니다.
  private static TreeState treeWithCare(int careLevel, int todayStudyMinutes) {
    return TreeState.of(1500, todayStudyMinutes, careLevel, 0, 0, 0);
  }

  // ─── 단계: 누적 순공시간으로 자랍니다 ───

  @Test
  void feExample1NoStudyIsSeed() {
    // FE 예) 누적 0분 → 씨앗, 다음 단계까지 600분
    TreeState tree = treeOf(0);

    assertThat(tree.stage()).isEqualTo(0);
    assertThat(tree.stageName()).isEqualTo("씨앗");
    assertThat(tree.minutesToNextStage()).isEqualTo(600);
    assertThat(tree.progressPercent()).isEqualTo(0.0);
    assertThat(tree.isFullGrown()).isFalse();
  }

  @Test
  void feExample2TwentyFiveHoursIsSmallTree() {
    // FE 예) 누적 1,500분(25시간) → 작은 나무, 다음 단계까지 300분, 진행 50%
    TreeState tree = treeOf(1500);

    assertThat(tree.stage()).isEqualTo(2);
    assertThat(tree.stageName()).isEqualTo("작은 나무");
    assertThat(tree.minutesToNextStage()).isEqualTo(300);
    assertThat(tree.progressPercent()).isEqualTo(50.0);
    assertThat(tree.isFullGrown()).isFalse();
  }

  @Test
  void feExample3ThirtyThreeHoursIsFullGrown() {
    // FE 예) 누적 2,000분(33시간) → 큰 나무, 다 자람
    TreeState tree = treeOf(2000);

    assertThat(tree.stage()).isEqualTo(3);
    assertThat(tree.stageName()).isEqualTo("큰 나무");
    assertThat(tree.isFullGrown()).isTrue();
    // 다 자랐으면 남은 시간은 0, 진행은 100 입니다.
    assertThat(tree.minutesToNextStage()).isEqualTo(0);
    assertThat(tree.progressPercent()).isEqualTo(100.0);
  }

  @Test
  void growsOneStageEveryTenHours() {
    // 경계값: 10시간(600분)마다 정확히 한 단계씩 자랍니다. 1분 모자라면 아직 앞 단계입니다.
    assertThat(treeOf(599).stageName()).isEqualTo("씨앗");
    assertThat(treeOf(600).stageName()).isEqualTo("새싹");
    assertThat(treeOf(1199).stageName()).isEqualTo("새싹");
    assertThat(treeOf(1200).stageName()).isEqualTo("작은 나무");
    assertThat(treeOf(1799).stageName()).isEqualTo("작은 나무");
    assertThat(treeOf(1800).stageName()).isEqualTo("큰 나무");
  }

  @Test
  void neverGrowsPastTheLastStage() {
    // 아무리 많이 공부해도 큰 나무(3단계)에서 멈춥니다. 1,000시간이어도 4단계가 되지 않습니다.
    TreeState tree = treeOf(60_000);

    assertThat(tree.stage()).isEqualTo(3);
    assertThat(tree.stageName()).isEqualTo("큰 나무");
    assertThat(tree.isFullGrown()).isTrue();
    assertThat(tree.minutesToNextStage()).isEqualTo(0);
    assertThat(tree.progressPercent()).isEqualTo(100.0);
  }

  @Test
  void progressStartsOverAtEachStage() {
    // 진행은 "지금 단계 안에서" 얼마나 자랐는지입니다. 단계가 오르면 0 부터 다시 시작합니다.
    assertThat(treeOf(599).minutesToNextStage()).isEqualTo(1);
    assertThat(treeOf(600).minutesToNextStage()).isEqualTo(600);
    assertThat(treeOf(600).progressPercent()).isEqualTo(0.0);
    // 새싹에서 150분 더 → 4분의 1 → 25%
    assertThat(treeOf(750).progressPercent()).isEqualTo(25.0);
    assertThat(treeOf(750).minutesToNextStage()).isEqualTo(450);
  }

  @Test
  void progressIsNotRounded() {
    // 진행은 반올림하지 않은 소수 그대로입니다. (FE 와 같습니다. 화면에서 막대 길이로 씁니다)
    // 200분 ÷ 600분 = 33.33…%
    assertThat(treeOf(200).progressPercent()).isBetween(33.33, 33.34);
  }

  // ─── 시듦: 죽지 않고, 공부하면 살아납니다 ───

  @Test
  void feExample4WiltsAtLevelThreeWithoutStudyToday() {
    // FE 예) 관리 강도 3, 오늘 0분 → 시듦
    assertThat(treeWithCare(3, 0).isWilted()).isTrue();
  }

  @Test
  void feExample5StudyingTodayRevivesTheTree() {
    // FE 예) 관리 강도 3, 오늘 25분 → 안 시듦 (다시 공부해서 살아남)
    assertThat(treeWithCare(3, 25).isWilted()).isFalse();
  }

  @Test
  void oneMinuteOfStudyIsEnoughToRevive() {
    // 이슈 #64 완료 조건: 시든 상태에서 공부하면 회복됩니다. 1분이면 됩니다.
    assertThat(treeWithCare(4, 0).isWilted()).isTrue();
    assertThat(treeWithCare(4, 1).isWilted()).isFalse();
  }

  @Test
  void wiltsOnlyFromLevelThree() {
    // 관리 강도 0·1·2 에서는 오늘 공부를 안 했어도 시들지 않습니다. 3·4 에서만 시듭니다.
    assertThat(treeWithCare(0, 0).isWilted()).isFalse();
    assertThat(treeWithCare(1, 0).isWilted()).isFalse();
    assertThat(treeWithCare(2, 0).isWilted()).isFalse();
    assertThat(treeWithCare(3, 0).isWilted()).isTrue();
    assertThat(treeWithCare(4, 0).isWilted()).isTrue();
  }

  @Test
  void wiltedTreeKeepsEverythingItGrew() {
    // 시들어도 단계·잎·열매·진행은 하나도 줄지 않습니다. 시든 나무와 안 시든 나무는 isWilted 만 다릅니다.
    TreeState wilted = TreeState.of(1500, 0, 3, 4, 2, 3);
    TreeState healthy = TreeState.of(1500, 30, 3, 4, 2, 3);

    assertThat(wilted.isWilted()).isTrue();
    assertThat(healthy.isWilted()).isFalse();
    assertThat(wilted.stage()).isEqualTo(healthy.stage());
    assertThat(wilted.stageName()).isEqualTo(healthy.stageName());
    assertThat(wilted.leafCount()).isEqualTo(healthy.leafCount());
    assertThat(wilted.fruitCount()).isEqualTo(healthy.fruitCount());
    assertThat(wilted.minutesToNextStage()).isEqualTo(healthy.minutesToNextStage());
    assertThat(wilted.progressPercent()).isEqualTo(healthy.progressPercent());
  }

  @Test
  void treeNeverDies() {
    // 이슈 #64 완료 조건: 어떤 경우에도 '죽음' 상태가 없습니다.
    // 누적 시간·오늘 공부·관리 강도를 여러 가지로 바꿔 봐도, 단계 이름은 항상 정해 둔 4개 중 하나입니다.
    for (int totalMinutes = 0; totalMinutes <= 3000; totalMinutes += 100) {
      for (int careLevel = 0; careLevel <= 4; careLevel++) {
        for (int todayMinutes : new int[] {0, 1, 60}) {
          TreeState tree = TreeState.of(totalMinutes, todayMinutes, careLevel, 0, 0, 0);

          assertThat(TreeState.STAGE_NAMES).contains(tree.stageName());
          assertThat(tree.stage()).isBetween(0, 3);
          assertThat(tree.progressPercent()).isBetween(0.0, 100.0);
        }
      }
    }
  }

  // ─── 잎과 열매 ───

  @Test
  void leavesAreReflectionsPlusInsights() {
    // 잎 = 쓴 회고 수 + 받은 인사이트 수 / 열매 = 나 사용설명서 항목 수
    TreeState tree = TreeState.of(0, 0, 1, 4, 2, 3);

    assertThat(tree.leafCount()).isEqualTo(6);
    assertThat(tree.fruitCount()).isEqualTo(3);
  }

  @Test
  void leavesAndFruitsDoNotDependOnStage() {
    // 씨앗이어도 잎과 열매는 셉니다. 단계와는 따로입니다.
    assertThat(TreeState.of(0, 0, 1, 1, 1, 1).leafCount()).isEqualTo(2);
    assertThat(TreeState.of(2000, 0, 1, 1, 1, 1).leafCount()).isEqualTo(2);
  }

  // ─── 이상한 값 ───

  @Test
  void negativeNumbersAreTreatedAsZero() {
    // 깨진 기록 때문에 나무가 이상해지지 않게, 음수는 0 으로 봅니다.
    TreeState tree = TreeState.of(-500, -10, 1, -3, 2, -1);

    assertThat(tree.stageName()).isEqualTo("씨앗");
    assertThat(tree.minutesToNextStage()).isEqualTo(600);
    assertThat(tree.progressPercent()).isEqualTo(0.0);
    // 회고 -3 은 0 으로 보고 인사이트 2 만 셉니다.
    assertThat(tree.leafCount()).isEqualTo(2);
    assertThat(tree.fruitCount()).isEqualTo(0);
  }

  @Test
  void negativeTodayMinutesCountsAsNoStudy() {
    // 오늘 공부 시간이 음수면 0분(안 함)으로 봅니다. 관리 강도 3 이면 시듭니다.
    assertThat(treeWithCare(3, -5).isWilted()).isTrue();
  }
}
