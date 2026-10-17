package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "내 한 칸 길이" 규칙을 고르는 순서대로 확인합니다.
class BlockLengthTest {

  @Test
  @DisplayName("공부 기록이 5번 이상이면 한 번 길이의 가운데값을 5분 단위로 쓴다")
  void fromRecords() {
    BlockLength.Choice choice = BlockLength.of(List.of(20, 25, 22, 40, 24), 60, true);

    assertThat(choice.minutes()).isEqualTo(25);
    assertThat(choice.evidence()).isEqualTo("최근 14일 공부 기록 5번의 한 번 길이 가운데값이 24분이라 25분으로 잡았어요");
  }

  @Test
  @DisplayName("기록이 적으면 처음 설문의 집중 시간 → 심리검사 딴생각 → 50분 순서로 고른다")
  void fallbacks() {
    assertThat(BlockLength.of(List.of(20, 25), 40, true).minutes()).isEqualTo(40);
    assertThat(BlockLength.of(List.of(), null, true).minutes()).isEqualTo(30);
    assertThat(BlockLength.of(List.of(), null, false))
        .isEqualTo(new BlockLength.Choice(50, "아직 공부 기록이 적어서 50분으로 잡았어요"));
  }

  @Test
  @DisplayName("길이는 15 ~ 90분 안으로 맞추고, 가운데값은 짝수 개면 가운데 둘의 평균이다")
  void clampAndMedian() {
    assertThat(BlockLength.clamp(12)).isEqualTo(15);
    assertThat(BlockLength.clamp(27)).isEqualTo(25);
    assertThat(BlockLength.clamp(28)).isEqualTo(30);
    assertThat(BlockLength.clamp(120)).isEqualTo(90);
    assertThat(BlockLength.medianOf(List.of(10, 20, 31, 41))).isEqualTo(26);
  }
}
