package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// 루틴 카드 제안(이슈 #239)에서 GPT 가 돌려준 카드들을 검사합니다. GPT 도 틀릴 수 있어서 그대로 쓰지 않습니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// 검사 규칙 (카드 한 장마다)
//   1. 제목: 앞뒤 공백을 지우고, 비었거나 MAX_TITLE_LENGTH(40자)를 넘거나 깎아내리는 말이 있으면 그 카드를 버립니다.
//   2. 시간대: Slot 이름이 아니거나 앞 카드와 같은 시간대면 그 카드를 버립니다. (같은 시각에 카드 두 장이 겹치지 않게)
//   3. 체크리스트: 앞뒤 공백을 지우고, 비었거나 MAX_ITEM_LENGTH(50자)를 넘거나 깎아내리는 말이 든 항목은 버리고,
//      같은 항목은 하나만, 많으면 앞에서부터 MAX_ITEMS(4개)까지. 남은 항목이 없으면 그 카드를 버립니다.
// 그리고
//   4. 카드가 많으면 앞에서부터 MAX_CARDS(3장)까지.
//   5. 남은 카드가 MIN_CARDS(2장)보다 적으면 쓰지 않습니다(null). FE 는 기본 제안 2장으로 대신합니다.
//
// 예시
//   [ {"영단어 30쪽", "WEEKDAY_EVENING", ["폰 치우기", "30쪽 외우기"]},
//     {"주말 점검", "SATURDAY_MORNING", ["밀린 쪽 채우기"]},
//     {"또 저녁", "WEEKDAY_EVENING", ["복습"]} ]              →  앞의 2장 (셋째는 시간대가 겹쳐서 버림)
//   [ {"영단어 30쪽", "MIDNIGHT", ["30쪽"]}, {"주말", "SATURDAY_MORNING", ["점검"]} ]  →  null (1장만 남음)
public final class RoutineCardDraft {

  // 카드 제목의 최대 글자 수 (FE 카드 제목 칸과 같음)
  public static final int MAX_TITLE_LENGTH = 40;
  // 체크리스트 항목 하나의 최대 글자 수 (체크리스트 추천과 같음)
  public static final int MAX_ITEM_LENGTH = ChecklistDraft.MAX_ITEM_LENGTH;
  // 카드 한 장의 체크리스트 최대 개수. 루틴은 매주 반복하니 가볍게 잡았습니다.
  public static final int MAX_ITEMS = 4;

  // 검사를 통과한 카드 한 장
  public record RoutineCard(String title, RoutineCardPrompt.Slot slot, List<String> checklist) {}

  private RoutineCardDraft() {}

  // 쓸 수 있는 카드만 남깁니다. 2장보다 적으면 null 입니다.
  public static List<RoutineCard> from(RoutineCardPrompt.Answer answer) {
    if (answer == null || answer.cards() == null) {
      return null;
    }
    List<RoutineCard> cards = new ArrayList<>();
    Set<RoutineCardPrompt.Slot> usedSlots = EnumSet.noneOf(RoutineCardPrompt.Slot.class);
    for (RoutineCardPrompt.RawCard raw : answer.cards()) {
      // 규칙 4
      if (cards.size() >= RoutineCardPrompt.MAX_CARDS) {
        break;
      }
      if (raw == null) {
        continue;
      }
      // 규칙 1
      String title = raw.title() == null ? "" : raw.title().trim();
      if (title.isEmpty()
          || title.length() > MAX_TITLE_LENGTH
          || ToneGuide.hasBannedExpression(title)) {
        continue;
      }
      // 규칙 2
      RoutineCardPrompt.Slot slot = slotOf(raw.slot());
      if (slot == null || usedSlots.contains(slot)) {
        continue;
      }
      // 규칙 3
      List<String> checklist = checklistOf(raw.checklist());
      if (checklist.isEmpty()) {
        continue;
      }
      usedSlots.add(slot);
      cards.add(new RoutineCard(title, slot, checklist));
    }
    // 규칙 5
    return cards.size() < RoutineCardPrompt.MIN_CARDS ? null : cards;
  }

  // 'WEEKDAY_EVENING' → Slot.WEEKDAY_EVENING. 모르는 이름이면 null
  private static RoutineCardPrompt.Slot slotOf(String name) {
    if (name == null) {
      return null;
    }
    for (RoutineCardPrompt.Slot slot : RoutineCardPrompt.Slot.values()) {
      if (slot.name().equals(name.trim())) {
        return slot;
      }
    }
    return null;
  }

  // 체크리스트 항목 검사 (규칙 3)
  private static List<String> checklistOf(List<String> rawItems) {
    List<String> items = new ArrayList<>();
    if (rawItems == null) {
      return items;
    }
    for (String raw : rawItems) {
      if (items.size() >= MAX_ITEMS) {
        break;
      }
      String item = raw == null ? "" : raw.trim();
      if (item.isEmpty()
          || item.length() > MAX_ITEM_LENGTH
          || ToneGuide.hasBannedExpression(item)
          || items.contains(item)) {
        continue;
      }
      items.add(item);
    }
    return items;
  }
}
