package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanResponse;
import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlockRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.Chronotype;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.school.SchoolEvent;
import com.ithopeschool.projectbe.domain.school.SchoolEventRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// AI 하루 계획(POST /ai/day-plan)의 로직입니다. 날짜 하나를 받아, 내 기록을 근거로 그날의 카드 초안을 짭니다. (이슈 #237)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고른 카드만 FE 가 /api/v1/cards 로 저장합니다.
//
// 순서
//   1. 날짜는 오늘부터 14일 안이어야 합니다. (아니면 400)
//   2. 서버 DB 에서 재료를 모읍니다. (FE 가 보낸 값은 쓰지 않음)
//      - 처음 설문: 기상 · 취침 시각, 아침형 / 저녁형, 하루 공부 가능 시간
//      - 고정 일정(학교 · 학원) 중 그날 요일에 있는 것, 그날 이미 있는 카드
//      - 학교를 등록했으면 평일 기본 수업 시간 (수업 시간과 겹치는 고정 일정이 없고, 방학 · 휴업이 아닌 날)
//      - 최근 14일 공부 기록(하루 총량), 내 한 칸 길이(BlockLengthService), 심리검사의 계획 낙관
//      - 진행 중인 목표(D-day · 완주형 하루 분량)와 최근 카드 제목
//   3. 코드가 숫자 · 시각을 정합니다. (DayPlanRule) 총량 → 공부할 수 있는 시간 → 빈 시간 → 칸 놓기 → 칸마다 목표
//   4. AI 가 칸마다 카드 제목 · 체크리스트를 씁니다. 꺼짐 · 한도 · 실패면 기본 글(최근 카드 제목 · 하루 분량)로 채웁니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 것만 읽습니다.
// AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class DayPlanService {

  // 기록을 보는 기간 (어제까지 14일)
  static final int LOOKBACK_DAYS = 14;
  // 계획을 짤 수 있는 날: 오늘부터 이 날 수 뒤까지
  static final int MAX_DAYS_AHEAD = 14;
  // AI 에게 보여 주는 최근 카드 제목 수
  static final int RECENT_TITLES = 10;
  // 이 컨디션(1~5) 이하면 가벼운 계획 (총량을 LIGHTER_PERCENT % 로)
  static final int LOW_CONDITION = 2;
  static final int LIGHTER_PERCENT = 60;

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final PsychTestRepository psychTestRepository;
  private final FixedBlockRepository fixedBlockRepository;
  private final CardRepository cardRepository;
  private final GoalRepository goalRepository;
  private final BlockLengthService blockLengthService;
  private final AiClient aiClient;
  private final Clock clock;
  // 학교를 등록했는지(수업 시간을 피할지)와 그날 학사일정(방학 · 휴업)을 봅니다.
  private final AccountRepository accountRepository;
  private final SchoolEventRepository schoolEventRepository;

  public DayPlanResponse suggest(DayPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate date = request.date();

    // 1) 날짜
    if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "오늘부터 " + MAX_DAYS_AHEAD + "일 안의 날짜를 보내 주세요");
    }
    List<String> evidence = new ArrayList<>();

    // 2) 재료
    Optional<OnboardingAnswer> onboarding =
        onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId);
    LocalTime sleep = onboarding.map(OnboardingAnswer::getSleepTime).orElse(null);
    LocalTime wake = onboarding.map(OnboardingAnswer::getWakeUpTime).orElse(null);
    boolean night =
        onboarding.map(OnboardingAnswer::getChronotype).orElse(null) == Chronotype.NIGHT;
    Integer selfDaily =
        onboarding.map(OnboardingAnswer::getDailyAvailableMinutes).filter(m -> m > 0).orElse(null);
    boolean planOptimismHigh =
        psychTestRepository
            .findFirstByUserIdOrderByIdDesc(userId)
            // 답이 거의 다 같아서 믿기 어려운 심리검사는 없는 것으로 봅니다. (PsychTest.isReliable)
            .filter(PsychTest::isReliable)
            .map(
                test ->
                    PsychScore.levelOf(
                            PsychScore.scoresOf(test.getAnswers()).get(PsychScale.PLAN_OPTIMISM))
                        == PsychScore.Level.HIGH)
            .orElse(false);

    // 그날 차지한 시간: 고정 일정 + 시작 시각이 있는 카드
    List<DayPlanRule.Span> busy = new ArrayList<>();
    for (FixedBlock block : fixedBlockRepository.findByUserIdOrderById(userId)) {
      if (block.isOnDate(date)) {
        busy.add(
            new DayPlanRule.Span(
                DayPlanRule.dayMinutesOf(block.getStartTime()),
                DayPlanRule.dayMinutesOf(block.getEndTime())));
        evidence.add(
            "고정 일정 「"
                + block.getTitle()
                + "」 "
                + block.getStartTime()
                + " ~ "
                + block.getEndTime()
                + " 을 피했어요");
      }
    }
    String classHoursLine = classHoursLineOf(userId, date, busy);
    if (classHoursLine != null) {
      evidence.add(classHoursLine);
    } else if (busy.isEmpty()) {
      evidence.add("그날 고정 일정이 없어요. 학교 · 학원 시간을 고정 일정에 넣으면 피해서 짜요");
    }
    int alreadyPlanned = 0;
    int dayCards = 0;
    for (Card card : cardRepository.findCandidatesOn(userId, date)) {
      if (!card.isOnDate(date)) {
        continue;
      }
      alreadyPlanned += card.getDurationMinutes();
      if (card.getStartTime() != null) {
        int start = DayPlanRule.dayMinutesOf(card.getStartTime());
        busy.add(new DayPlanRule.Span(start, start + card.getDurationMinutes()));
        dayCards++;
      }
    }
    if (dayCards > 0) {
      evidence.add("그날 이미 있는 카드 " + dayCards + "장의 앞뒤 10분을 비웠어요");
    }

    // 3) 숫자 · 시각
    BlockLength.Choice block = blockLengthService.choose(userId);
    evidence.add(0, block.evidence());
    DayPlanRule.Total total =
        DayPlanRule.totalMinutesOf(
            dailyMinutesOf(userId, today), selfDaily, planOptimismHigh, alreadyPlanned);
    evidence.addAll(1, total.evidence());
    // 오늘 컨디션이 낮다고 했으면(1~2) 총량을 60% 로 줄여 가벼운 계획을 짭니다. (2026-10-18 계획 핑퐁)
    // 학생은 코치 대화에서 [더 늘려 줘] 로 다시 늘릴 수 있습니다. (DayPlanReviseService)
    // 한 칸 길이는 그대로 두고 총량만 줄이므로, 칸 수가 줄어듭니다. (한 칸은 30분 이상, StudyBlock)
    if (request.condition() != null
        && request.condition() <= LOW_CONDITION
        && total.minutes() >= DayPlanRule.MIN_BLOCK) {
      int lighter = Math.max(DayPlanRule.MIN_BLOCK, total.minutes() * LIGHTER_PERCENT / 100);
      evidence.add("오늘 컨디션이 낮다고 해서 " + total.minutes() + "분 대신 " + lighter + "분으로 가볍게 짰어요");
      total = new DayPlanRule.Total(lighter, total.evidence());
    }
    if (total.minutes() < DayPlanRule.MIN_BLOCK) {
      evidence.add("그날은 이미 충분히 계획했어요");
      return new DayPlanResponse(date, block.minutes(), total.minutes(), List.of(), evidence);
    }

    DayPlanRule.Span window = DayPlanRule.studyWindowOf(sleep, wake);
    evidence.add(windowEvidence(sleep, wake, window));
    if (date.equals(today)) {
      window = new DayPlanRule.Span(Math.max(window.start(), nowDayMinutes()), window.end());
    }
    List<DayPlanRule.Span> free = DayPlanRule.freeSlotsOf(window, busy);
    List<DayPlanRule.Placed> placed =
        DayPlanRule.placeBlocks(free, block.minutes(), total.minutes(), night);
    if (night) {
      evidence.add("저녁형이라고 해서 늦은 시간부터 채웠어요");
    }
    if (placed.isEmpty()) {
      evidence.add("그날은 카드를 넣을 빈 시간이 없어요");
      return new DayPlanResponse(date, block.minutes(), total.minutes(), List.of(), evidence);
    }
    int left = total.minutes() - placed.stream().mapToInt(DayPlanRule.Placed::minutes).sum();
    if (left >= DayPlanRule.MIN_BLOCK) {
      evidence.add("빈 시간이 모자라서 " + left + "분은 넣지 못했어요");
    } else if (left > 0) {
      evidence.add("남은 " + left + "분은 " + DayPlanRule.MIN_BLOCK + "분보다 짧아서 칸을 만들지 않았어요");
    }

    // 칸마다 목표
    List<Goal> activeGoals = new ArrayList<>();
    for (Goal goal : goalRepository.findByUserIdOrderById(userId)) {
      if (goal.isActive()) {
        activeGoals.add(goal);
      }
    }
    Map<String, Goal> goalsById = new HashMap<>();
    List<DayPlanRule.GoalInfo> goalInfos = new ArrayList<>();
    for (Goal goal : activeGoals) {
      int daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(date, goal.getDDay()));
      String pace =
          goal.getKind() == GoalKind.COMPLETION && goal.getTotalAmount() != null
              ? DayPlanRule.dailyPaceOf(goal.getTotalAmount(), daysLeft) + goal.getAmountUnit()
              : null;
      String id = String.valueOf(goal.getId());
      goalInfos.add(new DayPlanRule.GoalInfo(id, goal.getTitle(), daysLeft, pace));
      goalsById.put(id, goal);
    }
    List<DayPlanRule.GoalInfo> rotation = DayPlanRule.goalRotation(goalInfos, placed.size());
    evidence.add(rotation.isEmpty() ? "진행 중인 목표가 없어서 최근 카드로 채웠어요" : "D-day 가 가까운 목표를 더 많이 넣었어요");

    // 4) 글 (AI, 안 되면 기본 글)
    List<Card> myCards = cardRepository.findByUserIdOrderById(userId);
    List<String> recentTitles = recentTitlesOf(myCards);
    List<DayPlanPrompt.Block> blocks = new ArrayList<>();
    List<DayPlanPrompt.Text> fallbacks = new ArrayList<>();
    for (int i = 0; i < placed.size(); i++) {
      DayPlanRule.Placed spot = placed.get(i);
      DayPlanRule.GoalInfo goal = rotation.isEmpty() ? null : rotation.get(i);
      blocks.add(
          new DayPlanPrompt.Block(
              DayPlanRule.timeTextOf(spot.start()),
              spot.minutes(),
              goal == null ? null : goal.title(),
              goal == null ? null : goal.daysLeft(),
              goal == null ? null : goal.pace()));
      fallbacks.add(fallbackOf(goal, myCards, recentTitles, i));
    }
    List<DayPlanPrompt.Text> texts =
        DayPlanPrompt.textsOf(
            aiClient
                .ask(
                    userId,
                    GptUsageKind.ASSIST,
                    DayPlanPrompt.question(date, blocks, recentTitles, extraLinesOf(request)),
                    DayPlanPrompt.Answer.class)
                .orElse(null),
            fallbacks);

    List<DayPlanResponse.Draft> drafts = new ArrayList<>();
    for (int i = 0; i < placed.size(); i++) {
      DayPlanRule.GoalInfo goal = rotation.isEmpty() ? null : rotation.get(i);
      drafts.add(
          new DayPlanResponse.Draft(
              date,
              DayPlanRule.timeTextOf(placed.get(i).start()),
              placed.get(i).minutes(),
              texts.get(i).title(),
              texts.get(i).checklist(),
              goal == null ? null : goal.id()));
    }
    return new DayPlanResponse(date, block.minutes(), total.minutes(), drafts, evidence);
  }

  // 학교 수업 시간을 피해야 하는 날이면 기본 수업 시간(08:00 ~ 16:30)을 busy 에 넣고 근거 문장을 돌려줍니다.
  // 넣지 않는 날은 null 입니다.
  //   - 학교를 등록하지 않았거나, 주말이거나
  //   - 수업 시간과 겹치는 고정 일정이 이미 있거나 (학생이 넣은 학교 시간이 더 정확함)
  //     학원처럼 저녁에만 고정 일정이 있는 학생은 수업 시간을 아직 안 피했으니 넣습니다.
  //   - 그날 학사일정에 방학 · 휴업이 있으면 (방학식 날은 학교에 가니까 수업 있는 날로 봅니다)
  private String classHoursLineOf(Long userId, LocalDate date, List<DayPlanRule.Span> busy) {
    String schoolCode =
        accountRepository.findByUserId(userId).map(Account::getSchoolCode).orElse(null);
    if (schoolCode == null || schoolCode.isBlank()) {
      return null;
    }
    DayOfWeek day = date.getDayOfWeek();
    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
      return null;
    }
    DayPlanRule.Span classHours = DayPlanRule.classHours();
    for (DayPlanRule.Span span : busy) {
      if (DayPlanRule.overlaps(span, classHours)) {
        return null;
      }
    }
    for (SchoolEvent event :
        schoolEventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
            schoolCode, date, date)) {
      String name = event.getName();
      if (name.contains("휴업") || (name.contains("방학") && !name.contains("방학식"))) {
        return null;
      }
    }
    busy.add(classHours);
    return "학교 수업 시간(평일 "
        + DayPlanRule.CLASS_START
        + " ~ "
        + DayPlanRule.CLASS_END
        + ")을 피했어요. 시간표가 다르면 학교 시간을 고정 일정에 넣어 주세요";
  }

  // 학생이 알려 준 것(준비하는 것 · 오늘 과목)을 AI 입력 줄로. 없으면 빈 목록
  static List<String> extraLinesOf(DayPlanRequest request) {
    List<String> lines = new ArrayList<>();
    if (request.focus() != null) {
      lines.add("준비하는 것: " + request.focus().describe());
    }
    if (!request.subjects().isEmpty()) {
      lines.add("오늘 배운 과목: " + String.join(", ", request.subjects()));
    }
    return lines;
  }

  // 최근 14일(어제까지) 중 공부한 날마다의 순공(분). 노는 기록은 뺍니다.
  private List<Integer> dailyMinutesOf(Long userId, LocalDate today) {
    Map<LocalDate, Long> dailyMillis = new HashMap<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(today.minusDays(LOOKBACK_DAYS)), DayRule.startOf(today))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      dailyMillis.merge(DayRule.dateOf(session.getStartedAt()), millis, Long::sum);
    }
    List<Integer> dailyMinutes = new ArrayList<>();
    for (long millis : dailyMillis.values()) {
      dailyMinutes.add(StudyTime.toMinutes(millis));
    }
    return dailyMinutes;
  }

  // 지금 시각(한국 시간)을 하루 시작 기준 분으로, 10분 단위로 올림.   19:03 → 19:10
  private int nowDayMinutes() {
    LocalTime now = clock.instant().atZone(SEOUL).toLocalTime();
    int minutes = DayPlanRule.dayMinutesOf(now);
    return (minutes + 9) / 10 * 10;
  }

  // 공부할 수 있는 시간의 근거 문장
  private static String windowEvidence(LocalTime sleep, LocalTime wake, DayPlanRule.Span window) {
    String range =
        DayPlanRule.timeTextOf(window.start()) + " ~ " + DayPlanRule.timeTextOf(window.end());
    if (sleep == null || wake == null) {
      return "취침 · 기상 시각을 아직 몰라서 " + range + " 사이에 짰어요. 관리 설정에서 넣으면 맞춰 짜요";
    }
    return "취침 "
        + sleep
        + " · 기상 "
        + wake
        + " 이라 수면 시간(최소 6시간)과 일어난 뒤 1시간을 빼고 "
        + range
        + " 사이에 짰어요";
  }

  // AI 가 못 쓴 칸의 기본 글
  //   목표가 있는 칸: 그 목표로 만든 가장 최근 카드의 제목(없으면 목표 이름), 체크리스트는 하루 분량 또는 제목
  //   목표가 없는 칸: 최근 카드 제목을 차례로 (없으면 "공부")
  private static DayPlanPrompt.Text fallbackOf(
      DayPlanRule.GoalInfo goal, List<Card> myCards, List<String> recentTitles, int index) {
    String title;
    if (goal != null) {
      title = cut(goal.title());
      for (int i = myCards.size() - 1; i >= 0; i--) {
        Card card = myCards.get(i);
        if (card.getGoalId() != null && String.valueOf(card.getGoalId()).equals(goal.id())) {
          title = cut(card.getTitle().trim());
          break;
        }
      }
      return new DayPlanPrompt.Text(
          title, List.of(goal.pace() == null ? title : goal.pace() + " 하기"));
    }
    title = recentTitles.isEmpty() ? "공부" : cut(recentTitles.get(index % recentTitles.size()));
    return new DayPlanPrompt.Text(title, List.of(title));
  }

  // 카드 제목 길이(30자)에 맞게 자릅니다.
  private static String cut(String text) {
    return text.length() <= CardParse.MAX_TITLE_LENGTH
        ? text
        : text.substring(0, CardParse.MAX_TITLE_LENGTH);
  }

  // 최근에 만든 카드의 제목들. 최근 것부터, 같은 제목은 한 번만, 10개까지
  private static List<String> recentTitlesOf(List<Card> cards) {
    List<String> titles = new ArrayList<>();
    for (int i = cards.size() - 1; i >= 0 && titles.size() < RECENT_TITLES; i--) {
      String title = cards.get(i).getTitle().trim();
      if (!titles.contains(title)) {
        titles.add(title);
      }
    }
    return titles;
  }
}
