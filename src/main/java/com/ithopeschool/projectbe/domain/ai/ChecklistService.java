package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.domain.thinkingtest.StudyStyle;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTestRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 카드 체크리스트 추천의 로직입니다. 카드 제목을 받아 AI 가 만든 체크리스트를 돌려줍니다. (이슈 #236)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고친 뒤 카드와 함께 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 서버 DB 에 쌓인 내 기록에서 맥락을 모읍니다.
//      - 지난 같은 카드: 최근 4주(오늘 빼고) 같은 제목의 공부 계획, 최근 것부터 5개. 목표 시간 · 실제 순공 · 끝냈는지
//      - 계획 성향: 가장 최근 생각 습관 테스트의 "계획할 때" 가 낙관인지 (안 했으면 낙관 아님)
//   3. 개수를 코드가 정합니다. (ChecklistPrompt.countOf)
//   4. AI 를 부릅니다. 꺼짐 · 하루 한도 · 실패면 items 가 null 입니다. (AiClient)
//   5. 받은 항목을 검사합니다. 쓸 만한 항목이 모자라면 items 가 null 입니다. (ChecklistDraft)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽습니다.
// 카드 제목과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class ChecklistService {

  // 지난 같은 카드를 찾는 기간 (오늘 빼고 최근 28일 = 기획서의 "최근 4주")
  static final int LOOKBACK_DAYS = 28;
  // AI 에게 보여 주는 지난 같은 카드의 최대 개수 (최근 것부터)
  static final int MAX_PAST_CARDS = 5;

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;
  private final StudySessionRepository studySessionRepository;
  private final ThinkingTestRepository thinkingTestRepository;
  private final AiClient aiClient;
  private final Clock clock;

  // AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다.
  // (한도 기록은 GptUsageGuard 가 따로 저장합니다)
  public ChecklistResponse suggest(ChecklistRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현 (지난 같은 카드는 제목이 같아서 따로 보지 않습니다)
    if (CrisisText.hasCrisisText(Arrays.asList(request.title(), request.goal()))) {
      return ChecklistResponse.help();
    }

    // 2) 맥락
    List<StudyPlan> pastPlans = pastSameCards(userId, request.title());

    // 3) 개수. 체크리스트 결과("해낸 항목 수")를 저장하는 곳이 아직 없어서(#222) 지금은 빈 목록을 넘깁니다.
    //    그래서 지금은 늘 기본 3~6개이고, 계획 성향은 AI 에게 맥락으로만 줍니다. #222 가 생기면 여기서 채웁니다.
    boolean planOptimistic = isPlanOptimistic(userId);
    ChecklistPrompt.Count count = ChecklistPrompt.countOf(planOptimistic, List.of());

    // 4) AI
    AiQuestion question =
        ChecklistPrompt.question(
            request.title(), request.goal(), planOptimistic, pastCardsOf(userId, pastPlans), count);
    Optional<ChecklistPrompt.Answer> answer =
        aiClient.ask(userId, GptUsageKind.CHECKLIST, question, ChecklistPrompt.Answer.class);

    // 5) 검사
    return ChecklistResponse.of(answer.map(raw -> ChecklistDraft.from(raw, count)).orElse(null));
  }

  // 최근 4주(오늘 빼고) 내 공부 계획 중 제목이 같은 것들. 최근 것부터 5개까지입니다.
  private List<StudyPlan> pastSameCards(Long userId, String title) {
    LocalDate today = DayRule.dateOf(clock.instant());
    List<StudyPlan> plans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, today.minusDays(LOOKBACK_DAYS), today.minusDays(1));
    List<StudyPlan> same = new ArrayList<>();
    // 날짜 순서로 왔으니 뒤에서부터 보면 최근 것부터입니다.
    for (int i = plans.size() - 1; i >= 0 && same.size() < MAX_PAST_CARDS; i--) {
      StudyPlan plan = plans.get(i);
      if (plan.getKind() == PlanKind.STUDY && plan.getContent().trim().equals(title)) {
        same.add(plan);
      }
    }
    return same;
  }

  // 지난 계획마다 그 계획으로 켠 타이머의 순공을 더해 AI 에게 줄 모양으로 바꿉니다.
  private List<ChecklistPrompt.PastCard> pastCardsOf(Long userId, List<StudyPlan> plans) {
    if (plans.isEmpty()) {
      return List.of();
    }
    List<Long> planIds = new ArrayList<>();
    for (StudyPlan plan : plans) {
      planIds.add(plan.getId());
    }
    // 계획 번호 → 순공(밀리초). 밀리초로 다 더한 뒤 마지막에 한 번만 분으로 바꿉니다. (StudyTime.toMinutes)
    Map<Long, Long> studiedMillis = new HashMap<>();
    for (StudySession session : studySessionRepository.findByUserIdAndPlanIdIn(userId, planIds)) {
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      studiedMillis.merge(session.getPlanId(), millis, Long::sum);
    }
    List<ChecklistPrompt.PastCard> pastCards = new ArrayList<>();
    for (StudyPlan plan : plans) {
      pastCards.add(
          new ChecklistPrompt.PastCard(
              plan.getPlanDate(),
              plan.getTargetMinutes(),
              StudyTime.toMinutes(studiedMillis.getOrDefault(plan.getId(), 0L)),
              plan.isDone()));
    }
    return pastCards;
  }

  // 가장 최근 생각 습관 테스트에서 "계획할 때" 축이 낙관인지. 테스트를 안 했으면 false 입니다.
  // (온보딩 심리검사 v3 의 "계획 낙관"(#219)이 서버에 저장되면 그 값으로 바꿉니다)
  private boolean isPlanOptimistic(Long userId) {
    Optional<ThinkingTest> latest = thinkingTestRepository.findFirstByUserIdOrderByIdDesc(userId);
    if (latest.isEmpty()) {
      return false;
    }
    for (StudyStyle.AxisResult result : StudyStyle.stylesOf(latest.get().getScores())) {
      if (result.axis().first() == CognitiveBias.PLANNING_FALLACY) {
        return result.label().equals(result.axis().highLabel());
      }
    }
    return false;
  }
}
