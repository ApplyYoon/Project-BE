package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.suggestion.ExperimentEffect;
import com.ithopeschool.projectbe.domain.suggestion.SuggestionGrade;
import java.util.List;

// 어제 결과를 보고 "오늘 AI 가 어떻게 대응할지" 를 정합니다. (이슈 #55, 기획서 v2 4장)
// "못 하면 채찍" 이 아니라, 못 한 이유를 나눠서 대응을 정합니다.
// 잘 지켰는데 효과가 없으면 문제는 사람이 아니라 전략입니다.
//
// 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//
//   0) 3일 연속 미달 또는 기분 저하     →  회복 모드 우선, 채찍 금지   (아래 표보다 가장 먼저)
//   1) 잘 시행 + 효과 있음              →  당근, 목표 10% 상향
//      잘 시행 + 효과 없음              →  당근(노력 인정), 전략 교체
//      잘 시행 + 효과 애매              →  당근만. 1주 더 지켜봄
//   2) 부분 시행                        →  같은 전략 유지, 난이도 한 단계 낮춤
//   3) 못 시행 + 계획 과다·피곤·아픔·행사 →  목표 하향
//      못 시행 + 폰·하기 싫음           →  채찍 (한도 안에서 한 단계씩)
//      못 시행 + 기타이거나 안 고름      →  목표 하향
//
//   3일 연속 미달 + 폰 때문에 못 함  →  회복 우선  (원래라면 채찍이지만 막습니다)
//   기분 저하 + 오늘은 잘 지킴       →  회복 우선  (잘 한 날도 회복이 먼저)
//   2일 연속 미달                   →  아직 3일이 아니라서 표로 갑니다
//
// 이유를 모를 때(기타·안 고름)는 채찍 대신 목표 하향을 고릅니다.
// 이유 없이 다그치는 쪽이 더 위험하다고 보고 정한 규칙입니다. (이슈에 정해진 값이 없어서 팀에서 바꿀 수 있습니다)
// "잘 시행 + 효과 애매" 줄도 이슈의 표에는 없고 FE 에서 더한 줄입니다.
//
// FE 의 decideCareAction (src/lib/careDecision.ts) 과 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판단이 어긋나지 않습니다.
//
// action: 대응 종류 / reason: "왜 이렇게 됐는지" 를 학생에게 보여줄 한 줄
public record CareDecision(CareAction action, String reason) {

  // 이 일수 이상 연속으로 계획을 못 채우면 회복 모드를 우선합니다. (기획서: 3일 연속 미달)
  public static final int RECOVERY_MISS_DAYS = 3;

  // 못 채운 이유 중 "계획이나 몸 상태 때문" 인 것들입니다. 사람을 다그치지 않고 목표를 낮춥니다.
  private static final List<MissReason> GOAL_DOWN_REASONS =
      List.of(MissReason.TOO_MUCH, MissReason.TIRED, MissReason.SICK, MissReason.EVENT);

  // 못 채운 이유 중 "행동 습관 때문" 인 것들입니다. 채찍(한도 안에서 한 단계씩)으로 갑니다.
  private static final List<MissReason> WHIP_REASONS =
      List.of(MissReason.PHONE, MissReason.UNWILLING);

  // 오늘의 대응을 정합니다.
  //
  // fulfillment: 개선안 이행 판정 (이슈 #53). 기록이 없어서 판정이 없을 때는 이 함수를 부르지 말고, 부르는 쪽에서 먼저 처리합니다.
  // effect: 개선안 효과 판정 (이슈 #54)
  // mainMissReason: 계획을 못 채운 주된 이유. 고르지 않았으면 null
  // consecutiveMissDays: 최근 연속으로 계획을 못 채운 일수 (MissStreak)
  // isMoodLow: 최근 기분이 낮은 상태인지 (LowMoodStreak)
  public static CareDecision decide(
      SuggestionGrade fulfillment,
      ExperimentEffect effect,
      MissReason mainMissReason,
      int consecutiveMissDays,
      boolean isMoodLow) {
    if (fulfillment == null || effect == null) {
      throw new IllegalArgumentException(
          "이행 판정과 효과 판정이 있어야 대응을 정할 수 있어요. 기록이 없을 때는 부르는 쪽에서 먼저 처리해 주세요");
    }

    // 0) 회복 모드 우선: 다른 어떤 조건보다 먼저 봅니다.
    if (isMoodLow) {
      return new CareDecision(
          CareAction.RECOVERY_FIRST, "요즘 기분이 많이 가라앉아 보여서, 오늘은 다그치지 않고 회복부터 도울게요.");
    }
    if (consecutiveMissDays >= RECOVERY_MISS_DAYS) {
      return new CareDecision(
          CareAction.RECOVERY_FIRST,
          consecutiveMissDays + "일 연속 계획을 못 채워서, 오늘은 다그치지 않고 회복부터 도울게요.");
    }

    // 1) 잘 시행: 효과에 따라 셋으로 나뉩니다.
    if (fulfillment == SuggestionGrade.GOOD) {
      if (effect == ExperimentEffect.EFFECTIVE) {
        return new CareDecision(CareAction.CARROT_RAISE, "잘 지켰고 효과도 있었어요. 목표를 10% 올려 볼게요.");
      }
      if (effect == ExperimentEffect.NO_EFFECT) {
        return new CareDecision(
            CareAction.CARROT_CHANGE_STRATEGY, "잘 지켰는데 효과가 없었어요. 노력한 건 맞으니, 방법을 바꿔 볼게요.");
      }
      return new CareDecision(CareAction.CARROT_KEEP, "잘 지켰어요. 효과는 아직 애매해서 1주 더 지켜볼게요.");
    }

    // 2) 부분 시행
    if (fulfillment == SuggestionGrade.PARTIAL) {
      return new CareDecision(CareAction.KEEP_EASIER, "일부는 지켰어요. 같은 방법으로 두고, 난이도만 한 단계 낮출게요.");
    }

    // 3) 못 시행: 이유로 나눕니다.
    if (mainMissReason != null && WHIP_REASONS.contains(mainMissReason)) {
      return new CareDecision(CareAction.WHIP, "폰이나 미루는 습관 때문에 못 지켰어요. 오늘은 한 단계 더 따끔하게 말할게요.");
    }
    if (mainMissReason != null && GOAL_DOWN_REASONS.contains(mainMissReason)) {
      return new CareDecision(CareAction.GOAL_DOWN, "계획이 많았거나 몸이 힘들었던 것 같아요. 오늘은 목표를 낮출게요.");
    }
    return new CareDecision(CareAction.GOAL_DOWN, "못 지킨 이유를 아직 몰라서, 오늘은 다그치지 않고 목표를 낮출게요.");
  }
}
