package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.QuickCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 즉석 카드의 로직입니다. 학생이 쓴 한 줄을 카드 초안(제목 + 체크리스트)으로 바꿉니다. (이슈 #237 표 6번)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 확인한 뒤 FE 가 카드로 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 서버 DB 에서 맥락을 모읍니다.
//      - 내 과목 이름 (과목 번호 순서)
//      - 최근 카드 제목: 최근 2주(오늘 포함) 내 공부 계획의 제목, 최근 것부터 겹치지 않게 RECENT_TITLES(10)개
//        평소 쓰던 제목으로 맞추면, 같은 카드의 지난 기록(체크리스트 추천 등)을 이어서 볼 수 있습니다.
//   3. AI 가 카드 초안을 씁니다. 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답이면 card 가 null 입니다. (QuickCard.from)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 과목·계획만 읽습니다.
// 학생이 쓴 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class QuickCardService {

  // 최근 카드 제목을 찾는 기간 (오늘 포함 14일)
  static final int LOOKBACK_DAYS = 14;
  // AI 에게 보여 주는 최근 카드 제목의 최대 개수
  static final int RECENT_TITLES = 10;

  private final CurrentUserProvider currentUserProvider;
  private final SubjectRepository subjectRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final AiClient aiClient;
  private final Clock clock;

  public QuickCardResponse suggest(QuickCardRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(List.of(request.text()))) {
      return QuickCardResponse.help();
    }

    // 2) 맥락
    List<String> subjectNames = new ArrayList<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      subjectNames.add(subject.getName());
    }
    List<String> recentTitles = recentTitlesOf(userId);

    // 3) AI
    Optional<QuickCard.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            QuickCard.question(request.text(), subjectNames, recentTitles),
            QuickCard.Answer.class);
    QuickCard.Draft draft = answer.map(QuickCard::from).orElse(null);
    if (draft == null) {
      return QuickCardResponse.of(null);
    }
    return QuickCardResponse.of(new QuickCardResponse.Card(draft.title(), draft.items()));
  }

  // 최근 2주 내 공부 계획의 제목들. 최근 것부터, 같은 제목은 한 번만, 10개까지입니다.
  private List<String> recentTitlesOf(Long userId) {
    LocalDate today = DayRule.dateOf(clock.instant());
    List<StudyPlan> plans =
        studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(
            userId, today.minusDays(LOOKBACK_DAYS - 1), today);
    List<String> titles = new ArrayList<>();
    // 날짜 순서로 왔으니 뒤에서부터 보면 최근 것부터입니다.
    for (int i = plans.size() - 1; i >= 0 && titles.size() < RECENT_TITLES; i--) {
      StudyPlan plan = plans.get(i);
      String title = plan.getContent().trim();
      if (plan.getKind() == PlanKind.STUDY && !titles.contains(title)) {
        titles.add(title);
      }
    }
    return titles;
  }
}
