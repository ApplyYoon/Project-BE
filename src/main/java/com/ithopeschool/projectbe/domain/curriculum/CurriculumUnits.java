package com.ithopeschool.projectbe.domain.curriculum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 사진 채점 · 확인 문제가 함께 쓰는 "단원" 목록입니다. (2026-10-26 소유자 결정, BE #401)
// 단원 = 교육과정 성취기준의 '영역' 입니다. 예) 공통수학1 › 다항식, 공통국어1 › 문학
// 영역이 비어 있는 과목(국어 · 영어 일부)은 과목 이름을 단원으로 씁니다. 예) 문학
//
// AI 에게 이 목록(lines)을 보여 주고 그중에서 글자 그대로 고르게 합니다.
// 목록에 없는 글자를 고르면 단원을 비웁니다(null). 그래야 같은 단원이 다른 글자로 흩어지지 않습니다.
//
//   lines : AI 에게 보여 줄 줄들. "- 공통수학1: 다항식 · 방정식과 부등식 · 경우의 수 · 행렬"
//   names : 고를 수 있는 단원 이름 전체 (pick 이 확인할 때 씁니다)
public record CurriculumUnits(List<String> lines, Set<String> names) {

  // 단원 이름의 최대 글자 수. 지금 목록에서 가장 긴 이름이 25자입니다. (DB 칸 길이와 같은 값)
  public static final int MAX_UNIT_LENGTH = 30;

  // 목록이 없을 때. pick 은 늘 null 입니다.
  public static final CurriculumUnits EMPTY = new CurriculumUnits(List.of(), Set.of());

  // 성취기준 줄들 → 과목마다 영역을 모은 목록. 순서는 파일 순서(과목 · 영역 순서)입니다.
  public static CurriculumUnits of(List<AchievementStandard> standards) {
    // 과목 이름 → 그 과목의 단원들 (같은 이름은 한 번만)
    Map<String, Set<String>> unitsBySubject = new LinkedHashMap<>();
    for (AchievementStandard standard : standards) {
      String unit = unitOf(standard);
      if (unit.length() > MAX_UNIT_LENGTH) {
        continue;
      }
      unitsBySubject
          .computeIfAbsent(standard.subject(), subject -> new LinkedHashSet<>())
          .add(unit);
    }

    List<String> lines = new ArrayList<>();
    Set<String> names = new LinkedHashSet<>();
    for (Map.Entry<String, Set<String>> entry : unitsBySubject.entrySet()) {
      lines.add("- " + entry.getKey() + ": " + String.join(" · ", entry.getValue()));
      names.addAll(entry.getValue());
    }
    return new CurriculumUnits(List.copyOf(lines), Set.copyOf(names));
  }

  // 성취기준 한 줄의 단원. 영역이 비었으면 과목 이름입니다.
  static String unitOf(AchievementStandard standard) {
    String area = standard.area().trim();
    return area.isEmpty() ? standard.subject().trim() : area;
  }

  // AI 가 고른 단원 글자를 확인합니다. 앞뒤 공백을 지우고, 목록에 있으면 그 이름, 없으면 null 입니다.
  public String pick(String text) {
    if (text == null) {
      return null;
    }
    String unit = text.trim();
    return names.contains(unit) ? unit : null;
  }
}
