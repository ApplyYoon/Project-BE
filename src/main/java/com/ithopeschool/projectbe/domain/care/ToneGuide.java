package com.ithopeschool.projectbe.domain.care;

import java.util.List;

// AI 가 학생에게 하는 말의 "말투 지침" 을 한곳에 모은 파일입니다. (이슈 #65)
// 통화 대본(CallScript)과 코치 문단(CoachPrompt)이 여기의 규칙을 같이 씁니다.
// 말투 규칙을 고칠 때는 이 파일만 고치면 두 곳에 같이 반영됩니다.
//
// ■ 말투 4가지 (관리 강도 0 ~ 4 단계와 짝입니다. CareLevelInfo)
//   SUPPORT  위로      회복(0). 아쉬운 점을 지적하지 않고 응원만 합니다.
//   PRAISE   칭찬      당근(1). 잘한 점을 숫자와 함께 구체적으로 칭찬합니다.
//   PLAIN    담백      넛지(2). 사실만 말합니다. 칭찬도 지적도 길게 하지 않습니다.
//   FRANK    솔직      채찍(3·4). 아쉬운 점을 돌려 말하지 않고 짚습니다. 그래도 사람은 깎아내리지 않습니다.
//
// ■ 어떤 설정보다 먼저인 것
//   1. 힘든 날에는 위로가 먼저입니다. 기분이 낮거나 안전 브레이크가 켜져 있으면 단계와 상관없이 SUPPORT 입니다.
//   2. 독설 허용 수위(1 ~ 5)가 2 이하면, 채찍 단계여도 FRANK 대신 PLAIN 입니다.
//      허용 수위는 "여기까지는 괜찮다" 는 한도이지, 그만큼 세게 하라는 뜻이 아닙니다.
//   3. 어떤 말투에서도 외모·성적·능력을 깎아내리거나 다른 사람과 비교하지 않습니다. 지적은 한 일(행동)에만 합니다.
//      (미성년자가 듣고 읽는 말입니다. CLAUDE.md 4장)
//
// ■ 두 겹으로 지킵니다
//   - AI 에게 주는 지침에 금지 규칙(NO_PUT_DOWN_RULE)을 항상 넣습니다.
//   - AI 가 쓴 글은 내보내기 전에 금지 표현 검사(hasBannedExpression)를 거칩니다. 걸리면 그 글은 버립니다.
//   통화는 소리가 학생의 브라우저와 AI 회사 사이를 직접 오가서 서버가 검사할 수 없습니다. 그래서 통화는 지침이 유일한 안전장치입니다.
public final class ToneGuide {

  // 말투의 종류입니다.
  public enum Tone {
    // 위로
    SUPPORT,
    // 칭찬
    PRAISE,
    // 담백
    PLAIN,
    // 솔직
    FRANK
  }

  // 모든 AI 지침에 똑같이 들어가는 금지 규칙입니다. (어떤 것 3)
  public static final String NO_PUT_DOWN_RULE = "외모·성적·능력을 깎아내리지 마. 다른 사람과 비교하지 마.";

  // 독설 허용 수위가 이 값 이하면 솔직한 말투(FRANK)를 쓰지 않습니다.
  public static final int PLAIN_MAX_HARSHNESS = 2;

  // 깎아내리거나 남과 비교하는 말의 목록입니다. AI 가 쓴 글에 하나라도 들어 있으면 그 글을 버립니다.
  // 목록을 늘릴 때는 평범한 문장이 걸리지 않는지 테스트(ToneGuideTest)에 예문을 같이 넣어 주세요.
  private static final List<String> BANNED_EXPRESSIONS =
      List.of(
          "멍청", "한심", "바보", "쓰레기", "게으르", "게을러", "게으른", "못생", "머리가 나쁘", "구제불능", "실망이야", "남들은",
          "다른 애들");

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ToneGuide() {}

  // "오늘의 관리 강도" 를 아는 곳(통화)에서 쓸 말투를 고릅니다.
  //
  // careLevel: 오늘의 관리 강도 (0 회복 ~ 4 강제적 채찍. 4 는 잠금 기능의 단계라 말투는 3 과 같습니다)
  // maxHarshness: 사용자가 허용한 독설 수위 (1 ~ 5)
  // needsSupport: 기분이 낮거나 안전 브레이크가 켜져 있어서 위로가 먼저인 날이면 true
  //
  //   (0, 3, false) → SUPPORT   (1, 3, false) → PRAISE   (2, 3, false) → PLAIN   (3, 3, false) →
  // FRANK
  //   (3, 2, false) → PLAIN     (3, 5, true)  → SUPPORT
  public static Tone toneOf(int careLevel, int maxHarshness, boolean needsSupport) {
    if (needsSupport || careLevel <= 0) {
      return Tone.SUPPORT;
    }
    if (careLevel == 1) {
      return Tone.PRAISE;
    }
    if (careLevel == 2 || maxHarshness <= PLAIN_MAX_HARSHNESS) {
      return Tone.PLAIN;
    }
    return Tone.FRANK;
  }

  // "오늘의 관리 강도" 는 모르고 사용자가 정한 한도만 아는 곳(코치 문단)에서 쓸 말투를 고릅니다.
  // 오늘 몇 단계인지 모르니 칭찬(PRAISE)을 따로 고르지 않고, 한도가 0 이면 위로, 그 밖에는 허용 수위로 정합니다.
  //
  // maxCareLevel: 사용자가 정한 관리 강도 최대 한도 (0 ~ 4). 안전 브레이크가 켜져 있으면 부르는 쪽에서 0 을 넘깁니다.
  // maxHarshness: 사용자가 허용한 독설 수위 (1 ~ 5)
  //
  //   (0, 5) → SUPPORT   (1, 2) → PLAIN   (4, 2) → PLAIN   (1, 3) → FRANK   (4, 5) → FRANK
  public static Tone toneWithinLimit(int maxCareLevel, int maxHarshness) {
    if (maxCareLevel <= 0) {
      return Tone.SUPPORT;
    }
    if (maxHarshness <= PLAIN_MAX_HARSHNESS) {
      return Tone.PLAIN;
    }
    return Tone.FRANK;
  }

  // 글에 깎아내리거나 남과 비교하는 말이 들어 있으면 true 입니다. AI 가 쓴 글을 내보내기 전에 꼭 거칩니다.
  //
  //   "완주율이 55% 라니 한심하다."     →  true
  //   "화요일은 40% 로 조금 낮았어."    →  false
  public static boolean hasBannedExpression(String text) {
    for (String expression : BANNED_EXPRESSIONS) {
      if (text.contains(expression)) {
        return true;
      }
    }
    return false;
  }
}
