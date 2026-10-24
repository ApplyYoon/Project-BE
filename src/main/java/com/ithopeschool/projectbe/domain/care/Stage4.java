package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardPriority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// 관리 4단계(강제적 채찍)를 앱 안에서 하는 규칙입니다. (최종 기획서 「5.4 관리 단계」 4단계, 이슈 #234)
// OS 앱 잠금이 아직 없어서, 앱 안에서 할 수 있는 장치로 1차 구현합니다. (기획서 1장 9번)
//   ① 미루기 잠금                    → CardDayService 가 PostponeRule 에 stage4Locked 로 넘김
//   ② 다음 반복 카드 1장을 절대 카드로  → pickForcedCard. 카드 자체는 안 바꾸고 "그 카드 · 그 날짜만" 절대로 봅니다
//   ③ 산출물 증빙 강제                → 4단계에 저장한 세션은 proofRequired. 증빙이 없으면 실행으로 인정하지 않음 (CardResultJudge)
//   해제: 증빙이 있는 실행 1회 → 3단계 (Stage4Service.releaseIfProven)
// 다짐 녹음 자동 재생은 녹음이 기기에만 있어서 FE 만의 일입니다.
//
// 값만 계산하는 순수 함수라서 DB 를 모릅니다. FE 의 src/lib/stage4.ts 와 같은 규칙입니다.
public final class Stage4 {

  // 강제적 채찍 단계
  public static final int STAGE = 4;
  // 풀리면 내려가는 단계
  public static final int RELEASED_STAGE = 3;
  // 다음 반복 카드를 찾아보는 날 수 (오늘 포함, FE 의 LOOKAHEAD_DAYS)
  public static final int LOOKAHEAD_DAYS = 7;

  // 미루기 잠금 문장 (FE 가 그대로 보여 줌)
  public static final String POSTPONE_LOCK_REASON = "관리 4단계라 지금은 미룰 수 없어요. 증빙을 남기고 한 번 해내면 풀려요";
  // 풀렸을 때의 이유 한 줄 (FE 의 releaseStage4 와 같은 문장)
  public static final String RELEASE_REASON = "증빙이 있는 실행을 한 번 해서 4단계를 풀고 3단계로 내려왔어.";

  // 절대 카드로 바꾼 카드 한 번 (반복 카드의 그날 것)
  public record Target(Long cardId, LocalDate date) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private Stage4() {}

  // 지금 4단계 장치를 켤지. 판정이 4단계이고, 아직 안 풀렸고, 제재 한도가 4 이상이고, 보호 조치가 꺼져 있을 때만입니다.
  // (한도는 낮추면 그날 바로 적용이라, 판정 뒤에 낮췄어도 여기서 다시 봅니다)
  //
  //   [4단계, 안 풀림, 한도 4, 보호 조치 꺼짐] → true
  //   [4단계, 풀림,    한도 4, 보호 조치 꺼짐] → false   (3단계로 내려옴)
  //   [4단계, 안 풀림, 한도 3, 보호 조치 꺼짐] → false   (한도를 낮춤)
  //   [4단계, 안 풀림, 한도 4, 보호 조치 켜짐] → false   (안전이 먼저)
  //   [3단계, 안 풀림, 한도 4, 보호 조치 꺼짐] → false
  public static boolean isOn(int stage, boolean released, int maxLevel, boolean safetyActive) {
    return stage == STAGE && !released && maxLevel >= STAGE && !safetyActive;
  }

  // 절대 카드로 바꿀 "다음 반복 카드 1장" 을 고릅니다. 지금 이후에 시작하는 가장 가까운 반복 카드의 그날 것입니다.
  // 1회성 카드 · 이미 절대인 카드 · 시작 시각이 없는 카드 · 오늘 이미 한 카드(doneTodayIds)는 고르지 않습니다.
  // 7일 안에 없으면 null 입니다. 같은 날 같은 시각이면 번호가 작은 카드입니다.
  //
  //   (지금 10/17 18:00) 반복 A 19:00(매일), 반복 B 20:00(매일) → A, 10/17
  //   (지금 10/17 21:00) 반복 A 19:00(매일)                     → A, 10/18
  //   (지금 10/17 18:00) 반복 A 19:00(매일), 오늘 A 를 이미 함    → A, 10/18
  public static Target pickForcedCard(
      List<Card> cards, LocalDate today, Instant now, Set<Long> doneTodayIds) {
    for (int offset = 0; offset < LOOKAHEAD_DAYS; offset++) {
      LocalDate date = today.plusDays(offset);
      Card picked = null;
      Instant pickedStartAt = null;
      for (Card card : cards) {
        if (card.isOneOff()
            || card.getPriority() == CardPriority.ABSOLUTE
            || card.getStartTime() == null
            || !card.isOnDate(date)) {
          continue;
        }
        if (offset == 0 && doneTodayIds.contains(card.getId())) {
          continue;
        }
        Instant startAt = Card.startAtOf(date, card.getStartTime());
        if (!startAt.isAfter(now)) {
          continue;
        }
        if (pickedStartAt == null || startAt.isBefore(pickedStartAt)) {
          picked = card;
          pickedStartAt = startAt;
        }
      }
      if (picked != null) {
        return new Target(picked.getId(), date);
      }
    }
    return null;
  }
}
