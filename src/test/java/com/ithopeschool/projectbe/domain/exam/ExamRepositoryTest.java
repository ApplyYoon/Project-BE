package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// ExamRepository · ExamScoreRepository 가 진짜 DB(H2)에서 "내 것만" 정확히 찾는지,
// 그리고 DB 가 "같은 시험에 같은 과목 두 번" 과 "0~100 밖의 점수" 를 막는지 확인합니다.
// 두 사람(1번, 2번)의 시험을 섞어 넣어 두고, 남의 것이 딸려 나오지 않는지 봅니다.
@DataJpaTest
class ExamRepositoryTest {

  @Autowired private ExamRepository examRepository;
  @Autowired private ExamScoreRepository examScoreRepository;
  @Autowired private TestEntityManager em;

  private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);
  private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);
  private static final Long MATH = 3L;
  private static final Long ENGLISH = 4L;

  private Exam saveExam(Long userId, String name, LocalDate date) {
    return em.persistAndFlush(new Exam(userId, name, ExamType.MOCK, date));
  }

  private ExamScore saveScore(Long userId, Long examId, Long subjectId, int expectedScore) {
    return em.persistAndFlush(new ExamScore(userId, examId, subjectId, expectedScore));
  }

  // ---- 시험 ----

  @Test
  @DisplayName("내 시험은 번호로 찾아지고, 남의 시험 번호나 없는 번호는 빈 Optional")
  void findByIdAndUserId() {
    Exam mine = saveExam(1L, "내 시험", OCT_20);
    Exam others = saveExam(2L, "남의 시험", OCT_20);

    assertThat(examRepository.findByIdAndUserId(mine.getId(), 1L)).contains(mine);
    // 번호는 실제로 있어도, 내 것이 아니면 없는 것과 똑같이 취급됩니다. (실제 점수 입력이 404 가 되는 이유)
    assertThat(examRepository.findByIdAndUserId(others.getId(), 1L)).isEmpty();
    assertThat(examRepository.findByIdAndUserId(99999L, 1L)).isEmpty();
  }

  @Test
  @DisplayName("내 시험만, 시험 날짜가 늦은 것부터 나오고 같은 날이면 나중에 만든 것이 먼저")
  void findByUserIdOrder() {
    saveExam(1L, "10/10 시험", OCT_10);
    saveExam(2L, "남의 시험", OCT_20);
    saveExam(1L, "10/20 시험 먼저 만듦", OCT_20);
    saveExam(1L, "10/20 시험 나중에 만듦", OCT_20);
    em.clear();

    assertThat(examRepository.findByUserIdOrderByExamDateDescIdDesc(1L))
        .extracting(Exam::getName)
        .containsExactly("10/20 시험 나중에 만듦", "10/20 시험 먼저 만듦", "10/10 시험");
    assertThat(examRepository.findByUserIdOrderByExamDateDescIdDesc(2L))
        .extracting(Exam::getName)
        .containsExactly("남의 시험");
  }

  @Test
  @DisplayName("시험이 없는 사람은 빈 목록")
  void noExams() {
    saveExam(1L, "내 시험", OCT_20);

    assertThat(examRepository.findByUserIdOrderByExamDateDescIdDesc(999L)).isEmpty();
  }

  @Test
  @DisplayName("시험의 종류는 이름 그대로(MOCK) 저장되고 그대로 읽힌다")
  void typeIsStoredAsName() {
    Exam saved = em.persistAndFlush(new Exam(1L, "쪽지", ExamType.QUIZ, OCT_20));
    em.clear();

    assertThat(examRepository.findById(saved.getId()).orElseThrow().getType())
        .isEqualTo(ExamType.QUIZ);
  }

  // ---- 과목 점수 ----

  @Test
  @DisplayName("시험 하나의 내 점수만, 만든 순서대로 나온다")
  void findByUserIdAndExamId() {
    Exam first = saveExam(1L, "1번 시험", OCT_20);
    Exam second = saveExam(1L, "2번 시험", OCT_20);
    saveScore(1L, first.getId(), MATH, 85);
    saveScore(1L, second.getId(), MATH, 60);
    saveScore(1L, first.getId(), ENGLISH, 70);
    // 같은 시험 번호에 남의 점수가 끼어 있어도 딸려 나오지 않아야 합니다.
    saveScore(2L, first.getId(), 5L, 99);
    em.clear();

    assertThat(examScoreRepository.findByUserIdAndExamIdOrderById(1L, first.getId()))
        .extracting(ExamScore::getSubjectId, ExamScore::getExpectedScore)
        .containsExactly(tuple(MATH, 85), tuple(ENGLISH, 70));
    assertThat(examScoreRepository.findByUserIdAndExamIdOrderById(2L, second.getId())).isEmpty();
  }

  @Test
  @DisplayName("여러 시험의 내 점수를 한 번에 찾고, 남의 점수는 섞이지 않는다")
  void findByUserIdAndExamIdIn() {
    Exam first = saveExam(1L, "1번 시험", OCT_20);
    Exam second = saveExam(1L, "2번 시험", OCT_20);
    Exam third = saveExam(1L, "3번 시험", OCT_20);
    saveScore(1L, first.getId(), MATH, 85);
    saveScore(1L, second.getId(), MATH, 60);
    saveScore(1L, third.getId(), MATH, 50);
    saveScore(2L, first.getId(), 5L, 99);
    em.clear();

    List<ExamScore> found =
        examScoreRepository.findByUserIdAndExamIdInOrderById(
            1L, List.of(first.getId(), second.getId()));

    assertThat(found)
        .extracting(ExamScore::getExamId, ExamScore::getExpectedScore)
        .containsExactly(tuple(first.getId(), 85), tuple(second.getId(), 60));
  }

  @Test
  @DisplayName("실제 점수는 비어 있다가 나중에 채워지고, 예상 점수는 그대로 남는다")
  void actualScoreIsFilledLater() {
    Exam exam = saveExam(1L, "시험", OCT_20);
    ExamScore score = saveScore(1L, exam.getId(), MATH, 85);
    em.clear();
    assertThat(examScoreRepository.findById(score.getId()).orElseThrow().getActualScore()).isNull();

    ExamScore loaded = examScoreRepository.findById(score.getId()).orElseThrow();
    loaded.changeActualScore(80);
    em.flush();
    em.clear();

    ExamScore reloaded = examScoreRepository.findById(score.getId()).orElseThrow();
    assertThat(reloaded.getActualScore()).isEqualTo(80);
    assertThat(reloaded.getExpectedScore()).isEqualTo(85);
  }

  // ---- DB 가 한 번 더 막는 것 ----

  @Test
  @DisplayName("같은 시험에 같은 과목의 점수를 두 번 저장하면 DB 가 막는다")
  void sameSubjectTwiceInOneExamIsRejected() {
    Exam exam = saveExam(1L, "시험", OCT_20);
    saveScore(1L, exam.getId(), MATH, 85);

    assertThatThrownBy(() -> saveScore(1L, exam.getId(), MATH, 90))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("다른 시험이면 같은 과목을 또 적을 수 있다")
  void sameSubjectInAnotherExamIsAllowed() {
    Exam first = saveExam(1L, "1번 시험", OCT_20);
    Exam second = saveExam(1L, "2번 시험", OCT_20);
    saveScore(1L, first.getId(), MATH, 85);

    assertThat(saveScore(1L, second.getId(), MATH, 90).getId()).isNotNull();
  }

  @Test
  @DisplayName("예상 점수가 0~100 밖이면 DB 가 막는다. 0 과 100 은 된다")
  void expectedScoreRange() {
    Exam exam = saveExam(1L, "시험", OCT_20);

    assertThat(saveScore(1L, exam.getId(), 11L, 0).getId()).isNotNull();
    assertThat(saveScore(1L, exam.getId(), 12L, 100).getId()).isNotNull();
    assertThatThrownBy(() -> saveScore(1L, exam.getId(), 13L, 101))
        .isInstanceOf(PersistenceException.class);
    assertThatThrownBy(() -> saveScore(1L, exam.getId(), 14L, -1))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("실제 점수가 0~100 밖이면 DB 가 막는다. 비어 있는 것은 된다")
  void actualScoreRange() {
    Exam exam = saveExam(1L, "시험", OCT_20);
    ExamScore empty = saveScore(1L, exam.getId(), 11L, 80);
    assertThat(empty.getActualScore()).isNull();

    ExamScore tooHigh = saveScore(1L, exam.getId(), 12L, 80);
    tooHigh.changeActualScore(101);

    assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
  }
}
