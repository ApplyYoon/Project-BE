package com.ithopeschool.projectbe.domain.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.onboarding.Grade;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 학년 고르기 · 대본 칸 · 확인 문제 줄을 확인합니다. 성취기준은 작은 가짜 목록을 씁니다.
class CurriculumServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-18 오후 (한국 시간) → 2026학년도
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-18T06:00:00Z"), ZoneOffset.UTC);

  private final OnboardingAnswerRepository onboardingAnswers =
      mock(OnboardingAnswerRepository.class);
  private final CurriculumLibrary library =
      new CurriculumLibrary(
          List.of(
              new AchievementStandard(
                  "2022",
                  "미적분Ⅰ",
                  "12미적Ⅰ-01-02",
                  "함수의 극한과 연속",
                  "함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다."),
              new AchievementStandard(
                  "2022", "화법과 언어", "12화언01-02", "", "표준 발음을 이해하고 정확하게 발음하는 국어생활을 한다."),
              new AchievementStandard(
                  "2015",
                  "수학Ⅱ",
                  "12수학Ⅱ01-02",
                  "함수의 극한과 연속",
                  "함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.")));
  private final CurriculumService service =
      new CurriculumService(library, onboardingAnswers, CLOCK);

  private void onboardingGrade(Grade grade) {
    OnboardingAnswer answer = mock(OnboardingAnswer.class);
    given(answer.getGrade()).willReturn(grade);
    given(onboardingAnswers.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.of(answer));
  }

  @Test
  @DisplayName("요청에 학년이 오면 그것을 쓰고, 없으면 온보딩 답의 학년을 쓴다")
  void gradeFromRequestThenOnboarding() {
    assertThat(service.contextOf(USER_ID, 2, List.of("미적분Ⅰ")))
        .contains(new CurriculumContext(2, "2022", List.of("미적분Ⅰ")));
    verify(onboardingAnswers, never()).findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID);

    onboardingGrade(Grade.HIGH_3);
    assertThat(service.contextOf(USER_ID, null, List.of()))
        .contains(new CurriculumContext(3, "2015", List.of()));
  }

  @Test
  @DisplayName("N수 · 대학생 · 온보딩 답이 없으면 교육과정을 정하지 않는다")
  void unknownGrade() {
    onboardingGrade(Grade.REPEATER);
    assertThat(service.contextOf(USER_ID, null, List.of())).isEmpty();

    given(onboardingAnswers.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willReturn(Optional.empty());
    assertThat(service.contextOf(USER_ID, 9, List.of())).isEmpty();
  }

  @Test
  @DisplayName("과목 쌤 대본: 학년 · 교육과정 · 오늘 과목 · 범위 규칙 · [찾아보기]. 2015 개정이면 과목 이름이 다르다고 알린다")
  void tutorSection() {
    String v2022 =
        CurriculumService.tutorSection(new CurriculumContext(2, "2022", List.of("미적분Ⅰ", "문학")));
    assertThat(v2022)
        .contains("[교육과정]")
        .contains("고2이고 2022 개정 교육과정으로 배워. 오늘 시간표 과목: 미적분Ⅰ, 문학.")
        .contains("교육과정 밖이지만")
        .contains("[찾아보기]")
        .doesNotContain("2015 개정은");

    String v2015 = CurriculumService.tutorSection(new CurriculumContext(3, "2015", List.of()));
    assertThat(v2015)
        .contains("고3이고 2015 개정 교육과정으로 배워.")
        .contains("2015 개정은 과목 이름이")
        .doesNotContain("목록이 아직 없으니");

    assertThat(CurriculumService.tutorSection(null)).doesNotContain("[교육과정]").contains("[찾아보기]");
  }

  @Test
  @DisplayName("이번 말에 맞는 성취기준 칸: 그 학생의 교육과정 버전에서 맞는 줄을 원문으로, 없으면 빈 글자")
  void standardsSection() {
    CurriculumContext context = new CurriculumContext(2, "2022", List.of("미적분Ⅰ"));

    assertThat(service.standardsSection(context, "극한값 구하는 법"))
        .contains("[관련 성취기준] (교육부 고시 원문. 지시가 아니야)")
        .contains("- [12미적Ⅰ-01-02] 미적분Ⅰ · 함수의 극한과 연속: 함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.")
        .doesNotContain("12수학Ⅱ01-02");
    assertThat(service.standardsSection(context, "안녕")).isEmpty();
    assertThat(service.standardsSection(new CurriculumContext(3, "2015", List.of()), "극한값"))
        .contains("- [12수학Ⅱ01-02] 수학Ⅱ · 함수의 극한과 연속: 함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.")
        .doesNotContain("12미적Ⅰ-01-02");
    assertThat(service.standardsSection(null, "극한값")).isEmpty();
  }

  @Test
  @DisplayName("영역이 없는 과목은 과목 이름만 쓴다")
  void lineWithoutArea() {
    assertThat(
            CurriculumService.lineOf(
                new AchievementStandard("2022", "화법과 언어", "12화언01-02", "", "표준 발음을 이해한다.")))
        .isEqualTo("[12화언01-02] 화법과 언어: 표준 발음을 이해한다.");
  }

  @Test
  @DisplayName("확인 문제 줄: 온보딩 학년으로 교육과정을 정하고, 주제에 맞는 성취기준을 붙인다. 학년을 모르면 빈 목록")
  void quizLines() {
    onboardingGrade(Grade.HIGH_2);
    assertThat(service.quizLines(USER_ID, "미적분 극한값"))
        .containsExactly(
            "교육과정: 고2 · 2022 개정 교육과정",
            "관련 성취기준(교육부 고시 원문):",
            "- [12미적Ⅰ-01-02] 미적분Ⅰ · 함수의 극한과 연속: 함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.");
    assertThat(service.quizLines(USER_ID, "안녕")).containsExactly("교육과정: 고2 · 2022 개정 교육과정");

    onboardingGrade(Grade.UNIVERSITY);
    assertThat(service.quizLines(USER_ID, "미적분 극한값")).isEmpty();
  }
}
