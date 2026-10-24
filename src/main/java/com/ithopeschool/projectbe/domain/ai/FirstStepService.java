package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 첫 준비 행동의 로직입니다. 체크리스트 첫 항목을 아주 작은 준비 행동으로 바꿉니다. (이슈 #236)
// ⚠️ 아무것도 저장하지 않습니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. AI 를 부릅니다. 꺼짐 · 실패면 step 이 null 입니다. (AiClient)
//      호출 기록은 체크리스트 추천과 같은 종류(CHECKLIST)로 남깁니다. 같은 기능(카드 체크리스트)의 한 부분이라서입니다.
//   3. 받은 답을 검사합니다. 쓸 수 없으면 step 이 null 입니다. (FirstStep.from)
//
// 이 기능은 DB 를 읽지 않습니다. 지금 시작하려는 항목 하나를 쪼개는 일이라, 쌓인 기록 중에 쓸 것이 아직 없습니다.
// (체크리스트 결과가 저장되면(#222) "자주 막히는 항목" 을 맥락으로 줄 수 있습니다)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 입니다.
// 보낸 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class FirstStepService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;

  public FirstStepResponse suggest(FirstStepRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(
        Arrays.asList(request.item(), request.title(), request.previousStep()))) {
      return FirstStepResponse.help();
    }

    // 2) AI
    AiQuestion question =
        FirstStep.question(
            request.item(), request.title(), request.level(), request.previousStep());
    Optional<FirstStep.Answer> answer =
        aiClient.ask(userId, GptUsageKind.CHECKLIST, question, FirstStep.Answer.class);

    // 3) 검사
    return FirstStepResponse.of(
        answer.map(raw -> FirstStep.from(raw, request.previousStep())).orElse(null));
  }
}
