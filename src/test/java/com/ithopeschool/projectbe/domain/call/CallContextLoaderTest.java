package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.exam.ExamType;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// DB 를 가짜(mock)로 두고, 어떤 기록을 골라 꺼내는지만 확인합니다. 문장 모양은 CallContextTest 가 확인합니다.
class CallContextLoaderTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  private final ExamRepository examRepository = mock(ExamRepository.class);
  private final ExamScoreRepository examScoreRepository = mock(ExamScoreRepository.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
  private final GoalRepository goalRepository = mock(GoalRepository.class);

  private final CallContextLoader loader =
      new CallContextLoader(examRepository, examScoreRepository, subjectRepository, goalRepository);

  private static Exam exam(long id, String name, LocalDate date) {
    Exam exam = new Exam(USER_ID, name, ExamType.MOCK, date);
    ReflectionTestUtils.setField(exam, "id", id);
    return exam;
  }

  private static ExamScore score(long examId, long subjectId, int expected, Integer actual) {
    ExamScore score = new ExamScore(USER_ID, examId, subjectId, expected);
    if (actual != null) {
      score.changeActualScore(actual);
    }
    return score;
  }

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  private static Goal goal(String title, LocalDate dDay, String obstacle) {
    return new Goal(
        USER_ID,
        GoalKind.SCORE,
        new Goal.Target(title, dDay, "5등급", null, null),
        new Goal.Woop("좋은 점", "치를 대가", obstacle, "씻기 전에 오답 1개"),
        Instant.parse("2026-09-21T00:00:00Z"));
  }

  @Test
  @DisplayName("기록이 하나도 없으면 비어 있는 값이라 대본에 칸이 생기지 않는다")
  void nothing() {
    CallContext.Facts facts = loader.load(USER_ID, TODAY);

    assertThat(CallContext.section(facts)).isEmpty();
  }

  @Test
  @DisplayName("다음 시험은 오늘 포함 가장 가까운 것, 지난 시험은 가장 최근 것에서 실제 점수를 적은 과목만 꺼낸다")
  void exams() {
    // 날짜가 늦은 것부터 (Repository 이름 그대로)
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(USER_ID))
        .willReturn(
            List.of(
                exam(4, "기말고사", LocalDate.of(2026, 12, 7)),
                exam(3, "11월 학평", LocalDate.of(2026, 11, 20)),
                exam(2, "9월 학평", LocalDate.of(2026, 9, 3)),
                exam(1, "6월 학평", LocalDate.of(2026, 6, 4))));
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(10, "국어"), subject(11, "수학")));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, 2L))
        .willReturn(List.of(score(2, 10, 70, 66), score(2, 11, 55, null)));

    CallContext.Facts facts = loader.load(USER_ID, TODAY);

    assertThat(facts.nextExam()).isEqualTo(new CallContext.NextExam("11월 학평", 33));
    assertThat(facts.pastExam())
        .isEqualTo(
            new CallContext.PastExam("9월 학평", List.of(new CallContext.ScoreLine("국어", 70, 66))));
  }

  @Test
  @DisplayName("지난 시험에 실제 점수가 하나도 없으면 지난 시험을 넣지 않는다")
  void pastExamWithoutActualScores() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(USER_ID))
        .willReturn(List.of(exam(2, "9월 학평", LocalDate.of(2026, 9, 3))));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(USER_ID, 2L))
        .willReturn(List.of(score(2, 10, 70, null)));

    assertThat(loader.load(USER_ID, TODAY).pastExam()).isNull();
  }

  @Test
  @DisplayName("목표는 진행 중이고 D-day 가 지나지 않은 것만, 가까운 순으로 2개까지 꺼낸다")
  void goals() {
    Goal done = goal("끝낸 목표", LocalDate.of(2026, 10, 30), null);
    done.markDone();
    given(goalRepository.findByUserIdOrderById(USER_ID))
        .willReturn(
            List.of(
                goal("기말 국어 2등급", LocalDate.of(2026, 12, 7), null),
                done,
                goal("지난 목표", LocalDate.of(2026, 10, 17), null),
                goal("11월 학평 수학 3등급", LocalDate.of(2026, 11, 20), "학원 다녀오면 피곤해요"),
                goal("영단어장 1회독", LocalDate.of(2026, 11, 7), "  ")));

    List<CallContext.GoalLine> goals = loader.load(USER_ID, TODAY).goals();

    assertThat(goals)
        .containsExactly(
            new CallContext.GoalLine("영단어장 1회독", 20, "지금 5등급", null, "씻기 전에 오답 1개"),
            new CallContext.GoalLine("11월 학평 수학 3등급", 33, "지금 5등급", "학원 다녀오면 피곤해요", "씻기 전에 오답 1개"));
  }

  @Test
  @DisplayName("완주형 목표의 지금 수준은 '전체 양' 으로 적는다")
  void completionGoal() {
    Goal words =
        new Goal(
            USER_ID,
            GoalKind.COMPLETION,
            new Goal.Target("기말 전 영단어장 1회독", LocalDate.of(2026, 11, 7), null, 300, "쪽"),
            new Goal.Woop("좋은 점", "치를 대가", "폰을 자꾸 봐요", "폰을 다른 방에"),
            Instant.parse("2026-09-21T00:00:00Z"));
    given(goalRepository.findByUserIdOrderById(USER_ID)).willReturn(List.of(words));

    assertThat(loader.load(USER_ID, TODAY).goals().get(0).level()).isEqualTo("전체 300쪽");
  }

  @Test
  @DisplayName("모든 조회는 넘겨받은 내 번호로만 한다")
  void onlyMine() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(anyLong())).willReturn(List.of());

    loader.load(USER_ID, TODAY);

    verify(examRepository).findByUserIdOrderByExamDateDescIdDesc(USER_ID);
    verify(goalRepository).findByUserIdOrderById(USER_ID);
  }
}
