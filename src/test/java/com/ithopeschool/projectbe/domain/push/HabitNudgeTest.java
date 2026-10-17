package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.message.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "오늘의 작전" 알림 문장을 확인합니다.
class HabitNudgeTest {

  @Test
  @DisplayName("습관이 있으면 그 습관의 해 볼 행동을 '○○ 대비' 와 함께 보낸다")
  void habitNudge() {
    HabitNudge nudge = HabitNudge.of(CognitiveBias.PLANNING_FALLACY);

    assertThat(nudge.title()).isEqualTo("오늘의 작전");
    assertThat(nudge.body()).isEqualTo("다음 계획은 처음 예상한 시간의 1.5배로 잡아 보기 (계획 오류 대비)");
    assertThat(nudge.url()).isEqualTo("/timer");
  }

  @Test
  @DisplayName("습관을 모르면 기본 문장을 보낸다")
  void genericNudge() {
    assertThat(HabitNudge.of(null).body()).isEqualTo("오늘 계획 하나, 5분만 먼저 시작해 볼까?");
  }

  @Test
  @DisplayName("모든 문장에 깎아내리는 말이 없고, 메시지 길이 한도 안이다")
  void everyNudgeIsSafe() {
    for (CognitiveBias bias : CognitiveBias.values()) {
      String body = HabitNudge.of(bias).body();
      assertThat(ToneGuide.hasBannedExpression(body)).as(body).isFalse();
      assertThat(body.length()).isLessThanOrEqualTo(MessageService.MAX_TEXT_LENGTH);
    }
  }
}
