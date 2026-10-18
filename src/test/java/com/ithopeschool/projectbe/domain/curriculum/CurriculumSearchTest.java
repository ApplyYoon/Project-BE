package com.ithopeschool.projectbe.domain.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 성취기준 고르기(글자 맞추기)를 작은 목록으로 확인합니다.
class CurriculumSearchTest {

  private static AchievementStandard std(String subject, String code, String area, String text) {
    return new AchievementStandard("2022", subject, code, area, text);
  }

  private static final List<AchievementStandard> ALL =
      List.of(
          std("대수", "12대수03-01", "수열", "수열의 뜻을 설명할 수 있다."),
          std("미적분Ⅰ", "12미적Ⅰ-01-01", "함수의 극한과 연속", "함수의 극한의 뜻을 알고, 이를 설명할 수 있다."),
          std("미적분Ⅰ", "12미적Ⅰ-01-02", "함수의 극한과 연속", "함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다."),
          std("미적분Ⅱ", "12미적Ⅱ-01-01", "수열의 극한", "수열의 수렴, 발산의 뜻을 알고, 이를 판별할 수 있다."),
          std("확률과 통계", "12확통01-01", "경우의 수", "중복순열과 중복조합을 이해하고, 그 경우의 수를 구할 수 있다."),
          std("영어 Ⅱ", "12영Ⅱ-01-01", "이해", "다양한 주제에 대한 말이나 글의 세부 정보를 파악한다."));

  @Test
  @DisplayName("띄어쓰기 · 로마 숫자 · 한글 뒤 I 를 고르게 바꾼다 (NEIS '영어Ⅱ' 와 고시 '영어 Ⅱ' 가 같게)")
  void normalize() {
    assertThat(CurriculumSearch.normalize("영어 Ⅱ")).isEqualTo("영어2");
    assertThat(CurriculumSearch.normalize("영어Ⅱ")).isEqualTo("영어2");
    assertThat(CurriculumSearch.normalize("미적분I")).isEqualTo("미적분1");
    assertThat(CurriculumSearch.normalize("미적분 II")).isEqualTo("미적분2");
    assertThat(CurriculumSearch.normalize("공통수학1")).isEqualTo("공통수학1");
    assertThat(CurriculumSearch.normalize(null)).isEmpty();
  }

  @Test
  @DisplayName("질문을 낱말로: 조사를 떼고, 2글자 미만 · 흔한 말은 빼고, 같은 낱말은 하나만")
  void keywords() {
    assertThat(CurriculumSearch.keywords("수열의 극한이 뭔지 모르겠어")).containsExactly("수열", "극한");
    assertThat(CurriculumSearch.keywords("극한, 극한값을 구하는 문제 좀 알려줘!"))
        .containsExactly("극한", "극한값", "구하"); // '구하는' 의 '는' 도 조사처럼 떼져서 '구할' 과도 맞음
    assertThat(CurriculumSearch.keywords(null)).isEmpty();
  }

  @Test
  @DisplayName("낱말이 들어 있는 줄을 고르고, 과목 이름을 말했으면 그 과목이 먼저다")
  void namedSubjectComesFirst() {
    List<AchievementStandard> found =
        CurriculumSearch.rank(ALL, List.of(), "미적분Ⅱ 수열 극한 판별이 헷갈려", 2);

    assertThat(found).extracting(AchievementStandard::code).first().isEqualTo("12미적Ⅱ-01-01");
  }

  @Test
  @DisplayName("오늘 시간표 과목에 점수를 더한다 (과목을 말하지 않았을 때)")
  void todaySubjectBonus() {
    List<AchievementStandard> found =
        CurriculumSearch.rank(ALL, List.of("미적분Ⅰ"), "극한값 구하는 게 어려워", 3);

    assertThat(found).extracting(AchievementStandard::code).first().isEqualTo("12미적Ⅰ-01-02");
  }

  @Test
  @DisplayName("과목 이름의 앞부분('미적분')만 말해도 그 과목들에 점수를 준다")
  void subjectPrefix() {
    List<AchievementStandard> found = CurriculumSearch.rank(ALL, List.of(), "미적분 극한의 뜻", 2);

    assertThat(found)
        .hasSize(2)
        .extracting(AchievementStandard::subject)
        .allMatch(subject -> subject.startsWith("미적분"));
  }

  @Test
  @DisplayName("줄인 과목 이름(확통)도 알아듣는다")
  void alias() {
    List<AchievementStandard> found = CurriculumSearch.rank(ALL, List.of(), "확통 중복조합", 1);

    assertThat(found).extracting(AchievementStandard::code).containsExactly("12확통01-01");
  }

  @Test
  @DisplayName("과목 이름만 있고 내용 낱말이 없으면 아무것도 고르지 않는다")
  void subjectOnlyPicksNothing() {
    assertThat(CurriculumSearch.rank(ALL, List.of("미적분Ⅰ"), "미적분 문제 모르겠어", 3)).isEmpty();
    assertThat(CurriculumSearch.rank(ALL, List.of(), "안녕", 3)).isEmpty();
  }

  @Test
  @DisplayName("limit 개까지, 같은 점수면 파일 순서(단원 순서)대로")
  void limitAndOrder() {
    List<AchievementStandard> found = CurriculumSearch.rank(ALL, List.of(), "함수 극한", 1);

    assertThat(found).extracting(AchievementStandard::code).containsExactly("12미적Ⅰ-01-01");
  }
}
