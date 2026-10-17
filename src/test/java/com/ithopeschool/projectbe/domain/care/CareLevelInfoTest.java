package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 관리 강도 단계의 이름·설명·기본 이유 문장(이슈 #62)을 확인합니다.
// 문구는 FE 의 careLevelInfos (src/lib/careLevelInfo.ts) 와 같습니다.
class CareLevelInfoTest {

  @Test
  void hasAllFiveLevelsInOrder() {
    // 0 ~ 4 다섯 단계가 순서대로 있습니다. 목록의 자리 번호가 곧 단계 번호입니다.
    assertThat(CareLevelInfo.ALL).hasSize(5);
    for (int level = CareLevel.MIN_LEVEL; level <= CareLevel.MAX_LEVEL; level++) {
      assertThat(CareLevelInfo.ALL.get(level).level()).isEqualTo(level);
    }
  }

  @Test
  void levelNamesAreTheSameAsFe() {
    // 단계 이름: 0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍
    assertThat(CareLevelInfo.ALL)
        .extracting(CareLevelInfo::name)
        .containsExactly("회복", "당근", "넛지", "심리적 채찍", "강제적 채찍");
  }

  @Test
  void findsTheInfoByLevel() {
    // 단계 번호로 찾으면 그 단계의 정보가 나옵니다.
    CareLevelInfo nudge = CareLevelInfo.of(2);

    assertThat(nudge.level()).isEqualTo(2);
    assertThat(nudge.name()).isEqualTo("넛지");
    assertThat(nudge.description()).isEqualTo("담백한 알림. \"계획 시간 10분 지났어\"");
    assertThat(nudge.defaultReason()).isEqualTo("계획 시간마다 가볍게 챙기는 중이야.");
  }

  @Test
  void defaultReasonsAreTheSameAsFe() {
    // 이유 문장이 따로 없는 날에 보여줄 기본 한 줄입니다. 모두 "지금 어떻게 대하는지" 만 알려 줍니다.
    assertThat(CareLevelInfo.of(0).defaultReason())
        .isEqualTo("지금은 쉬어 가는 중이야. 오늘은 부담 없이 할 수 있는 만큼만 해 보자.");
    assertThat(CareLevelInfo.of(1).defaultReason())
        .isEqualTo("요즘 잘 해내고 있어서, 잘한 점을 구체적으로 알려주는 중이야.");
    assertThat(CareLevelInfo.of(3).defaultReason()).isEqualTo("계획이 자꾸 미뤄져서, 조금 더 따끔하게 말하는 중이야.");
    assertThat(CareLevelInfo.of(4).defaultReason()).isEqualTo("공부 시간에는 집중 모드로 화면을 잠그는 중이야.");
  }

  @Test
  void everyLevelHasSomethingToSay() {
    // 어느 단계든 이름·설명·기본 이유가 비어 있지 않습니다. (규칙: "왜 이 모드인지 항상 말함")
    assertThat(CareLevelInfo.ALL)
        .allSatisfy(
            info -> {
              assertThat(info.name()).isNotBlank();
              assertThat(info.description()).isNotBlank();
              assertThat(info.defaultReason()).isNotBlank();
            });
  }

  @Test
  void unknownLevelFallsBackToRecovery() {
    // 0 ~ 4 밖의 값이 들어오면 에러 대신 가장 약한 0단계(회복)의 말을 돌려줍니다.
    // 잘못된 값 때문에 센 말이 나가는 것보다 가장 부드러운 말이 나가는 쪽이 안전합니다.
    assertThat(CareLevelInfo.of(5)).isEqualTo(CareLevelInfo.of(0));
    assertThat(CareLevelInfo.of(-1)).isEqualTo(CareLevelInfo.of(0));
    assertThat(CareLevelInfo.of(99).name()).isEqualTo("회복");
  }

  @Test
  void worksWithTheLevelDecidedByCareLevel() {
    // 실제로 쓰는 흐름: 안전 브레이크 때문에 0단계가 된 날에는 CareDecision 의 이유 문장이 맞지 않습니다.
    // 그럴 때 0단계의 기본 문장을 보여 줍니다.
    int todayLevel = CareLevel.of(3, CareAction.WHIP, 4, true);

    assertThat(todayLevel).isEqualTo(0);
    assertThat(CareLevelInfo.of(todayLevel).defaultReason())
        .isEqualTo("지금은 쉬어 가는 중이야. 오늘은 부담 없이 할 수 있는 만큼만 해 보자.");
  }
}
