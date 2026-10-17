package com.ithopeschool.projectbe.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 회고를 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class ReviewRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate MONDAY = LocalDate.parse("2026-10-12");

  @Autowired private ReviewRepository reviewRepository;

  private Review saveExam(Long userId, Long examId) {
    return reviewRepository.saveAndFlush(
        new Review(userId, ReviewType.EXAM, examId, null, "4", "아쉬운 점", "바꿀 것", NOW));
  }

  private Review saveWeekly(Long userId, LocalDate weekStart) {
    return reviewRepository.saveAndFlush(
        new Review(userId, ReviewType.WEEKLY, null, weekStart, "잘한 것", "", "", NOW));
  }

  @Test
  @DisplayName("시험 후 회고와 주간 회고가 저장되고, 내 것만 시험·주로 찾아진다")
  void findsMineByExamAndWeek() {
    saveExam(1L, 10L);
    saveWeekly(1L, MONDAY);
    saveExam(2L, 20L);

    Review exam = reviewRepository.findByUserIdAndExamId(1L, 10L).orElseThrow();
    assertThat(exam.getType()).isEqualTo(ReviewType.EXAM);
    assertThat(exam.getWeekStart()).isNull();
    assertThat(exam.getAnswer2()).isEqualTo("아쉬운 점");

    Review weekly = reviewRepository.findByUserIdAndWeekStart(1L, MONDAY).orElseThrow();
    assertThat(weekly.getType()).isEqualTo(ReviewType.WEEKLY);
    assertThat(weekly.getExamId()).isNull();
    assertThat(weekly.getAnswer3()).isEmpty();

    // 20번 시험의 회고는 2번 사용자의 것이라 1번으로는 찾을 수 없습니다.
    assertThat(reviewRepository.findByUserIdAndExamId(1L, 20L)).isEmpty();
    assertThat(reviewRepository.findByUserIdAndWeekStart(2L, MONDAY)).isEmpty();
  }

  @Test
  @DisplayName("한 사람이 같은 시험에 회고 두 줄을 만들 수는 없다")
  void oneReviewPerExam() {
    saveExam(1L, 10L);

    assertThatThrownBy(() -> saveExam(1L, 10L)).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("한 사람이 같은 주에 회고 두 줄을 만들 수는 없다 (다른 사람이면 괜찮다)")
  void oneReviewPerWeek() {
    saveWeekly(1L, MONDAY);
    saveWeekly(2L, MONDAY);

    assertThatThrownBy(() -> saveWeekly(1L, MONDAY))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("비어 있는 칸끼리는 겹친다고 보지 않아서, 주간 회고 여러 주와 시험 회고 여러 개가 함께 저장된다")
  void nullColumnsDoNotCollide() {
    saveWeekly(1L, MONDAY);
    saveWeekly(1L, MONDAY.plusWeeks(1));
    saveExam(1L, 10L);
    saveExam(1L, 11L);

    assertThat(reviewRepository.count()).isEqualTo(4);
  }

  @Test
  @DisplayName("답은 200자까지 들어간다")
  void storesTwoHundredChars() {
    String longAnswer = "가".repeat(200);
    reviewRepository.saveAndFlush(
        new Review(1L, ReviewType.EXAM, 10L, null, longAnswer, longAnswer, longAnswer, NOW));

    assertThat(reviewRepository.findByUserIdAndExamId(1L, 10L).orElseThrow().getAnswer1())
        .hasSize(200);
  }
}
