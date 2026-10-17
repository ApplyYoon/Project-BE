package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.QuickCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: DB 와 AI(AiClient)를 가짜로 바꿔서 "DB 에서 무엇을 읽어 AI 에게 주는지" 를 확인합니다.
class QuickCardServiceTest {

  private static final Long USER_ID = 7L;
  // 2026-10-17 낮 12시(한국 시간) → 오늘은 10/17, 최근 2주는 10/4 ~ 10/17
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate FROM = LocalDate.parse("2026-10-04");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-17");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
  private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final QuickCardService service =
      new QuickCardService(
          currentUserProvider, subjectRepository, studyPlanRepository, aiClient, CLOCK);

  private static StudyPlan plan(String date, String content, PlanKind kind) {
    return new StudyPlan(
        USER_ID,
        kind == PlanKind.STUDY ? 3L : null,
        LocalDate.parse(date),
        content,
        60,
        null,
        kind);
  }

  private void aiAnswers(String title, String... items) {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(QuickCard.Answer.class)))
        .willReturn(Optional.of(new QuickCard.Answer(title, List.of(items))));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(
            List.of(
                new Subject(USER_ID, "국어", SubjectColor.RED),
                new Subject(USER_ID, "수학", SubjectColor.RED)));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, FROM, TODAY))
        .willReturn(List.of());
  }

  @Test
  @DisplayName("내 과목과 최근 2주 공부 계획 제목(최근 것부터 · 겹치지 않게 · 노는 계획 빼고)을 AI 에게 주고 초안을 돌려준다")
  void suggestsWithDbContext() {
    List<StudyPlan> plans = new ArrayList<>();
    plans.add(plan("2026-10-10", "국어 비문학", PlanKind.STUDY));
    plans.add(plan("2026-10-12", "수학 문제집", PlanKind.STUDY));
    plans.add(plan("2026-10-14", "게임", PlanKind.PLAY));
    plans.add(plan("2026-10-16", "수학 문제집", PlanKind.STUDY));
    plans.add(plan("2026-10-17", "영어 단어", PlanKind.STUDY));
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, FROM, TODAY))
        .willReturn(plans);
    aiAnswers("수학 문제집", "42쪽 펴기", "5문제 풀기");

    QuickCardResponse response = service.suggest(new QuickCardRequest("수학 하자"));

    assertThat(response)
        .isEqualTo(
            QuickCardResponse.of(
                new QuickCardResponse.Card("수학 문제집", List.of("42쪽 펴기", "5문제 풀기"))));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(eq(USER_ID), eq(GptUsageKind.ASSIST), question.capture(), eq(QuickCard.Answer.class));
    // 이름·학교 같은 개인정보가 들어가지 않는지 보려고, 입력 글 전체가 정확히 이것인지 봅니다.
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            학생이 쓴 한 줄: 수학 하자
            내 과목: 국어, 수학
            최근 카드 제목: 영어 단어 / 수학 문제집 / 국어 비문학""");
  }

  @Test
  @DisplayName("최근 카드 제목은 10개까지만 준다")
  void recentTitlesUpToTen() {
    List<StudyPlan> plans = new ArrayList<>();
    for (int day = 4; day <= 15; day++) {
      plans.add(plan(String.format("2026-10-%02d", day), "카드" + day, PlanKind.STUDY));
    }
    given(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
                USER_ID, FROM, TODAY))
        .willReturn(plans);
    aiAnswers("카드15", "하나", "둘");

    service.suggest(new QuickCardRequest("아무거나"));

    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient).ask(any(), any(), question.capture(), eq(QuickCard.Answer.class));
    assertThat(question.getValue().input())
        .endsWith("최근 카드 제목: 카드15 / 카드14 / 카드13 / 카드12 / 카드11 / 카드10 / 카드9 / 카드8 / 카드7 / 카드6");
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 쓸 수 없는 답이면 card: null 이다")
  void unusableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(QuickCard.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest(new QuickCardRequest("수학 하자")))
        .isEqualTo(QuickCardResponse.of(null));

    aiAnswers("수학", "하나뿐");
    assertThat(service.suggest(new QuickCardRequest("수학 하자")).card()).isNull();
  }

  @Test
  @DisplayName("한 줄에 위기 표현이 있으면 DB 와 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    assertThat(service.suggest(new QuickCardRequest("공부고 뭐고 죽고 싶다")))
        .isEqualTo(QuickCardResponse.help());
    verifyNoInteractions(aiClient, subjectRepository, studyPlanRepository);
  }
}
