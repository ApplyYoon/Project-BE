package com.ithopeschool.projectbe.domain.plan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// "말로 계획 세우기" 에서 GPT 가 돌려준 계획 초안 한 줄입니다. (이슈 #86)
// GPT 의 답은 그대로 쓰지 않고 from 이 줄마다 다시 검사합니다. GPT 도 틀릴 수 있기 때문입니다.
//
// 검사 규칙 (계획 추가 API #12 와 같은 범위)
//   1. kind 가 study · play 가 아니면 그 줄은 버립니다.
//   2. content 는 앞뒤 공백을 지우고, 비었거나 200자를 넘으면 그 줄은 버립니다.
//   3. targetMinutes 가 없거나 1 ~ 600 밖이면 그 줄은 버립니다.
//   4. startTime 은 없으면(null) 시각 미정이고, "19:00" 모양(00:00 ~ 23:59)이 아니면 그 줄은 버립니다.
//   5. subjectId 가 내 과목 번호가 아니면 버리지 않고 비웁니다(null). 새 과목은 만들지 않습니다.
//      노는 계획(play)은 과목이 없어서 항상 비웁니다.
//   6. 초안은 MAX_DRAFTS 개까지만 돌려줍니다. 남은 줄은 버립니다.
//
// 예시 (내 과목 번호: 3, 4)
//   study · "3"  · "문제집" · 120 · null     →  그대로
//   study · "99" · "단어"   · 30  · null     →  subjectId 만 null (남의 과목·없는 과목)
//   play  · "3"  · "게임"   · 60  · "21:00"  →  subjectId 만 null (노는 계획)
//   study · "3"  · "문제집" · 0   · null     →  버림 (1분 미만)
//   study · "3"  · "문제집" · 60  · "25:00"  →  버림 (없는 시각)
//
// subjectId: 과목 번호(글자). 못 골랐거나 노는 계획이면 null / content: 할 일
// targetMinutes: 목표 시간(분) / startTime: 시작 시각 "HH:mm", 문장에 시각이 없으면 null
// kind: "study"(공부) 또는 "play"(놀기)
public record PlanDraft(
    String subjectId, String content, int targetMinutes, String startTime, String kind) {

  // 한 번에 돌려주는 초안의 최대 개수입니다. 한 문장(200자)에 이보다 많은 계획이 나오면 이상한 답으로 봅니다.
  public static final int MAX_DRAFTS = 10;

  // 계획 추가 API(StudyPlanCreateRequest)와 같은 범위입니다.
  static final int MAX_CONTENT_LENGTH = 200;
  static final int MIN_TARGET_MINUTES = 1;
  static final int MAX_TARGET_MINUTES = 600;
  private static final Pattern START_TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

  // GPT 가 돌려주는 JSON 모양 그대로입니다. (PlanParsePrompt.schema 와 같은 칸)
  //   { "crisis": false,
  //     "plans": [ { "subjectId": "3", "content": "문제집", "targetMinutes": 120,
  //                  "startTime": null, "kind": "study" } ] }
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Raw(Boolean crisis, List<Item> plans) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        String subjectId, String content, Integer targetMinutes, String startTime, String kind) {}
  }

  // GPT 의 답에서 쓸 수 있는 줄만 골라 초안으로 만듭니다.
  // mySubjectIds: 내 과목 번호들(글자). 이 안에 있는 번호만 subjectId 로 남깁니다.
  public static List<PlanDraft> from(Raw raw, Set<String> mySubjectIds) {
    List<PlanDraft> drafts = new ArrayList<>();
    if (raw == null || raw.plans() == null) {
      return drafts;
    }
    for (Raw.Item item : raw.plans()) {
      // 규칙 6
      if (drafts.size() >= MAX_DRAFTS) {
        break;
      }
      if (item == null) {
        continue;
      }

      // 규칙 1
      PlanKind kind = PlanKind.fromValue(item.kind());
      if (kind == null) {
        continue;
      }

      // 규칙 2
      String content = item.content() == null ? "" : item.content().trim();
      if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
        continue;
      }

      // 규칙 3
      Integer minutes = item.targetMinutes();
      if (minutes == null || minutes < MIN_TARGET_MINUTES || minutes > MAX_TARGET_MINUTES) {
        continue;
      }

      // 규칙 4
      String startTime = item.startTime();
      if (startTime != null && !START_TIME.matcher(startTime).matches()) {
        continue;
      }

      // 규칙 5
      String subjectId = item.subjectId();
      // (Set.of 로 만든 목록은 null 을 물으면 에러가 나서, null 을 먼저 거릅니다)
      if (kind == PlanKind.PLAY || subjectId == null || !mySubjectIds.contains(subjectId)) {
        subjectId = null;
      }

      drafts.add(new PlanDraft(subjectId, content, minutes, startTime, kind.toValue()));
    }
    return drafts;
  }
}
