package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 질문 만들기와 답 검사만 봅니다. (GPT 는 부르지 않습니다)
class DailyMissionsTest {

  private static final List<String> SUBJECTS = List.of("미적분Ⅰ", "문학");

  @Test
  @DisplayName("입력에는 준비하는 것 · 오늘 과목 · 가까운 시험 · 컨디션이 들어가고, 모르는 것은 (모름)")
  void input() {
    DailyMissionsRequest request =
        new DailyMissionsRequest(
            LocalDate.of(2026, 10, 19),
            new DailyMissionsRequest.Focus("CONTEST", "정보올림피아드"),
            SUBJECTS,
            new DailyMissionsRequest.Exam("2학기 2차 정기시험", 14),
            2);

    String input = DailyMissions.input(request);

    assertThat(input).contains("준비하는 것: 대회 · 자격증 — 정보올림피아드");
    assertThat(input).contains("오늘 배운 과목: 미적분Ⅰ, 문학");
    assertThat(input).contains("가까운 시험: 2학기 2차 정기시험 (14일 남음)");
    assertThat(input).contains("오늘 컨디션: 2");

    String unknown =
        DailyMissions.input(
            new DailyMissionsRequest(LocalDate.of(2026, 10, 19), null, null, null, null));
    assertThat(unknown).contains("준비하는 것: (모름)").contains("오늘 배운 과목: (모름)");
  }

  @Test
  @DisplayName("답 검사: 빈 · 긴 · 겹친 제목과 60분 넘는 것은 버리고, 30분보다 짧으면 30분으로, 없는 과목은 null, 3개까지")
  void from() {
    DailyMissions.Answer answer =
        new DailyMissions.Answer(
            List.of(
                new DailyMissions.Mission("수능 기출 미적분 3문제", 15, "미적분Ⅰ"),
                new DailyMissions.Mission("수능 기출 미적분 3문제", 15, "미적분Ⅰ"),
                new DailyMissions.Mission(" ", 15, null),
                new DailyMissions.Mission("가".repeat(31), 15, null),
                new DailyMissions.Mission("문학 작품 다시 읽기", 90, "문학"),
                new DailyMissions.Mission("물리 개념 정리", 20, "물리학Ⅰ"),
                new DailyMissions.Mission("영단어 30개", 10, null),
                new DailyMissions.Mission("네 번째", 10, null)));

    List<DailyMissions.Mission> missions = DailyMissions.from(answer, SUBJECTS);

    assertThat(missions)
        .containsExactly(
            new DailyMissions.Mission("수능 기출 미적분 3문제", 30, "미적분Ⅰ"),
            new DailyMissions.Mission("물리 개념 정리", 30, null),
            new DailyMissions.Mission("영단어 30개", 30, null));
  }

  @Test
  @DisplayName("쓸 미션이 하나도 없으면 null (FE 는 규칙 미션을 씀)")
  void fromNothingUsable() {
    assertThat(DailyMissions.from(null, SUBJECTS)).isNull();
    assertThat(
            DailyMissions.from(
                new DailyMissions.Answer(List.of(new DailyMissions.Mission("길이 없음", 0, null))),
                SUBJECTS))
        .isNull();
  }
}
