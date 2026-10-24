package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.TextLabelRequest;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 글에 이름표 붙이기의 로직입니다. (이슈 #237 표 7번)
// ⚠️ 아무것도 저장하지 않습니다. 이름표는 FE 가 그 기록(일시정지·실패·메모)과 함께 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//      딴생각 메모에는 걱정이 적히기 쉬워서 특히 먼저 봅니다.
//   2. AI 가 이름표 하나를 고릅니다. 꺼짐 · 실패 · 정해진 이름표가 아니면 label 이 null 입니다.
//
// 이 기능은 DB 를 읽지 않습니다. 글 하나의 뜻만 보면 되는 일이라, 쌓인 기록이 필요 없습니다.
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 입니다.
// 보낸 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class TextLabelService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;

  public TextLabelResponse label(TextLabelRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(List.of(request.text()))) {
      return TextLabelResponse.help();
    }

    // 2) AI
    Optional<TextLabel.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            TextLabel.question(request.kind(), request.text()),
            TextLabel.Answer.class);
    return TextLabelResponse.of(
        answer.map(raw -> TextLabel.from(raw, request.kind())).orElse(null));
  }
}
