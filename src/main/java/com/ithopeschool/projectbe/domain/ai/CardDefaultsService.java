package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardDefaultsResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "카드 폼 기본값 추천" 의 로직입니다. 카드 제목을 보고 목표 연결 · 유형 · 길이를 미리 채웁니다. (이슈 #237, FE #419)
// ⚠️ 아무것도 저장하지 않습니다. FE 가 폼에 채워 보여 주고, 학생이 고쳐서 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 같은 제목의 지난 카드가 있으면 (가장 최근에 만든 것) AI 를 부르지 않고 그 카드를 따릅니다.
//      - 길이 · 유형은 그대로, 목표는 그 목표가 아직 진행 중일 때만
//      같은 일을 하는 카드는 보통 같은 모양이고, 학생이 지난번에 직접 정한 값이 AI 의 짐작보다 정확합니다.
//   3. 처음 보는 제목이면
//      - 길이: 내 기록의 한 칸 길이 (BlockLengthService, AI 를 부르지 않음)
//      - 목표 · 유형: AI 가 고릅니다. 꺼짐 · 하루 한도 · 실패면 둘 다 null 입니다. (한도는 작은 AI 기능 묶음 ASSIST)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 카드 · 목표 · 기록만 읽습니다.
// 학생이 쓴 제목과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CardDefaultsService {

  private final CurrentUserProvider currentUserProvider;
  private final CardRepository cardRepository;
  private final GoalRepository goalRepository;
  private final BlockLengthService blockLengthService;
  private final AiClient aiClient;

  public CardDefaultsResponse suggest(CardDefaultsRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    String title = request.title();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(List.of(title))) {
      return CardDefaultsResponse.help();
    }

    // 내 진행 중 목표 (번호 → 목표)
    Map<String, Goal> activeGoals = new HashMap<>();
    List<CardParse.GoalHint> goalHints = new ArrayList<>();
    for (Goal goal : goalRepository.findByUserIdOrderById(userId)) {
      if (goal.isActive()) {
        String id = String.valueOf(goal.getId());
        activeGoals.put(id, goal);
        goalHints.add(new CardParse.GoalHint(id, goal.getTitle(), goal.getDDay()));
      }
    }

    // 2) 같은 제목의 지난 카드
    Card last = lastCardWithTitle(userId, title);
    if (last != null) {
      List<String> evidence = new ArrayList<>();
      evidence.add("지난번 같은 제목의 카드(" + last.getCardDate() + ")와 같은 목표 · 유형 · 길이로 채웠어요");
      String goalId = last.getGoalId() == null ? null : String.valueOf(last.getGoalId());
      if (goalId != null && !activeGoals.containsKey(goalId)) {
        goalId = null; // 지난번 목표가 끝났거나(완료 · 포기) 없어졌으면 잇지 않습니다.
        evidence.add("지난번에 이은 목표는 지금 진행 중이 아니라서 목표는 비워 두었어요");
      }
      return new CardDefaultsResponse(
          false, goalId, last.getType().toValue(), last.getDurationMinutes(), evidence);
    }

    // 3) 처음 보는 제목
    BlockLength.Choice block = blockLengthService.choose(userId);
    List<String> evidence = new ArrayList<>(List.of(block.evidence()));
    CardDefaults.Picked picked =
        aiClient
            .ask(
                userId,
                GptUsageKind.ASSIST,
                CardDefaults.question(title, goalHints),
                CardDefaults.Answer.class)
            .map(answer -> CardDefaults.from(answer, activeGoals.keySet()))
            .orElse(new CardDefaults.Picked(null, null));
    if (picked.goalId() != null) {
      evidence.add("제목을 보고 「" + activeGoals.get(picked.goalId()).getTitle() + "」 목표에 이었어요");
    }
    return new CardDefaultsResponse(
        false,
        picked.goalId(),
        picked.type() == null ? null : picked.type().toValue(),
        block.minutes(),
        evidence);
  }

  // 제목이 같은(앞뒤 공백을 빼고 글자 그대로) 내 카드 중 가장 최근에 만든 것. 없으면 null 입니다.
  private Card lastCardWithTitle(Long userId, String title) {
    List<Card> cards = cardRepository.findByUserIdOrderById(userId);
    // 번호 순서로 왔으니 뒤에서부터 보면 최근에 만든 것부터입니다.
    for (int i = cards.size() - 1; i >= 0; i--) {
      if (cards.get(i).getTitle().trim().equals(title)) {
        return cards.get(i);
      }
    }
    return null;
  }
}
