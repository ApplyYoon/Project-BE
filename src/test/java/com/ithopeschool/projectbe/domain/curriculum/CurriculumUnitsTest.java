package com.ithopeschool.projectbe.domain.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 단원 목록(단원 = 교육과정 영역)을 만들고 AI 가 고른 글자를 확인하는 규칙입니다. (2026-10-26 BE #401)
class CurriculumUnitsTest {

  private static AchievementStandard standard(String subject, String area) {
    return new AchievementStandard("2022", subject, "code", area, "성취기준 문장");
  }

  @Test
  @DisplayName("과목마다 영역을 한 번씩 모으고, 영역이 빈 과목은 과목 이름을 단원으로 쓴다")
  void groupsAreasBySubject() {
    CurriculumUnits units =
        CurriculumUnits.of(
            List.of(
                standard("공통수학1", "다항식"),
                standard("공통수학1", "다항식"),
                standard("공통수학1", "행렬"),
                standard("문학", ""),
                standard("문학", " ")));

    assertThat(units.lines()).containsExactly("- 공통수학1: 다항식 · 행렬", "- 문학: 문학");
    assertThat(units.names()).containsExactlyInAnyOrder("다항식", "행렬", "문학");
  }

  @Test
  @DisplayName("고른 글자는 앞뒤 공백을 지워 목록에 있을 때만 돌려주고, 아니면 null")
  void pick() {
    CurriculumUnits units = CurriculumUnits.of(List.of(standard("공통수학1", "다항식")));

    assertThat(units.pick(" 다항식 ")).isEqualTo("다항식");
    assertThat(units.pick("다항식의 연산")).isNull();
    assertThat(units.pick(null)).isNull();
    assertThat(CurriculumUnits.EMPTY.pick("다항식")).isNull();
  }

  @Test
  @DisplayName("실제 교육과정 파일의 단원 이름은 모두 DB 칸 길이(30자) 안에 들어간다")
  void realFilesFitColumn() {
    CurriculumLibrary library = new CurriculumLibrary();
    CurriculumUnits all =
        CurriculumUnits.of(
            java.util.stream.Stream.concat(
                    library.standards(CurriculumVersion.V2022).stream(),
                    library.standards(CurriculumVersion.V2015).stream())
                .toList());

    assertThat(all.names()).isNotEmpty();
    assertThat(all.names()).allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(30));
    assertThat(all.names()).contains("다항식", "문학");
  }
}
