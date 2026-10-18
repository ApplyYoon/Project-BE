package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamDday;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.goal.GoalStatus;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// AI 통화 · 상담 채팅 대본의 "[기록에서 꺼낸 것]" 에 넣을 값을 서버 DB 에서 꺼냅니다. (CallContext)
// 계산 규칙은 CallContext 에 있고, 이 파일은 DB 에서 그 사람의 기록을 꺼내 고르기만 합니다.
//
// 누구의 기록인지는 부르는 쪽이 로그인 토큰으로 얻은 사용자 번호(CurrentUserProvider.getCurrentUserId())로 넘깁니다.
// 모든 조회에 그 번호가 들어가서, 다른 사람의 기록이 대본에 섞이지 않습니다.
//
// 꺼내는 기록
//   - 다음 시험: 오늘 포함 가장 가까운 시험 하나
//   - 지난 시험: 어제 이전 시험 중 가장 최근 것 하나. 실제 점수를 적은 과목만 (하나도 없으면 넣지 않음)
//   - 목표: 진행 중이고 D-day 가 지나지 않은 것, 가까운 순으로 최대 2개
//   - 심리검사: 가장 최근 검사의 척도별 점수
@Component
@RequiredArgsConstructor
public class CallContextLoader {

  // 대본에 넣는 목표의 최대 개수. 많으면 AI 가 늘어놓기 쉬워서 줄입니다.
  static final int MAX_GOALS = 2;

  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  private final SubjectRepository subjectRepository;
  private final GoalRepository goalRepository;
  private final PsychTestRepository psychTestRepository;

  // 그 사람의 기록. today: 오늘 (새벽 4시 기준, DayRule)
  public CallContext.Facts load(Long userId, LocalDate today) {
    List<Exam> exams = examRepository.findByUserIdOrderByExamDateDescIdDesc(userId);
    return new CallContext.Facts(
        nextExamOf(exams, today),
        pastExamOf(userId, exams, today),
        goalsOf(userId, today),
        psychScoresOf(userId));
  }

  // 오늘 포함 가장 가까운 시험. 없으면 null
  private CallContext.NextExam nextExamOf(List<Exam> exams, LocalDate today) {
    Exam next = null;
    for (Exam exam : exams) {
      boolean upcoming = ExamDday.daysUntil(exam.getExamDate(), today) >= 0;
      if (upcoming && (next == null || exam.getExamDate().isBefore(next.getExamDate()))) {
        next = exam;
      }
    }
    if (next == null) {
      return null;
    }
    return new CallContext.NextExam(next.getName(), ExamDday.daysUntil(next.getExamDate(), today));
  }

  // 가장 최근 지난 시험과, 그 시험에서 실제 점수를 적은 과목들. 지난 시험이 없거나 실제 점수가 없으면 null
  private CallContext.PastExam pastExamOf(Long userId, List<Exam> exams, LocalDate today) {
    // exams 는 날짜가 늦은 것부터라서, 처음 만나는 지난 시험이 가장 최근 것입니다.
    Exam last = null;
    for (Exam exam : exams) {
      if (ExamDday.daysUntil(exam.getExamDate(), today) < 0) {
        last = exam;
        break;
      }
    }
    if (last == null) {
      return null;
    }
    Map<Long, String> subjectNames = new HashMap<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      subjectNames.put(subject.getId(), subject.getName());
    }
    List<CallContext.ScoreLine> scores = new ArrayList<>();
    for (ExamScore score :
        examScoreRepository.findByUserIdAndExamIdOrderById(userId, last.getId())) {
      String subject = subjectNames.get(score.getSubjectId());
      if (score.getActualScore() == null || subject == null) {
        continue;
      }
      scores.add(
          new CallContext.ScoreLine(subject, score.getExpectedScore(), score.getActualScore()));
    }
    return scores.isEmpty() ? null : new CallContext.PastExam(last.getName(), scores);
  }

  // 진행 중이고 D-day 가 지나지 않은 목표. 가까운 순으로 최대 2개
  private List<CallContext.GoalLine> goalsOf(Long userId, LocalDate today) {
    List<Goal> active = new ArrayList<>();
    for (Goal goal : goalRepository.findByUserIdOrderById(userId)) {
      if (goal.getStatus() == GoalStatus.ACTIVE && ExamDday.daysUntil(goal.getDDay(), today) >= 0) {
        active.add(goal);
      }
    }
    active.sort(Comparator.comparing(Goal::getDDay));
    List<CallContext.GoalLine> lines = new ArrayList<>();
    for (Goal goal : active.subList(0, Math.min(MAX_GOALS, active.size()))) {
      lines.add(
          new CallContext.GoalLine(
              goal.getTitle(),
              ExamDday.daysUntil(goal.getDDay(), today),
              levelOf(goal),
              blankToNull(goal.getObstacle()),
              blankToNull(goal.getPlan())));
    }
    return lines;
  }

  // 점수형이면 지금 점수("지금 5등급"), 완주형이면 전체 양("전체 300쪽"). 없으면 null
  private static String levelOf(Goal goal) {
    if (goal.getKind() == GoalKind.COMPLETION && goal.getTotalAmount() != null) {
      String unit = goal.getAmountUnit() == null ? "" : goal.getAmountUnit();
      return "전체 " + goal.getTotalAmount() + unit;
    }
    String currentScore = blankToNull(goal.getCurrentScore());
    return currentScore == null ? null : "지금 " + currentScore;
  }

  // 가장 최근 심리검사의 척도별 점수. 검사를 안 했으면 빈 맵
  private Map<PsychScale, Double> psychScoresOf(Long userId) {
    return psychTestRepository
        .findFirstByUserIdOrderByIdDesc(userId)
        .map(test -> PsychScore.scoresOf(test.getAnswers()))
        .orElse(Map.of());
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text;
  }
}
