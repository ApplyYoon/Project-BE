package com.ithopeschool.projectbe.domain.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 진짜 파일(src/main/resources/curriculum/*.json)을 읽어서 모양을 확인합니다. 파일을 고치면 여기서 깨지는지 봅니다.
class CurriculumLibraryTest {

  private final CurriculumLibrary library = new CurriculumLibrary();

  @Test
  @DisplayName("2022 개정 국어 · 수학 · 영어 · 과학 · 사회의 고등학교 성취기준을 읽는다")
  void loadsAllFiles() {
    List<AchievementStandard> all = library.standards("2022");
    Set<String> subjects =
        all.stream().map(AchievementStandard::subject).collect(Collectors.toSet());

    assertThat(all).hasSizeGreaterThan(1000);
    assertThat(subjects)
        .contains("공통수학1", "대수", "미적분Ⅰ", "확률과 통계", "공통영어1", "공통국어1", "통합과학1", "통합사회1");
    assertThat(library.standards("2015")).isEmpty();
  }

  @Test
  @DisplayName("모든 줄은 고등학교 코드(10 · 12 로 시작)와 문장이 있고, 코드는 겹치지 않는다")
  void everyLineIsWellFormed() {
    List<AchievementStandard> all = library.standards("2022");

    assertThat(all)
        .allSatisfy(
            s -> {
              assertThat(s.code()).matches("1[02].+-\\d\\d");
              assertThat(s.text()).isNotBlank().endsWith(".");
            });
    assertThat(all.stream().map(AchievementStandard::code).distinct().count())
        .isEqualTo(all.size());
  }

  @Test
  @DisplayName("한 과목의 코드는 앞부분이 모두 같다 (해설에 잘못 적힌 '[12동역-01-01]' 같은 줄이 섞이지 않게)")
  void codesShareSubjectPrefix() {
    // 코드 끝의 "두자리-두자리" 를 뗀 앞부분. 12동역01-01 → "12동역", 10공수1-01-01 → "10공수1-"
    Map<String, Set<String>> prefixes = new TreeMap<>();
    for (AchievementStandard s : library.standards("2022")) {
      prefixes
          .computeIfAbsent(s.subject(), subject -> new TreeSet<>())
          .add(s.code().replaceAll("\\d\\d-\\d\\d$", ""));
    }

    assertThat(prefixes).allSatisfy((subject, set) -> assertThat(set).as(subject).hasSize(1));
  }

  @Test
  @DisplayName("고시 원문 문장이 그대로 들어 있다 (미적분Ⅰ 함수의 극한)")
  void keepsOriginalText() {
    AchievementStandard standard =
        library.standards("2022").stream()
            .filter(s -> s.code().equals("12미적Ⅰ-01-02"))
            .findFirst()
            .orElseThrow();

    assertThat(standard.subject()).isEqualTo("미적분Ⅰ");
    assertThat(standard.area()).isEqualTo("함수의 극한과 연속");
    assertThat(standard.text()).isEqualTo("함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.");
  }

  @Test
  @DisplayName("고르기는 그 교육과정 안에서만 한다")
  void searchInVersion() {
    assertThat(library.search("2022", List.of(), "함수의 극한값", 2)).isNotEmpty();
    assertThat(library.search("2015", List.of(), "함수의 극한값", 2)).isEmpty();
  }

  @Test
  @DisplayName("빈 파일 · 코드나 문장이 빈 줄은 건너뛴다")
  void skipsBrokenLines() {
    CurriculumLibrary.FileShape file =
        new CurriculumLibrary.FileShape(
            "출처",
            "2022",
            List.of(
                new CurriculumLibrary.SubjectShape(
                    "대수",
                    List.of(
                        new CurriculumLibrary.StandardShape("12대수01-01", null, "거듭제곱을 계산한다."),
                        new CurriculumLibrary.StandardShape(null, "", "코드 없음."),
                        new CurriculumLibrary.StandardShape("12대수01-02", "", " ")))));

    List<AchievementStandard> read = CurriculumLibrary.read(file);

    assertThat(read).extracting(AchievementStandard::code).containsExactly("12대수01-01");
    assertThat(read.get(0).area()).isEmpty();
    assertThat(CurriculumLibrary.read(null)).isEmpty();
  }
}
