package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 루틴 카드 제안의 로직입니다. 목표 + 방해물 + 대처를 받아 AI 가 만든 루틴 카드 2~3장을 돌려줍니다. (이슈 #239)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고른 카드만 FE 가 카드로 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 완주형이면 하루 분량을 코드가 계산합니다. (RoutineCardPrompt.dailyAmountOf, 오늘은 하루의 기준 새벽 4시)
//   3. AI 를 부릅니다. 꺼짐 · 실패면 cards 가 null 입니다. (AiClient, 기록은 작은 AI 기능 ASSIST)
//   4. 받은 카드를 검사합니다. 쓸 만한 카드가 2장보다 적으면 cards 가 null 입니다. (RoutineCardDraft)
//
// DB 는 읽지 않습니다. 목표가 아직 서버에 저장되지 않아서(#221) 필요한 글을 요청으로 받습니다.
// 목표가 저장되면 목표 번호를 받고, 그 목표의 지난 카드 기록을 DB 에서 찾아 AI 에게 줄 수 있습니다.
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 입니다.
// 보낸 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class RoutineCardService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;
  private final Clock clock;

  public RoutineCardResponse suggest(RoutineCardRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(
        Arrays.asList(request.goal(), request.obstacle(), request.plan(), request.amountUnit()))) {
      return RoutineCardResponse.help();
    }

    // 2) 하루 분량
    String dailyAmount =
        RoutineCardPrompt.dailyAmountOf(
            request.totalAmount(),
            request.amountUnit(),
            request.dDay(),
            DayRule.dateOf(clock.instant()));

    // 3) AI
    AiQuestion question =
        RoutineCardPrompt.question(request.goal(), request.obstacle(), request.plan(), dailyAmount);
    Optional<RoutineCardPrompt.Answer> answer =
        aiClient.ask(userId, GptUsageKind.ASSIST, question, RoutineCardPrompt.Answer.class);

    // 4) 검사
    return RoutineCardResponse.of(answer.map(RoutineCardDraft::from).orElse(null));
  }
}
