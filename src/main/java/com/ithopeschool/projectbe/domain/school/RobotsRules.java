package com.ithopeschool.projectbe.domain.school;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 학교 홈페이지의 robots.txt 를 읽은 결과입니다. (2026-10-18, SchoolHomepageCrawler)
// robots.txt 는 사이트가 "로봇(자동으로 페이지를 읽는 프로그램)은 여기 오지 마" 를 적어 두는 파일입니다.
//
// ■ 우리 규칙 (예의 지키기)
//   - 어느 로봇에게라도 막아 둔 경로(Disallow)는 우리도 가지 않습니다. 우리 이름이 적혀 있지 않아도요.
//     (예: 분당고는 Googlebot · NaverBot 에게 /upload/ · /editor/ 를 막아 둠 → 우리도 안 감)
//   - "Disallow: /" 가 있으면 그 학교 홈페이지는 아예 읽지 않습니다.
//   - robots.txt 가 없으면(404 등) 다 열린 것으로 봅니다. 서버가 아파서(5xx · 시간 초과) 못 읽으면
//     그날은 아무것도 읽지 않습니다. (allowAll · denyAll — 구글이 쓰는 약속과 같음)
//
//   RobotsRules.parse("User-agent: NaverBot\nDisallow: /upload/").allows("/upload/a.png") → false
//   RobotsRules.parse("User-agent: NaverBot\nDisallow: /upload/").allows("/main.do")      → true
final class RobotsRules {

  // 막힌 경로들. 정규식으로 바꿔 둡니다. ('*' 는 아무 글자, 끝의 '$' 는 "여기서 끝")
  private final List<Pattern> blocked;
  private final boolean blocksEverything;

  private RobotsRules(List<Pattern> blocked, boolean blocksEverything) {
    this.blocked = blocked;
    this.blocksEverything = blocksEverything;
  }

  // robots.txt 가 없을 때: 모두 허용
  static RobotsRules allowAll() {
    return new RobotsRules(List.of(), false);
  }

  // robots.txt 를 못 읽었을 때(서버 아픔): 모두 막음
  static RobotsRules denyAll() {
    return new RobotsRules(List.of(), true);
  }

  // robots.txt 글을 읽습니다. 모든 묶음(User-agent)의 Disallow 를 모읍니다.
  static RobotsRules parse(String text) {
    List<Pattern> blocked = new ArrayList<>();
    boolean blocksEverything = false;
    for (String rawLine : text.split("\\r?\\n")) {
      // '#' 뒤는 설명이라 뗍니다.
      String line = rawLine.replaceAll("#.*$", "").trim();
      if (!line.toLowerCase().startsWith("disallow:")) {
        continue;
      }
      String path = line.substring("disallow:".length()).trim();
      if (path.isEmpty()) {
        // "Disallow:" 만 있으면 "막는 것 없음" 이라는 뜻입니다.
        continue;
      }
      if (path.equals("/") || path.equals("/*")) {
        blocksEverything = true;
      }
      blocked.add(toPattern(path));
    }
    return new RobotsRules(blocked, blocksEverything);
  }

  // 이 경로(물음표 뒤 포함, 예 '/bundang-h/main.do')에 가도 되는지
  boolean allows(String path) {
    if (blocksEverything) {
      return false;
    }
    String target = path == null || path.isEmpty() ? "/" : path;
    for (Pattern pattern : blocked) {
      if (pattern.matcher(target).lookingAt()) {
        return false;
      }
    }
    return true;
  }

  // '/upload/*.hwp$' → 정규식. 글자는 그대로 비교하고 '*' 만 "아무 글자" 로 바꿉니다.
  private static Pattern toPattern(String path) {
    boolean endsHere = path.endsWith("$");
    String body = endsHere ? path.substring(0, path.length() - 1) : path;
    String[] parts = body.split("\\*", -1);
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(parts[i]));
    }
    if (endsHere) {
      regex.append("$");
    }
    return Pattern.compile(regex.toString());
  }
}
