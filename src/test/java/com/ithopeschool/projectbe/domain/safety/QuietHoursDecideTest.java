package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.message.EventMessageType;
import com.ithopeschool.projectbe.domain.message.MessageKind;
import com.ithopeschool.projectbe.domain.safety.QuietHours.Action;
import com.ithopeschool.projectbe.domain.safety.QuietHours.Decision;
import com.ithopeschool.projectbe.domain.safety.QuietHours.Window;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 연락 금지 시간에 걸린 메시지를 "지금 보냄 / 미룸 / 버림" 중 무엇으로 할지 확인합니다. (이슈 #57)
class QuietHoursDecideTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 한국 시간 10월 day 일 hour:minute 을 순간(Instant)으로 바꿉니다.
  private static Instant seoul(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 10, day, hour, minute).atZone(SEOUL).toInstant();
  }

  // ---- 판단 ----

  @Test
  @DisplayName("금지 시간이 아니면 미룰 메시지든 버릴 메시지든 지금 보낸다")
  void sendWhenNotQuiet() {
    Instant evening = seoul(17, 19, 0);

    assertThat(QuietHours.decide(evening, Window.DEFAULT, QuietPolicy.DELAY))
        .isEqualTo(new Decision(Action.SEND, evening));
    assertThat(QuietHours.decide(evening, Window.DEFAULT, QuietPolicy.DROP))
        .isEqualTo(new Decision(Action.SEND, evening));
  }

  @Test
  @DisplayName("금지 시간에 걸린 버릴 메시지는 버린다 (보낼 시각 없음)")
  void dropWhenQuiet() {
    assertThat(QuietHours.decide(seoul(17, 23, 30), Window.DEFAULT, QuietPolicy.DROP))
        .isEqualTo(new Decision(Action.DROP, null));
  }

  @Test
  @DisplayName("밤 11시 30분에 걸린 미룰 메시지는 자정을 넘어 다음 날 아침 7시로 미룬다")
  void delayAcrossMidnight() {
    assertThat(QuietHours.decide(seoul(17, 23, 30), Window.DEFAULT, QuietPolicy.DELAY))
        .isEqualTo(new Decision(Action.DELAY, seoul(18, 7, 0)));
  }

  @Test
  @DisplayName("새벽 1시에 걸린 미룰 메시지는 같은 날 아침 7시로 미룬다")
  void delayAfterMidnight() {
    assertThat(QuietHours.decide(seoul(18, 1, 0), Window.DEFAULT, QuietPolicy.DELAY))
        .isEqualTo(new Decision(Action.DELAY, seoul(18, 7, 0)));
  }

  @Test
  @DisplayName("사용자가 정한 금지 시간(22:30 ~ 07:30)을 따르고, 분까지 본다")
  void usesTheUsersWindow() {
    Window window = new Window(LocalTime.of(22, 30), LocalTime.of(7, 30));

    // 22:10 은 아직 금지 시간이 아님
    assertThat(QuietHours.decide(seoul(17, 22, 10), window, QuietPolicy.DELAY).action())
        .isEqualTo(Action.SEND);
    // 22:40 은 금지 → 다음 날 07:30 으로
    assertThat(QuietHours.decide(seoul(17, 22, 40), window, QuietPolicy.DELAY))
        .isEqualTo(new Decision(Action.DELAY, seoul(18, 7, 30)));
    // 07:30 이 되면 다시 보냄
    assertThat(QuietHours.decide(seoul(18, 7, 30), window, QuietPolicy.DROP).action())
        .isEqualTo(Action.SEND);
  }

  @Test
  @DisplayName("자정을 넘지 않는 금지 시간(13:00 ~ 14:00, 예: 점심)도 끝나는 시각으로 미룬다")
  void delayWithinTheSameDay() {
    Window lunch = new Window(LocalTime.of(13, 0), LocalTime.of(14, 0));

    assertThat(QuietHours.decide(seoul(17, 13, 20), lunch, QuietPolicy.DELAY))
        .isEqualTo(new Decision(Action.DELAY, seoul(17, 14, 0)));
  }

  @Test
  @DisplayName("시작과 끝이 같으면 금지 시간이 없어서 언제나 지금 보낸다")
  void noQuietWindow() {
    Window none = new Window(LocalTime.of(23, 0), LocalTime.of(23, 0));

    assertThat(QuietHours.decide(seoul(17, 23, 0), none, QuietPolicy.DROP).action())
        .isEqualTo(Action.SEND);
  }

  @Test
  @DisplayName("기본 금지 시간은 23:00 ~ 07:00 이다")
  void defaultWindow() {
    assertThat(Window.DEFAULT).isEqualTo(new Window(LocalTime.of(23, 0), LocalTime.of(7, 0)));
  }

  // ---- 미룸 / 버림 분류 (학생이 정함: 아침에 받아도 뜻이 있으면 미룸) ----

  @Test
  @DisplayName("이벤트 메시지: '계획 시간 지남' 은 버리고, 목표 달성 · 3일 연속 미달은 미룬다")
  void eventMessagePolicies() {
    assertThat(EventMessageType.PLAN_LATE.getQuietPolicy()).isEqualTo(QuietPolicy.DROP);
    assertThat(EventMessageType.GOAL_DONE.getQuietPolicy()).isEqualTo(QuietPolicy.DELAY);
    assertThat(EventMessageType.MISSED_3_DAYS.getQuietPolicy()).isEqualTo(QuietPolicy.DELAY);
  }

  @Test
  @DisplayName("메시지 종류: 당근 · 넛지 · 회복은 미루고, 채찍(독촉)은 버린다. 이벤트는 덜 연락하는 쪽(버림)")
  void messageKindPolicies() {
    assertThat(MessageKind.CARROT.quietPolicy()).isEqualTo(QuietPolicy.DELAY);
    assertThat(MessageKind.NUDGE.quietPolicy()).isEqualTo(QuietPolicy.DELAY);
    assertThat(MessageKind.RECOVERY.quietPolicy()).isEqualTo(QuietPolicy.DELAY);
    assertThat(MessageKind.STICK.quietPolicy()).isEqualTo(QuietPolicy.DROP);
    assertThat(MessageKind.EVENT.quietPolicy()).isEqualTo(QuietPolicy.DROP);
  }
}
