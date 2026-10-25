package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 음성 통화의 "오늘 주제" 고르기 규칙(CallTopic.pick)을 봅니다. 스프링을 띄우지 않는 단위 테스트입니다. (#434)
class CallTopicTest {

  private static final String PROMISE = "만약 학원에서 오면 → 단어 20개";
  private static final BiasQuestion QUESTION = new BiasQuestion(CognitiveBias.FUTURE_SELF, 0);

  // 어제 순공 · 계획(분), 이번 주 평균 시작 지연(분), 어제 나간 횟수만 바꿔 쓰는 카드
  private static Card card(int studied, int planned, Integer startDelay, Integer awayCount) {
    return new Card(
        studied, planned, null, 3, 180, "19:00", 64, 55, startDelay, null, 4, awayCount, 21, null);
  }

  // 아무 신호도 없는 보통 날: 어제 79%, 시작 지연 10분, 나간 적 없음
  private static final Card ORDINARY = card(190, 240, 10, 0);

  @Test
  @DisplayName("1) 위로하는 통화면 무엇이 있어도 위로가 먼저다")
  void comfortComesFirst() {
    assertThat(CallTopic.pick(true, true, card(100, 240, 30, 5), PROMISE, QUESTION))
        .isEqualTo(CallTopic.COMFORT);
  }

  @Test
  @DisplayName("2) 오늘 끝낸 공부가 있으면(확인 통화) 약속 · 습관 · 생각 습관보다 먼저다")
  void checkStudyBeforeEverythingElse() {
    assertThat(CallTopic.pick(false, true, card(100, 240, 30, 5), PROMISE, QUESTION))
        .isEqualTo(CallTopic.CHECK_STUDY);
  }

  @Test
  @DisplayName("3) 지난 약속이 있으면 시작 지연 · 나간 것 · 생각 습관보다 먼저다")
  void promiseBeforeHabits() {
    assertThat(CallTopic.pick(false, false, card(100, 240, 30, 5), PROMISE, QUESTION))
        .isEqualTo(CallTopic.PROMISE);
  }

  @Test
  @DisplayName("4) 이번 주 평균 시작 지연이 15분을 넘으면 시작 시각 (15분까지는 아님)")
  void lateStart() {
    assertThat(CallTopic.pick(false, false, card(100, 240, 16, 5), null, QUESTION))
        .isEqualTo(CallTopic.LATE_START);
    assertThat(CallTopic.pick(false, false, card(100, 240, 15, 0), null, null))
        .isEqualTo(CallTopic.TODAY);
  }

  @Test
  @DisplayName("5) 어제 못 채웠고 3번 이상 나갔으면 나간 것. 다 채운 날 · 2번까지는 아님")
  void awayOnMissedDay() {
    assertThat(CallTopic.pick(false, false, card(100, 240, 10, 3), null, QUESTION))
        .isEqualTo(CallTopic.AWAY);
    // 계획을 다 채운 날에는 여러 번 나갔어도 고르지 않습니다. (잘한 날에 탓하지 않게)
    assertThat(CallTopic.pick(false, false, card(240, 240, 10, 5), null, QUESTION))
        .isEqualTo(CallTopic.BIAS);
    assertThat(CallTopic.pick(false, false, card(100, 240, 10, 2), null, null))
        .isEqualTo(CallTopic.TODAY);
    // 어제 계획이 없었으면(달성률을 모름) 못 채운 날로 보지 않습니다.
    assertThat(CallTopic.pick(false, false, card(60, 0, 10, 5), null, null))
        .isEqualTo(CallTopic.TODAY);
  }

  @Test
  @DisplayName("6) 생각 습관 질문이 있으면 생각 습관, 7) 아무것도 없으면 오늘 계획")
  void biasThenToday() {
    assertThat(CallTopic.pick(false, false, ORDINARY, null, QUESTION)).isEqualTo(CallTopic.BIAS);
    assertThat(CallTopic.pick(false, false, ORDINARY, null, null)).isEqualTo(CallTopic.TODAY);
    // 기록이 없는 첫 통화
    assertThat(CallTopic.pick(false, false, null, null, null)).isEqualTo(CallTopic.TODAY);
  }
}
