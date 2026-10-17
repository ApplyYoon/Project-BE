package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.exam.*;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.*;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScoreStudyServiceTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 15);

  @Test
  void skipsMissingScoresAndSingleExamSubjects() {
    assertThat(
            ScoreStudyService.calculate(
                List.of(exam(1L, DATE)),
                List.of(score(1L, 3L, 50), new ExamScore(1L, 1L, 4L, 70)),
                List.of()))
        .isEmpty();
  }

  @Test
  void includesFourteenDaysSubtractsPausesAndExcludesOtherSubjectsAndPlay() {
    var first = exam(1L, DATE.minusDays(30));
    var second = exam(2L, DATE);
    var start = DayRule.startOf(DATE.minusDays(14));
    var sessions =
        List.of(
            new StudySession(
                1L,
                3L,
                start,
                start.plusSeconds(7200),
                1,
                3600000,
                null,
                null,
                null,
                PlanKind.STUDY),
            new StudySession(1L, 4L, start, start.plusSeconds(3600), 0),
            new StudySession(
                1L, 3L, start, start.plusSeconds(3600), 0, 0, null, null, null, PlanKind.PLAY),
            new StudySession(
                1L, 3L, DayRule.startOf(DATE), DayRule.startOf(DATE).plusSeconds(3600), 0),
            new StudySession(1L, 3L, start.minusSeconds(1), start, 0));
    var result =
        ScoreStudyService.calculate(
                List.of(first, second), List.of(score(1L, 3L, 60), score(2L, 3L, 50)), sessions)
            .getFirst();
    assertThat(result.sampleSize()).isEqualTo(2);
    assertThat(result.hypothesis()).isTrue();
    assertThat(result.exams().getFirst().pointsPerStudyHour()).isNull();
    var latest = result.exams().getLast();
    assertThat(latest.days()).hasSize(14);
    assertThat(latest.days().getFirst().studyMinutes()).isEqualTo(60);
    assertThat(latest.days().getLast().date()).isEqualTo(DATE.minusDays(1));
    assertThat(latest.studyMinutes()).isEqualTo(60);
    assertThat(latest.scoreChange()).isEqualTo(-10);
    assertThat(latest.pointsPerStudyHour()).isEqualTo(-10.0);
  }

  @Test
  void queriesOnlyCurrentUserAndPastExamsInChronologicalOrder() {
    var users = mock(CurrentUserProvider.class);
    var exams = mock(ExamRepository.class);
    var scores = mock(ExamScoreRepository.class);
    var sessions = mock(StudySessionRepository.class);
    when(users.getCurrentUserId()).thenReturn(7L);
    when(exams.findByUserIdOrderByExamDateDescIdDesc(7L))
        .thenReturn(
            List.of(exam(3L, DATE.plusDays(1)), exam(2L, DATE), exam(1L, DATE.minusDays(30))));
    when(scores.findByUserIdAndExamIdInOrderById(7L, List.of(1L, 2L)))
        .thenReturn(List.of(score(2L, 3L, 80), score(1L, 3L, 70)));
    var service =
        new ScoreStudyService(
            users, exams, scores, sessions, Clock.fixed(DayRule.startOf(DATE), ZoneOffset.UTC));
    var result = service.getAnalysis().getFirst();
    assertThat(result.exams().getLast().scoreChange()).isEqualTo(10);
    assertThat(result.exams().getLast().pointsPerStudyHour()).isNull();
    verify(sessions)
        .findStartedBetween(7L, DayRule.startOf(DATE.minusDays(44)), DayRule.startOf(DATE));
  }

  private static Exam exam(Long id, LocalDate date) {
    var exam = new Exam(1L, "시험", ExamType.MOCK, date);
    ReflectionTestUtils.setField(exam, "id", id);
    return exam;
  }

  private static ExamScore score(Long examId, Long subjectId, int actual) {
    var score = new ExamScore(1L, examId, subjectId, 70);
    score.changeActualScore(actual);
    return score;
  }
}
