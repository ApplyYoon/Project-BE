package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.DayPlanReviseResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "계획 핑퐁" 의 로직입니다. 코치가 짜 준 계획을 학생의 요청대로 AI 가 고칩니다. (2026-10-18)
// 사용자: "얘가 플래너 한번 짜 주고 핑퐁하고 수정하는 게 맞지, 일방적으로 10분 주는 건 아닌 듯"
// ⚠️ 아무것도 저장하지 않습니다. 학생이 [이대로 할게] 를 누르면 FE 가 카드로 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 학생의 요청에 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56)
//   2. AI 를 부릅니다. 꺼짐 · 하루 한도 · 실패면 drafts 가 null 입니다. (한도는 ASSIST)
//   3. 받은 계획을 검사합니다. (DayPlanRevise.from — 시각 · 길이 · 겹침 · 하루 합 · 글)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 한도는 사람마다 셉니다.
@Service
@RequiredArgsConstructor
public class DayPlanReviseService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;
  private final Clock clock;

  public DayPlanReviseResponse revise(DayPlanReviseRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현 — 학생이 쓴 요청과 카드 글(사진에서 온 글일 수 있음)
    List<String> texts = new ArrayList<>();
    texts.add(request.request());
    for (DayPlanReviseRequest.Draft draft : request.drafts()) {
      texts.add(draft.title());
    }
    if (request.focus() != null) {
      texts.add(request.focus().name());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return DayPlanReviseResponse.help();
    }

    // 2) AI → 3) 검사
    return aiClient
        .ask(
            userId,
            GptUsageKind.ASSIST,
            DayPlanRevise.question(request),
            DayPlanRevise.Answer.class)
        .map(
            answer ->
                DayPlanReviseResponse.of(
                    DayPlanRevise.from(answer, DayRule.dateOf(clock.instant()), request.drafts())))
        .orElse(DayPlanReviseResponse.notMade());
  }
}
