package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.safety.ExamPeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 한 학생이 오늘 시험 기간인지를 진짜 DB(H2)에 등록된 시험(#16)으로 판단하는지 확인합니다. (이슈 #58)
@DataJpaTest
class ExamPeriodCheckerTest {

  @Autowired private ExamRepository examRepository;
  @Autowired private TestEntityManager em;

  private ExamPeriodChecker checker;

  @BeforeEach
  void setUp() {
    checker = new ExamPeriodChecker(examRepository);
  }

  private void saveExam(Long userId, LocalDate date) {
    em.persistAndFlush(new Exam(userId, "시험", ExamType.SCHOOL, date));
  }

  @Test
  @DisplayName("내 시험의 직전 3일 ~ 직후 2일이면 시험 기간이고, 이유 문장을 돌려준다")
  void myExamPeriod() {
    saveExam(1L, LocalDate.of(2026, 10, 20));

    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 16))).isFalse();
    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 17))).isTrue();
    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 22))).isTrue();
    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 23))).isFalse();
    assertThat(checker.reasonFor(1L, LocalDate.of(2026, 10, 20))).isEqualTo(ExamPeriod.REASON);
    assertThat(checker.reasonFor(1L, LocalDate.of(2026, 10, 23))).isNull();
  }

  @Test
  @DisplayName("남의 시험은 내 시험 기간에 들어가지 않는다")
  void othersExamsDoNotCount() {
    saveExam(2L, LocalDate.of(2026, 10, 20));

    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 20))).isFalse();
    assertThat(checker.isExamPeriod(2L, LocalDate.of(2026, 10, 20))).isTrue();
  }

  @Test
  @DisplayName("시험이 여러 개면 하나만 걸려도 시험 기간이고, 시험이 없으면 아니다")
  void severalExamsAndNone() {
    saveExam(1L, LocalDate.of(2026, 9, 1));
    saveExam(1L, LocalDate.of(2026, 10, 20));

    assertThat(checker.isExamPeriod(1L, LocalDate.of(2026, 10, 19))).isTrue();
    assertThat(checker.isExamPeriod(3L, LocalDate.of(2026, 10, 19))).isFalse();
  }
}
