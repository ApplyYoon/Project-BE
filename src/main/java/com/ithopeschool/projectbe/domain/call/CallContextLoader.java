package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamDday;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.goal.GoalStatus;
import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.session.SessionTags.ShortfallReason;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
//   - 아침형/저녁형: 가장 최근 설문의 답과, 최근 2주(어제까지) 판정이 끝난 카드 중 아침 · 저녁 카드를 체크리스트까지 다 채운 횟수
//     (CardResultService.getJudgedDays 의 checklistRate)
//   - 못 채운 이유: 최근 2주 미실행 카드에서 고른 원인 + 타이머를 목표 전에 끝내며 고른 이유 ('기타' 는 뺌)
@Component
@RequiredArgsConstructor
public class CallContextLoader {

  // 대본에 넣는 목표의 최대 개수. 많으면 AI 가 늘어놓기 쉬워서 줄입니다.
  static final int MAX_GOALS = 2;
  // 아침형/저녁형 · 못 채운 이유를 볼 기간(일). 어제까지 14일입니다. (오늘 카드는 아직 끝나지 않았을 수 있음)
  static final int RECENT_DAYS = 14;
  // 아침 카드: 이 시각부터 정오 전까지 시작 / 저녁 카드: 18시부터 다음 날 이 시각 전까지 시작 (하루의 기준 새벽 4시)
  static final LocalTime DAY_START = LocalTime.of(4, 0);
  static final LocalTime NOON = LocalTime.NOON;
  static final LocalTime EVENING = LocalTime.of(18, 0);

  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  private final SubjectRepository subjectRepository;
  private final GoalRepository goalRepository;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final CardRepository cardRepository;
  private final CardResultService cardResultService;
  private final StudySessionRepository studySessionRepository;

  // 그 사람의 기록. today: 오늘 (새벽 4시 기준, DayRule)
  public CallContext.Facts load(Long userId, LocalDate today) {
    List<Exam> exams = examRepository.findByUserIdOrderByExamDateDescIdDesc(userId);
    LocalDate from = today.minusDays(RECENT_DAYS);
    LocalDate to = today.minusDays(1);
    List<JudgedCardDay> judged = cardResultService.getJudgedDays(userId, from, to);
    return new CallContext.Facts(
        nextExamOf(exams, today),
        pastExamOf(userId, exams, today),
        goalsOf(userId, today),
        rhythmGapOf(userId, judged),
        causesOf(userId, judged, from, to));
  }

  // 가장 최근 설문의 아침형/저녁형과, 아침 · 저녁 카드를 다 채운 횟수. 답이 없으면 null
  private CallContext.RhythmGap rhythmGapOf(Long userId, List<JudgedCardDay> judged) {
    Chronotype said =
        onboardingAnswerRepository
            .findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId)
            .map(OnboardingAnswer::getChronotype)
            .orElse(null);
    if (said == null || judged.isEmpty()) {
      return null;
    }
    Map<Long, LocalTime> startTimes = new HashMap<>();
    for (Card card :
        cardRepository.findByUserIdAndIdIn(
            userId, judged.stream().map(JudgedCardDay::cardId).distinct().toList())) {
      startTimes.put(card.getId(), card.getStartTime());
    }
    int morningDone = 0;
    int morningTotal = 0;
    int nightDone = 0;
    int nightTotal = 0;
    for (JudgedCardDay day : judged) {
      LocalTime start = startTimes.get(day.cardId());
      // 휴식한 날은 모든 집계에서 뺍니다. (CardResult.REST) 시작 시각이 없는 옛 카드도 뺍니다.
      if (start == null || day.result() == CardResult.REST) {
        continue;
      }
      // 체크리스트까지 다 채운 날만 "다 한 날" 로 셉니다. 미실행이면 달성률이 없거나 0 입니다.
      int done = !day.result().isMiss() && isFull(day.checklistRate()) ? 1 : 0;
      if (!start.isBefore(DAY_START) && start.isBefore(NOON)) {
        morningDone += done;
        morningTotal++;
      } else if (!start.isBefore(EVENING) || start.isBefore(DAY_START)) {
        nightDone += done;
        nightTotal++;
      }
    }
    return new CallContext.RhythmGap(
        said == Chronotype.NIGHT, morningDone, morningTotal, nightDone, nightTotal);
  }

  // 체크리스트 달성률이 100% 인지 (소수 계산 오차를 생각해 아주 작은 차이는 다 채운 것으로 봅니다)
  private static boolean isFull(Double checklistRate) {
    return checklistRate != null && checklistRate >= 1 - 1e-9;
  }

  // 못 채운 이유와 횟수. 많은 것부터 (같으면 먼저 나온 것부터)
  private List<CallContext.CauseCount> causesOf(
      Long userId, List<JudgedCardDay> judged, LocalDate from, LocalDate to) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (JudgedCardDay day : judged) {
      if (day.missReason() != null && day.missReason() != MissReason.ETC) {
        counts.merge(CallSummary.missReasonLabel(day.missReason()), 1, Integer::sum);
      }
    }
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))) {
      String label = shortfallLabel(session.getShortfallReason());
      if (label != null) {
        counts.merge(label, 1, Integer::sum);
      }
    }
    List<CallContext.CauseCount> causes = new ArrayList<>();
    counts.forEach((label, count) -> causes.add(new CallContext.CauseCount(label, count)));
    causes.sort(Comparator.comparingInt(CallContext.CauseCount::count).reversed());
    return causes;
  }

  // 타이머를 목표 전에 끝내며 고른 이유의 글자. 체크인 이유와 같은 뜻이면 같은 글자로 맞춰서 한 번에 셉니다. '기타' 와 없음은 null
  static String shortfallLabel(ShortfallReason reason) {
    if (reason == null) {
      return null;
    }
    return switch (reason) {
      case TIRED -> CallSummary.missReasonLabel(MissReason.TIRED);
      case PHONE -> CallSummary.missReasonLabel(MissReason.PHONE);
      case TOO_MUCH -> CallSummary.missReasonLabel(MissReason.TOO_MUCH);
      case STUCK -> "막힘";
      case MIND_WANDER -> "딴생각";
      case EXTERNAL -> "외부 방해";
      case ETC -> null;
    };
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

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text;
  }
}
