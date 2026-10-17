package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepResponse;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.review.Review;
import com.ithopeschool.projectbe.domain.review.ReviewRepository;
import com.ithopeschool.projectbe.domain.review.ReviewType;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 와 AI(AiClient)를 가짜로 바꿔서 "DB 에 저장된 회고·점수를 읽어 AI 에게 주는지" 를 확인합니다.
class ExamNextStepServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long EXAM_ID = 9L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final ExamScoreRepository examScoreRepository = mock(ExamScoreRepository.class);
  private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ExamNextStepService service =
      new ExamNextStepService(
          currentUserProvider,
          examRepository,
          examScoreRepository,
          reviewRepository,
          subjectRepository,
          aiClient);

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  private static ExamScore score(long subjectId, int expected, Integer actual) {
    ExamScore score = new ExamScore(USER_ID, EXAM_ID, subjectId, expected);
    if (actual != null) {
      score.changeActualScore(actual);
    }
    return score;
  }

  private void reviewWith(String answer1, String answer2, String answer3) {
    given(reviewRepository.findByUserIdAndExamId(USER_ID, EXAM_ID))
        .willReturn(
            Optional.of(
                new Review(
                    USER_ID,
                    ReviewType.EXAM,
                    EXAM_ID,
                    null,
                    answer1,
                    answer2,
                    answer3,
                    Instant.parse("2026-10-21T00:00:00Z"))));
  }

  private void aiAnswers(String step) {
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(ExamNextStep.Answer.class)))
        .willReturn(Optional.of(new ExamNextStep.Answer(step)));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(examRepository.findByIdAndUserId(EXAM_ID, USER_ID))
        .willReturn(
            Optional.of(
                new Exam(USER_ID, "10월 모의고사", ExamType.MOCK, LocalDate.parse("2026-10-20"))));
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(1, "국어"), subject(3, "수학")));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, EXAM_ID))
        .willReturn(List.of(score(1, 85, 80), score(3, 70, 55)));
    reviewWith("4", "시간 분배를 못 했다", "시간 재고 풀기");
  }

  @Test
  @DisplayName("DB 의 회고·점수로 AI 에게 묻고, 행동과 코드가 고른 과목(예상보다 가장 아쉬운 수학)을 돌려준다")
  void suggests() {
    aiAnswers("수학 기출 1회분 시간 재고 풀기");

    ExamNextStepResponse response = service.suggest(new ExamNextStepRequest("9"));

    assertThat(response)
        .isEqualTo(
            ExamNextStepResponse.of(new ExamNextStepResponse.NextStep("수학 기출 1회분 시간 재고 풀기", "3")));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(ExamNextStep.Answer.class));
    // 이름·학교 같은 개인정보가 들어가지 않는지 보려고, 입력 글 전체가 정확히 이것인지 봅니다.
    assertThat(question.getValue().input())
        .isEqualTo(
            """
            시험: 10월 모의고사 (10/20)
            과목 점수 (예상 → 실제):
            - 국어 85 → 80
            - 수학 70 → 55
            회고:
            ① 준비가 충분했나: 4
            ② 가장 아쉬운 점: 시간 분배를 못 했다
            ③ 다음에 바꿀 한 가지: 시간 재고 풀기
            다음 카드 과목: 수학""");
  }

  @Test
  @DisplayName("실제 점수를 안 적었으면 과목 없이(subjectId: null) 행동만 돌려준다")
  void noFocusSubject() {
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, EXAM_ID))
        .willReturn(List.of(score(1, 85, null)));
    aiAnswers("오답 노트 3문제 다시 풀기");

    assertThat(service.suggest(new ExamNextStepRequest("9")).nextStep())
        .isEqualTo(new ExamNextStepResponse.NextStep("오답 노트 3문제 다시 풀기", null));
  }

  @Test
  @DisplayName("AI 가 못 만들었으면 에러가 아니라 nextStep: null 이다")
  void aiUnavailable() {
    given(aiClient.ask(any(), any(), any(), eq(ExamNextStep.Answer.class)))
        .willReturn(Optional.empty());

    assertThat(service.suggest(new ExamNextStepRequest("9")))
        .isEqualTo(ExamNextStepResponse.of(null));
  }

  @Test
  @DisplayName("내 시험이 아니거나 회고가 없으면 404 이고 AI 를 부르지 않는다")
  void notFound() {
    given(examRepository.findByIdAndUserId(10L, USER_ID)).willReturn(Optional.empty());
    assertThatThrownBy(() -> service.suggest(new ExamNextStepRequest("10")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
        .hasMessage("시험을 찾을 수 없어요");

    given(reviewRepository.findByUserIdAndExamId(USER_ID, EXAM_ID)).willReturn(Optional.empty());
    assertThatThrownBy(() -> service.suggest(new ExamNextStepRequest("9")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
        .hasMessage("이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요");
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("회고 답에 위기 표현이 있으면 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    reviewWith("1", "그냥 다 끝내고 죽고 싶다", "모르겠어");

    assertThat(service.suggest(new ExamNextStepRequest("9")))
        .isEqualTo(ExamNextStepResponse.help());
    verifyNoInteractions(aiClient);
  }
}
