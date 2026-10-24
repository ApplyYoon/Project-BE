package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.CardParseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardParseResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "말로 카드 만들기" 의 로직입니다. 학생이 말한 문장 하나를 카드 초안 여러 장으로 바꿉니다. (이슈 #237, FE #419)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 미리보기를 확인한 뒤 FE 가 카드 저장 API(/api/v1/cards)로 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 서버 DB 에서 맥락을 모읍니다.
//      - 내 진행 중 목표 (번호 · 제목 · D-day): AI 가 카드를 목표에 이을 수 있게
//      - 최근 카드 제목 (최근에 만든 것부터, 겹치지 않게 10개): 평소 쓰던 제목으로 맞추려고
//      - 내 한 칸 길이 (BlockLengthService): 길이를 말하지 않은 카드에 넣을 값
//   3. AI 를 부릅니다. 꺼짐 · 실패면 drafts 가 null 입니다. (AiClient, 기록은 작은 AI 기능 ASSIST)
//   4. AI 가 위기 표현이라고 하면 1번과 같이 합니다. (단어 목록과 AI 중 하나라도 걸리면 감지)
//   5. 카드마다 검사합니다. (CardParse.from — 날짜는 코드가 세고, 내 진행 중 목표 번호만 남김)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 목표 · 카드 · 기록만 읽습니다.
// 학생이 쓴 문장과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CardParseService {

  // AI 에게 보여 주는 최근 카드 제목의 최대 개수
  static final int RECENT_TITLES = 10;

  private final CurrentUserProvider currentUserProvider;
  private final GoalRepository goalRepository;
  private final CardRepository cardRepository;
  private final BlockLengthService blockLengthService;
  private final AiClient aiClient;

  // AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (읽기는 저장소마다 따로 끝남)
  public CardParseResponse parse(CardParseRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현 (단어 목록)
    if (CrisisText.hasCrisisText(List.of(request.text()))) {
      return CardParseResponse.help();
    }

    // 2) 맥락
    Context context = contextOf(userId);

    // 3) AI
    Optional<CardParse.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            CardParse.question(
                request.date(), request.text(), context.goals(), context.recentTitles()),
            CardParse.Answer.class);
    if (answer.isEmpty()) {
      return CardParseResponse.notMade();
    }

    // 4) 위기 표현 (AI 판단)
    if (Boolean.TRUE.equals(answer.get().crisis())) {
      return CardParseResponse.help();
    }

    // 5) 카드마다 검사
    List<CardParse.Draft> drafts =
        CardParse.from(
            answer.get(), request.date(), context.myGoalIds(), context.block().minutes());
    return CardParseResponse.of(drafts, context.block().evidence());
  }

  // AI 에게 줄 내 맥락입니다. "사진으로 카드 만들기"(CardPhotoService)도 같은 맥락을 씁니다.
  //   goals: 내 진행 중 목표 / myGoalIds: 그 번호들 / recentTitles: 최근 카드 제목 / block: 내 한 칸 길이
  record Context(
      List<CardParse.GoalHint> goals,
      Set<String> myGoalIds,
      List<String> recentTitles,
      BlockLength.Choice block) {}

  Context contextOf(Long userId) {
    List<CardParse.GoalHint> goals = new ArrayList<>();
    Set<String> myGoalIds = new HashSet<>();
    for (Goal goal : goalRepository.findByUserIdOrderById(userId)) {
      if (goal.isActive()) {
        String id = String.valueOf(goal.getId());
        goals.add(new CardParse.GoalHint(id, goal.getTitle(), goal.getDDay()));
        myGoalIds.add(id);
      }
    }
    return new Context(goals, myGoalIds, recentTitlesOf(userId), blockLengthService.choose(userId));
  }

  // 최근에 만든 카드의 제목들. 최근 것부터, 같은 제목은 한 번만, 10개까지입니다.
  private List<String> recentTitlesOf(Long userId) {
    List<Card> cards = cardRepository.findByUserIdOrderById(userId);
    List<String> titles = new ArrayList<>();
    // 번호 순서로 왔으니 뒤에서부터 보면 최근에 만든 것부터입니다.
    for (int i = cards.size() - 1; i >= 0 && titles.size() < RECENT_TITLES; i--) {
      String title = cards.get(i).getTitle().trim();
      if (!titles.contains(title)) {
        titles.add(title);
      }
    }
    return titles;
  }
}
