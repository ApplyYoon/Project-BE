package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.ArrayList;
import java.util.List;

// 체크리스트 추천(이슈 #236)에서 GPT 가 돌려준 항목들을 검사합니다. GPT 도 틀릴 수 있어서 그대로 쓰지 않습니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// 검사 규칙
//   1. 앞뒤 공백을 지우고, 비었거나 MAX_ITEM_LENGTH(50자)를 넘는 항목은 버립니다.
//   2. 깎아내리는 말(ToneGuide 의 금지 표현)이 든 항목은 버립니다.
//   3. 같은 항목이 또 나오면 처음 것만 남깁니다.
//   4. 개수가 많으면 앞에서부터 count.max 개만 남깁니다.
//   5. 남은 항목이 count.min 개보다 적으면 쓰지 않습니다(null). FE 는 기본값(직접 쓰기)으로 대신합니다.
//
// 예시 (개수 3~6개)
//   ["42~45쪽 풀기", " 오답 표시 ", "", "42~45쪽 풀기", "채점하기"]  →  ["42~45쪽 풀기", "오답 표시", "채점하기"]
//   ["42~45쪽 풀기", "채점하기"]                                     →  null (2개라 모자람)
public final class ChecklistDraft {

  // 항목 하나의 최대 글자 수. 체크리스트는 한눈에 읽혀야 해서 짧게 잡았습니다.
  public static final int MAX_ITEM_LENGTH = 50;

  private ChecklistDraft() {}

  // 쓸 수 있는 항목만 남깁니다. 개수가 모자라면 null 입니다.
  public static List<String> from(ChecklistPrompt.Answer answer, ChecklistPrompt.Count count) {
    if (answer == null || answer.items() == null) {
      return null;
    }
    List<String> items = new ArrayList<>();
    for (String raw : answer.items()) {
      // 규칙 4
      if (items.size() >= count.max()) {
        break;
      }
      // 규칙 1
      String item = raw == null ? "" : raw.trim();
      if (item.isEmpty() || item.length() > MAX_ITEM_LENGTH) {
        continue;
      }
      // 규칙 2 · 3
      if (ToneGuide.hasBannedExpression(item) || items.contains(item)) {
        continue;
      }
      items.add(item);
    }
    // 규칙 5
    return items.size() < count.min() ? null : items;
  }
}
