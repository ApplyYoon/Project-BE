package com.ithopeschool.projectbe.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.review.dto.ReviewResponse;
import com.ithopeschool.projectbe.domain.review.dto.ReviewSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 저장·덮어쓰기·검사 로직만 확인합니다.
class ReviewServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long EXAM_ID = 30L;
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  // 2026-10-12 는 월요일입니다.
  private static final LocalDate MONDAY = LocalDate.parse("2026-10-12");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final ReviewService service =
      new ReviewService(
          currentUserProvider, reviewRepository, examRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(reviewRepository.save(any()))
        .willAnswer(
            invocation -> {
              Review review = invocation.getArgument(0);
              ReflectionTestUtils.setField(review, "id", 3L);
              return review;
            });
    // 30번 시험은 내 시험입니다. 다른 번호는 없는 시험(또는 남의 시험)입니다.
    given(examRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(examRepository.findByIdAndUserId(EXAM_ID, USER_ID))
        .willReturn(
            Optional.of(
                new Exam(USER_ID, "10월 모의고사", ExamType.MOCK, LocalDate.parse("2026-10-15"))));
  }

  private ReviewSaveRequest examRequest(String examId, String... answers) {
    return new ReviewSaveRequest("exam", examId, null, List.of(answers));
  }

  private ReviewSaveRequest weeklyRequest(LocalDate weekStart, String... answers) {
    return new ReviewSaveRequest("weekly", null, weekStart, List.of(answers));
  }

  private void expectError(ReviewSaveRequest request, ErrorCode code, String message) {
    assertThatThrownBy(() -> service.save(request))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(code);
    verify(reviewRepository, never()).save(any());
  }

  @Test
  @DisplayName("시험 후 회고를 처음 쓰면 새 줄을 만들고, 시험 번호와 답 3개를 돌려준다")
  void createsExamReview() {
    given(reviewRepository.findByUserIdAndExamId(USER_ID, EXAM_ID)).willReturn(Optional.empty());

    ReviewResponse response = service.save(examRequest("30", "4", "시간 분배", "시간 재고 풀기"));

    assertThat(response.id()).isEqualTo("3");
    assertThat(response.type()).isEqualTo("exam");
    assertThat(response.examId()).isEqualTo("30");
    assertThat(response.weekStart()).isNull();
    assertThat(response.answers()).containsExactly("4", "시간 분배", "시간 재고 풀기");
    assertThat(response.savedAt()).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  @DisplayName("같은 시험에 다시 쓰면 새 줄을 만들지 않고 덮어쓴다")
  void overwritesSameExam() {
    Review existing =
        new Review(
            USER_ID,
            ReviewType.EXAM,
            EXAM_ID,
            null,
            "2",
            "옛 답",
            "옛 답",
            Instant.parse("2026-10-16T00:00:00Z"));
    ReflectionTestUtils.setField(existing, "id", 3L);
    given(reviewRepository.findByUserIdAndExamId(USER_ID, EXAM_ID))
        .willReturn(Optional.of(existing));

    ReviewResponse response = service.save(examRequest("30", "5", "새 답", "새 계획"));

    verify(reviewRepository, never()).save(any());
    assertThat(response.id()).isEqualTo("3");
    assertThat(response.answers()).containsExactly("5", "새 답", "새 계획");
    assertThat(existing.getAnswer2()).isEqualTo("새 답");
    assertThat(existing.getSavedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("주간 회고는 건너뛴 질문(빈 글자)이 있어도 저장되고, 같은 주에 다시 쓰면 덮어쓴다")
  void savesAndOverwritesWeekly() {
    given(reviewRepository.findByUserIdAndWeekStart(USER_ID, MONDAY)).willReturn(Optional.empty());

    ReviewResponse created = service.save(weeklyRequest(MONDAY, "매일 계획 지킴", "", ""));

    assertThat(created.type()).isEqualTo("weekly");
    assertThat(created.examId()).isNull();
    assertThat(created.weekStart()).isEqualTo(MONDAY);
    assertThat(created.answers()).containsExactly("매일 계획 지킴", "", "");

    Review existing = new Review(USER_ID, ReviewType.WEEKLY, null, MONDAY, "매일 계획 지킴", "", "", NOW);
    ReflectionTestUtils.setField(existing, "id", 4L);
    given(reviewRepository.findByUserIdAndWeekStart(USER_ID, MONDAY))
        .willReturn(Optional.of(existing));

    ReviewResponse overwritten = service.save(weeklyRequest(MONDAY, "", "주말 늦잠", "토요일 9시 시작"));

    assertThat(overwritten.id()).isEqualTo("4");
    assertThat(overwritten.answers()).containsExactly("", "주말 늦잠", "토요일 9시 시작");
  }

  @Test
  @DisplayName("없는 시험이나 남의 시험에 회고를 쓰면 404 이다")
  void rejectsOthersExam() {
    expectError(examRequest("99", "a", "b", "c"), ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요");
  }

  @Test
  @DisplayName("시험 후 회고: 시험 번호가 없거나, weekStart 를 같이 보내거나, 빈 답이 있으면 400 이다")
  void rejectsBadExamReview() {
    expectError(
        examRequest(null, "a", "b", "c"),
        ErrorCode.VALIDATION_ERROR,
        "어느 시험의 회고인지 examId 를 보내 주세요");
    expectError(
        new ReviewSaveRequest("exam", "30", MONDAY, List.of("a", "b", "c")),
        ErrorCode.VALIDATION_ERROR,
        "시험 후 회고에는 weekStart 를 보내지 않아요");
    expectError(
        examRequest("30", "a", "  ", "c"), ErrorCode.VALIDATION_ERROR, "시험 후 회고는 세 질문에 모두 답해 주세요");
  }

  @Test
  @DisplayName("주간 회고: 주 날짜가 없거나, 월요일이 아니거나, examId 를 같이 보내거나, 답이 모두 비면 400 이다")
  void rejectsBadWeeklyReview() {
    expectError(
        weeklyRequest(null, "a", "", ""),
        ErrorCode.VALIDATION_ERROR,
        "어느 주의 회고인지 weekStart 를 보내 주세요");
    expectError(
        weeklyRequest(LocalDate.parse("2026-10-14"), "a", "", ""),
        ErrorCode.VALIDATION_ERROR,
        "weekStart 는 그 주의 월요일 날짜로 보내 주세요");
    expectError(
        new ReviewSaveRequest("weekly", "30", MONDAY, List.of("a", "", "")),
        ErrorCode.VALIDATION_ERROR,
        "주간 회고에는 examId 를 보내지 않아요");
    expectError(
        weeklyRequest(MONDAY, "", " ", ""), ErrorCode.VALIDATION_ERROR, "적어도 한 질문에는 답해 주세요");
  }

  @Test
  @DisplayName("종류가 exam · weekly 가 아니면 400 이다")
  void rejectsUnknownType() {
    expectError(
        new ReviewSaveRequest("daily", null, null, List.of("a", "b", "c")),
        ErrorCode.VALIDATION_ERROR,
        "회고 종류는 exam, weekly 중에서 골라 주세요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이 그대로 나가고 아무것도 저장하지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    expectError(examRequest("30", "a", "b", "c"), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
  }
}
