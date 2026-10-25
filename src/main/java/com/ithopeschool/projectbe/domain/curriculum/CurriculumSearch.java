package com.ithopeschool.projectbe.domain.curriculum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 학생의 말(질문 · 문제 주제)에 맞는 성취기준 몇 줄을 고릅니다. "가벼운 RAG" 의 "찾기" 부분입니다. (2026-10-18)
// 벡터 DB · 임베딩 없이 글자 맞추기로만 고릅니다. 성취기준이 교육과정 버전마다 1,200줄 정도라 이걸로 충분하고, 학생이 읽고 고칠 수 있습니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 점수 (성취기준 한 줄마다)
//   1. 낱말 점수: 질문의 낱말(조사를 떼고 2글자 이상, 흔한 말은 뺌)이 그 줄(과목 · 영역 · 문장)에 들어 있으면 하나에 2점
//      → 낱말 점수가 0 이면 고르지 않습니다. (과목 이름만 같고 내용이 상관없는 줄은 넣지 않기)
//   2. 질문에 과목 이름이 있으면 그 과목 줄에 5점 (예: "미적분Ⅰ 극한 모르겠어")
//      과목 이름의 앞부분만 있으면 3점 (예: "미적분" → 미적분Ⅰ · 미적분Ⅱ 둘 다)
//   3. 오늘 시간표에 있는 과목이면 2점
//   점수가 높은 순서로, 같으면 파일 순서(단원 순서)대로 limit 개까지.
//
//   rank(전체, ["미적분Ⅰ"], "함수의 극한이 뭔지 모르겠어", 3)
//     → [12미적Ⅰ-01-01 함수의 극한의 뜻 …, 12미적Ⅰ-01-02 …] 처럼 '함수' · '극한' 이 들어 있고 오늘 과목인 줄이 앞에
public final class CurriculumSearch {

  static final int PER_KEYWORD = 2;
  static final int SUBJECT_NAMED = 5;
  static final int SUBJECT_PREFIX_NAMED = 3;
  static final int SUBJECT_TODAY = 2;
  // 이보다 짧은 낱말은 버립니다. (한 글자는 거의 모든 줄에 들어 있어서)
  static final int MIN_KEYWORD_LENGTH = 2;

  // 낱말 끝에서 떼는 조사. 긴 것부터 봅니다. (예: "극한에서" → "극한", "수열의" → "수열")
  static final List<String> PARTICLES =
      List.of(
          "에서는", "에서", "으로", "이랑", "하고", "에게", "까지", "부터", "이란", "이야", "인데", "은", "는", "이", "가",
          "을", "를", "의", "에", "로", "와", "과", "도", "만", "랑", "란", "요");

  // 질문에 자주 나오지만 내용과 상관없는 말. 낱말 점수에서 뺍니다.
  static final Set<String> STOP_WORDS =
      Set.of(
          "문제", "이거", "이것", "뭐야", "뭔지", "모르겠어", "모르겠다", "설명", "설명해", "알려", "알려줘", "어떻게", "이해", "공부",
          "오늘", "내일", "질문", "해줘", "정답", "풀이", "풀어", "풀어줘", "방법", "어려워", "헷갈려", "선생님", "과목", "단원",
          "시험", "개념", "그냥", "진짜", "너무", "다시", "조금", "있어", "없어", "하는", "하기");

  // 학생들이 자주 줄여 부르는 과목 이름 (줄인 말 → 원래 이름)
  // (보통 낱말과 겹치는 줄임말은 넣지 않습니다. 예: '통과' 는 '시험 통과' 와 헷갈려서 뺌)
  static final Map<String, String> SUBJECT_ALIASES = Map.of("확통", "확률과 통계");

  private CurriculumSearch() {}

  // 비교하려고 글자를 고르게 바꿉니다. 띄어쓰기를 지우고, 로마 숫자(Ⅰ · Ⅱ · Ⅲ, 한글 뒤의 I · II · III)를 아라비아 숫자로,
  // 영어는 소문자로 바꿉니다. NEIS 시간표의 "영어Ⅱ" 와 고시의 "영어 Ⅱ" 를 같은 과목으로 보려고 합니다.
  //   normalize("영어 Ⅱ") → "영어2"   normalize("미적분I") → "미적분1"   normalize("공통수학1") → "공통수학1"
  public static String normalize(String text) {
    if (text == null) {
      return "";
    }
    String s = text.replaceAll("\\s+", "");
    s = s.replace("Ⅲ", "3").replace("Ⅱ", "2").replace("Ⅰ", "1");
    s = s.replaceAll("(?<=[가-힣])III", "3").replaceAll("(?<=[가-힣])II", "2");
    s = s.replaceAll("(?<=[가-힣])I", "1");
    return s.toLowerCase(Locale.ROOT);
  }

  // 질문을 낱말로 나눕니다. 글자 · 숫자가 아닌 것으로 자르고, 조사를 떼고, 2글자 미만 · 흔한 말은 버립니다. 같은 낱말은 하나만.
  //   keywords("수열의 극한이 뭔지 모르겠어") → ["수열", "극한"]
  public static List<String> keywords(String query) {
    Set<String> words = new LinkedHashSet<>();
    if (query == null) {
      return List.of();
    }
    for (String raw : query.split("[^\\p{L}\\p{N}]+")) {
      String word = stripParticle(raw);
      if (word.length() >= MIN_KEYWORD_LENGTH && !STOP_WORDS.contains(word)) {
        words.add(word);
      }
    }
    return new ArrayList<>(words);
  }

  private static String stripParticle(String word) {
    for (String particle : PARTICLES) {
      if (word.length() > particle.length() + 1 && word.endsWith(particle)) {
        return word.substring(0, word.length() - particle.length());
      }
    }
    return word;
  }

  // 위의 점수 규칙으로 성취기준을 고릅니다. 맞는 줄이 없으면 빈 목록입니다.
  public static List<AchievementStandard> rank(
      List<AchievementStandard> all, List<String> todaySubjects, String query, int limit) {
    String normalizedQuery = normalize(expandAliases(query));
    Set<String> today = new LinkedHashSet<>();
    for (String subject : todaySubjects == null ? List.<String>of() : todaySubjects) {
      today.add(normalize(subject));
    }
    // 과목 이름처럼 보이는 낱말은 낱말 점수에서 뺍니다. (과목 점수로 따로 셈)
    Set<String> subjectNames = new LinkedHashSet<>();
    for (AchievementStandard standard : all) {
      subjectNames.add(normalize(standard.subject()));
    }
    List<String> keywords = new ArrayList<>();
    for (String word : keywords(query)) {
      String normalized = normalize(word);
      boolean isSubjectWord =
          subjectNames.stream()
              .anyMatch(name -> name.equals(normalized) || baseOf(name).equals(normalized));
      if (!isSubjectWord) {
        keywords.add(normalized);
      }
    }

    List<Scored> scored = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      AchievementStandard standard = all.get(i);
      String haystack =
          normalize(standard.subject() + " " + standard.area() + " " + standard.text());
      int keywordScore = 0;
      for (String keyword : keywords) {
        if (haystack.contains(keyword)) {
          keywordScore += PER_KEYWORD;
        }
      }
      if (keywordScore == 0) {
        continue;
      }
      String subject = normalize(standard.subject());
      int subjectScore = 0;
      if (normalizedQuery.contains(subject)) {
        subjectScore = SUBJECT_NAMED;
      } else if (baseOf(subject).length() >= MIN_KEYWORD_LENGTH
          && normalizedQuery.contains(baseOf(subject))) {
        subjectScore = SUBJECT_PREFIX_NAMED;
      }
      int todayScore = today.contains(subject) ? SUBJECT_TODAY : 0;
      scored.add(new Scored(standard, keywordScore + subjectScore + todayScore, i));
    }
    scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
    List<AchievementStandard> picked = new ArrayList<>();
    for (Scored item : scored) {
      if (picked.size() == limit) {
        break;
      }
      picked.add(item.standard());
    }
    return picked;
  }

  // 과목 이름에서 끝의 숫자를 뗀 앞부분. "미적분2" → "미적분", "공통수학1" → "공통수학", "대수" → "대수"
  static String baseOf(String normalizedSubject) {
    return normalizedSubject.replaceAll("\\d+$", "");
  }

  // 줄인 과목 이름을 원래 이름으로 바꿔서 질문에 덧붙입니다. ("확통 문제" → "확통 문제 확률과 통계")
  private static String expandAliases(String query) {
    String text = query == null ? "" : query;
    StringBuilder expanded = new StringBuilder(text);
    for (Map.Entry<String, String> alias : SUBJECT_ALIASES.entrySet()) {
      if (text.contains(alias.getKey())) {
        expanded.append(' ').append(alias.getValue());
      }
    }
    return expanded.toString();
  }

  private record Scored(AchievementStandard standard, int score, int order) {}
}
