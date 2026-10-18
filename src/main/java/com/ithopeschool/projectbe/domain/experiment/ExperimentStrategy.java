package com.ithopeschool.projectbe.domain.experiment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 자기 실험에서 고르는 전략 10개입니다. (최종 기획서 「4.6 분석」 자기 실험, FE src/lib/strategyCatalog.ts)
// FE 와 같은 id("phone-flip")로 주고받고, DB 에는 이름 그대로(PHONE_FLIP) 저장합니다.
// 글(rule)은 FE 와 똑같이 맞춥니다. 한쪽을 고치면 다른 쪽도 고쳐 주세요.
//
// group: 어떤 원인을 겨냥한 방법인지 (phone 폰 · start 시작이 어려움 · plan 계획이 큼)
// level: easy 쉬움 · normal 보통 · hard 강함
// rule: 카드에 붙는 '실험 규칙' 한 줄
//
// (옛 기획 v2 의 개선안 목록은 suggestion/StrategyCatalog 에 따로 있습니다. 이것은 v3 자기 실험용입니다)
@Getter
@RequiredArgsConstructor
public enum ExperimentStrategy {
  PHONE_FLIP("phone-flip", "phone", "easy", "시작할 때 폰을 뒤집어 두기"),
  PHONE_BAG("phone-bag", "phone", "normal", "시작할 때 폰을 가방에 넣기"),
  PHONE_ROOM("phone-room", "phone", "hard", "시작할 때 폰을 다른 방에 두기"),
  START_FIRST_LINE("start-first-line", "start", "easy", "시작 시각에 체크리스트 첫 줄만 하기"),
  START_COUNTDOWN("start-countdown", "start", "normal", "5초 카운트다운으로 시작하기"),
  START_DESK("start-desk", "start", "hard", "시작 10분 전에 책상과 책을 미리 펴 두기"),
  PLAN_ONE_LESS("plan-one-less", "plan", "easy", "체크리스트를 하나 줄이기"),
  PLAN_SHORTER("plan-shorter", "plan", "normal", "카드 길이를 10분 줄이기"),
  PLAN_SPLIT("plan-split", "plan", "hard", "큰 카드를 두 장으로 나눠 다른 날에 하기"),
  PROOF_ONE_LINE("proof-one-line", "start", "easy", "끝나면 한 일을 한 줄로 남기기");

  // FE 와 주고받는 id
  private final String id;
  private final String group;
  private final String level;
  private final String rule;

  // FE 가 보낸 id 를 전략으로 바꿉니다. 10개 중 하나가 아니면 null 입니다.
  public static ExperimentStrategy fromId(String id) {
    for (ExperimentStrategy strategy : values()) {
      if (strategy.id.equals(id)) {
        return strategy;
      }
    }
    return null;
  }
}
