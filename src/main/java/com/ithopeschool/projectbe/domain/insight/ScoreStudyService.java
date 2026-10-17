package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScoreStudyService {
  private final CurrentUserProvider currentUserProvider;
  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  private final StudySessionRepository studySessionRepository;
  private final Clock clock;

  public record Day(LocalDate date, int studyMinutes) {}

  public record Result(
      Long examId,
      LocalDate examDate,
      int score,
      Integer scoreChange,
      int studyMinutes,
      Double pointsPerStudyHour,
      List<Day> days) {}

  public record Subject(Long subjectId, int sampleSize, boolean hypothesis, List<Result> exams) {}

  @Transactional(readOnly = true)
  public List<Subject> getAnalysis() {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    List<Exam> exams =
        examRepository.findByUserIdOrderByExamDateDescIdDesc(userId).stream()
            .filter(exam -> !exam.getExamDate().isAfter(today))
            .sorted(Comparator.comparing(Exam::getExamDate).thenComparing(Exam::getId))
            .toList();
    if (exams.isEmpty()) return List.of();
    List<ExamScore> scores =
        examScoreRepository.findByUserIdAndExamIdInOrderById(
            userId, exams.stream().map(Exam::getId).toList());
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId,
            DayRule.startOf(exams.getFirst().getExamDate().minusDays(14)),
            DayRule.startOf(exams.getLast().getExamDate()));
    return calculate(exams, scores, sessions);
  }

  // 시험 14일 전부터 전날까지 셉니다. 시험 당일 기록은 결과를 본 뒤의 공부와 섞일 수 있어 뺍니다.
  static List<Subject> calculate(
      List<Exam> exams, List<ExamScore> scores, List<StudySession> sessions) {
    var bySubject = new TreeMap<Long, List<Result>>();
    for (Exam exam : exams) {
      for (ExamScore score : scores) {
        if (!score.getExamId().equals(exam.getId()) || score.getActualScore() == null) continue;
        var results = bySubject.computeIfAbsent(score.getSubjectId(), ignored -> new ArrayList<>());
        var days = new ArrayList<Day>();
        long totalMillis = 0;
        for (int offset = 14; offset >= 1; offset--) {
          LocalDate date = exam.getExamDate().minusDays(offset);
          long millis =
              sessions.stream()
                  .filter(
                      s ->
                          s.getKind() == PlanKind.STUDY
                              && score.getSubjectId().equals(s.getSubjectId()))
                  .filter(s -> DayRule.dateOf(s.getStartedAt()).equals(date))
                  .mapToLong(
                      s -> StudyTime.studyMillis(s.getStartedAt(), s.getEndedAt(), s.getPausedMs()))
                  .sum();
          totalMillis += millis;
          days.add(new Day(date, StudyTime.toMinutes(millis)));
        }
        Integer change =
            results.isEmpty() ? null : score.getActualScore() - results.getLast().score();
        // 관련성을 보여 주는 참고값이며 공부가 점수 변화를 일으켰다는 뜻은 아닙니다.
        Double rate = change == null || totalMillis == 0 ? null : change * 3600000.0 / totalMillis;
        results.add(
            new Result(
                exam.getId(),
                exam.getExamDate(),
                score.getActualScore(),
                change,
                StudyTime.toMinutes(totalMillis),
                rate,
                List.copyOf(days)));
      }
    }
    return bySubject.entrySet().stream()
        .filter(entry -> entry.getValue().size() >= 2)
        // 시험 5회 미만은 작은 표본으로 표시합니다. 인과관계나 통계적 유의성을 판정하지 않습니다.
        .map(
            entry ->
                new Subject(
                    entry.getKey(),
                    entry.getValue().size(),
                    entry.getValue().size() < 5,
                    List.copyOf(entry.getValue())))
        .toList();
  }
}
