package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DailyMissionsResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "AI 가 짜는 오늘의 미션" 의 로직입니다. (2026-10-18)
// ⚠️ 아무것도 저장하지 않습니다. FE 가 그날 하루 기기에 들고 있습니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//      학생이 직접 쓴 글은 준비하는 것의 이름 한 줄뿐이라 그것과 과목 · 시험 이름을 봅니다.
//   2. AI 를 부릅니다. 꺼짐 · 하루 한도 · 실패면 missions 가 null 입니다. (AiClient)
//      하루 한도는 다른 작은 AI 기능과 같은 종류(ASSIST)로 셉니다. (새 종류를 만들면 Neon 의 kind 검사를 고쳐야 해서)
//   3. 받은 답을 검사합니다. 쓸 미션이 없으면 null 입니다. (DailyMissions.from)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 한도는 사람마다 셉니다.
// 보낸 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class DailyMissionsService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;

  public DailyMissionsResponse suggest(DailyMissionsRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    List<String> texts = new ArrayList<>(request.subjects());
    if (request.focus() != null) {
      texts.add(request.focus().name());
    }
    if (request.exam() != null) {
      texts.add(request.exam().name());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return DailyMissionsResponse.help();
    }

    // 2) AI
    Optional<DailyMissions.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            DailyMissions.question(request),
            DailyMissions.Answer.class);

    // 3) 검사
    return DailyMissionsResponse.of(
        answer.map(raw -> DailyMissions.from(raw, request.subjects())).orElse(null));
  }
}
