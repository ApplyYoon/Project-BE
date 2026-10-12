package com.ithopeschool.projectbe.domain.safety;

import java.util.List;
import java.util.regex.Pattern;

// "위기 표현" 검사기입니다. (이슈 #56, 기획서 v2 5장 「안전 브레이크」)
// 일기·체크인·통화 요약 같은 글에 "죽고 싶다" 같은 표현이 있는지 봅니다.
// 사용자 대부분이 미성년자라서, 이 검사가 true 면 어떤 설정보다 먼저 모든 채찍을 멈추고 도움 연결을 안내합니다.
//
// 글의 띄어쓰기·줄바꿈을 모두 지운 뒤에, 목록의 글자가 들어 있는지 봅니다.
// 그래서 "죽고 싶다" 와 "죽고싶다" 를 같게 봅니다.
//
//   "오늘 학원에서 힘들었다"              →  false
//   "이제 그만 살고 싶다는 생각이 들었다"  →  true   ('그만살고싶')
//   "죽고싶다" · "죽고 싶어" · "사라지고 싶어" · "자해했어" · "극단적 선택"  →  모두 true
//   "오래오래 살고 싶다"                  →  false  ('살고싶' 만으로는 잡지 않습니다)
//
// 놓치는 것보다 한 번 더 안내하는 쪽이 안전하다고 보고, 일부러 넓게 잡습니다.
//   - "죽고 싶지는 않아" 처럼 부정하는 말도 잡힙니다.
//   - 띄어쓰기를 지우기 때문에 "혼자 살기"(혼자살기) · "혼자 해냈다"(혼자해냈다) 처럼
//     낱말 사이에서 우연히 '자살' · '자해' 가 만들어지는 글도 잡힙니다. (FE 도 똑같이 잡습니다)
//
// 글자만 보는 검사라서 돌려 말한 표현은 놓칩니다. 그래서 이슈 #56 은 GPT 판단도 같이 쓰고,
// 둘 중 하나라도 걸리면 감지로 보기로 했습니다. 이 파일은 그중 "단어 목록" 쪽입니다.
//
// 이 검사기는 true·false 만 돌려줍니다. 받은 글은 어디에도 남기지 않고, 로그에도 찍지 않습니다.
//
// FE 의 hasCrisisText (src/lib/safetyBrake.ts) 와 같은 규칙·같은 목록입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판단이 어긋나지 않습니다.
public final class CrisisText {

  // 위기 표현 목록입니다. 전문가 검토(Tmax-IT-Hope-School/Project#8) 결과로 바뀔 수 있어서 한 곳에 모아 둡니다.
  //
  // 적는 규칙
  //   - 띄어쓰기 없이 적습니다. 검사할 때 글의 띄어쓰기를 모두 지우고 비교하기 때문입니다.
  //   - 끝의 어미(다 · 어 · 었어 …)는 빼고 적습니다.
  //     "죽고싶" 하나로 "죽고 싶다" · "죽고싶어" · "죽고 싶었어" 를 모두 잡습니다.
  public static final List<String> KEYWORDS =
      List.of("죽고싶", "사라지고싶", "그만살고싶", "살고싶지않", "살기싫", "자살", "자해", "극단적선택", "극단적인선택");

  // 글에서 지울 "빈칸" 들입니다. 띄어쓰기·줄바꿈·탭이 이어진 부분을 한 번에 찾습니다.
  // 자바의 \s 는 띄어쓰기·탭·줄바꿈만 보는데, FE(자바스크립트)의 \s 는 전각 공백(U+3000) 같은
  // 특수 빈칸도 같이 봅니다. FE 와 똑같이 지우려고 그 글자들을 직접 더 적었습니다.
  private static final Pattern SPACES =
      Pattern.compile(
          "[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+");

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CrisisText() {}

  // 글 여러 개 중 하나에라도 위기 표현이 있으면 true 를 돌려줍니다.
  // texts: 검사할 글 원문들. 일기·체크인의 약속·통화 요약처럼 여러 글을 한 번에 넘깁니다.
  //        글이 없는 칸(null)이 섞여 있어도 됩니다. 그 칸은 건너뜁니다.
  public static boolean hasCrisisText(List<String> texts) {
    for (String text : texts) {
      if (text != null && containsKeyword(text)) {
        return true;
      }
    }
    return false;
  }

  // 글 하나에 목록의 표현이 들어 있는지 봅니다.
  private static boolean containsKeyword(String text) {
    // 빈칸을 전부 지워서 붙여 씁니다. ("죽고 싶다" → "죽고싶다")
    String compactText = SPACES.matcher(text).replaceAll("");
    for (String keyword : KEYWORDS) {
      if (compactText.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
