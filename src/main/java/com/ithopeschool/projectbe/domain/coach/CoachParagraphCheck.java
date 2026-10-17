package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// AI 가 쓴 문단을 내보내도 되는지 검사하는 곳입니다. 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// AI 는 가끔 숫자를 지어내거나 시킨 것과 다르게 씁니다. 그래서 내보내기 전에 꼭 한 번 봅니다.
// 하나라도 걸리면 그 문단은 버리고, FE 가 숫자를 끼워 넣어 만든 "기본 문장" 을 대신 보여줍니다. (이슈 #87)
//
//   1. 비어 있지 않고 너무 길지 않은가
//   2. 문단에 나온 숫자가 모두 우리가 준 숫자인가   ← 지어낸 숫자를 막습니다
//   3. 깎아내리는 말이 들어 있지 않은가               ← 미성년자가 읽는 글입니다 (CLAUDE.md 4장)
//      금지 표현의 목록과 검사는 공통 말투 지침(ToneGuide)에 있습니다. 여기서는 그 검사를 부르기만 합니다.
public final class CoachParagraphCheck {

  // 문단의 최대 길이(글자). 3~4문장이면 넉넉히 들어갑니다. 이보다 길면 시킨 대로 쓰지 않은 것으로 봅니다.
  public static final int MAX_LENGTH = 300;

  // 글 속의 숫자 덩어리를 찾습니다. 예: "55%" 에서 55, "평균 23분" 에서 23
  private static final Pattern DIGITS = Pattern.compile("\\d+");

  private CoachParagraphCheck() {}

  // 내보내도 되는 문단이면 true 입니다.
  public static boolean isSafe(String paragraph, List<WeeklyCoachRequest.Number> numbers) {
    if (paragraph == null || paragraph.isBlank()) {
      return false;
    }
    if (paragraph.length() > MAX_LENGTH) {
      return false;
    }
    return usesOnlyGivenNumbers(paragraph, numbers) && !hasBannedWord(paragraph);
  }

  // 문단에 나온 숫자가 모두 우리가 준 숫자인지 확인합니다.
  //   준 숫자: 64, 12, 55, 20   문단: "계획의 64% 를 했어. 완주율은 55% 야."  -> true
  //   준 숫자: 64, 12, 55, 20   문단: "계획의 70% 를 했어."                    -> false (70 은 준 적이 없습니다)
  static boolean usesOnlyGivenNumbers(String paragraph, List<WeeklyCoachRequest.Number> numbers) {
    Set<String> given = new HashSet<>();
    for (WeeklyCoachRequest.Number number : numbers) {
      // 이름·값·근거 어디에 있든 우리가 준 숫자입니다. 예: "계획 12개" 의 12
      given.addAll(digitsIn(number.label()));
      given.addAll(digitsIn(number.value()));
      given.addAll(digitsIn(number.basis()));
    }
    return given.containsAll(digitsIn(paragraph));
  }

  // 깎아내리는 말이 들어 있으면 true 입니다. (공통 말투 지침의 금지 표현 검사)
  static boolean hasBannedWord(String paragraph) {
    return ToneGuide.hasBannedExpression(paragraph);
  }

  // 글에서 숫자 덩어리를 모두 꺼냅니다.  "20번 중 11번" -> ["20", "11"]
  private static Set<String> digitsIn(String text) {
    Set<String> found = new HashSet<>();
    Matcher matcher = DIGITS.matcher(text);
    while (matcher.find()) {
      found.add(matcher.group());
    }
    return found;
  }
}
