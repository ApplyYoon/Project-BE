package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.FailCauseRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.stats.MissReasonService;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 예상 실패 원인 선지의 로직입니다. 카드 제목을 받아 "이번에 못 한다면 무엇 때문일지" 선지 3개를 돌려줍니다. (이슈 #237 표 3번)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고른 선지는 카드와 함께 저장합니다. (카드 저장 모양 #222)
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 서버 DB 에 쌓인 내 최근 30일 실패 원인을 셉니다. 실패 원인 분석 API(#39, MissReasonService)와 같은 계산입니다.
//   3. 원인 3개를 코드가 고릅니다. 기록이 모자라면 options 가 null 입니다. (FailCauseOptions.pick)
//   4. AI 가 원인마다 이 카드에 맞는 장면을 씁니다. 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답이면 options 가 null 입니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽습니다.
// AI 에게는 카드 제목과 원인 이름 · 날 수만 줍니다. 체크인의 '기타' 사유 글은 주지 않습니다.
@Service
@RequiredArgsConstructor
public class FailCauseService {

  private final CurrentUserProvider currentUserProvider;
  private final MissReasonService missReasonService;
  private final AiClient aiClient;

  public FailCauseResponse suggest(FailCauseRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(List.of(request.title()))) {
      return FailCauseResponse.help();
    }

    // 2) 최근 30일 실패 원인 (날 수가 많은 것부터 옵니다)
    MissReasonStatsResponse stats = missReasonService.getMissReasons();
    List<FailCauseOptions.ReasonDays> history = new ArrayList<>();
    for (MissReasonStatsResponse.Reason reason : stats.last30Days().reasons()) {
      history.add(
          new FailCauseOptions.ReasonDays(
              MissReason.fromValue(reason.reason()), reason.dayCount()));
    }

    // 3) 원인 고르기
    List<FailCauseOptions.ReasonDays> picked = FailCauseOptions.pick(history);
    if (picked == null) {
      return FailCauseResponse.of(null);
    }

    // 4) AI 가 장면을 씀
    Optional<FailCauseOptions.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            FailCauseOptions.question(request.title(), picked),
            FailCauseOptions.Answer.class);
    List<String> texts = answer.map(raw -> FailCauseOptions.from(raw, picked)).orElse(null);
    if (texts == null) {
      return FailCauseResponse.of(null);
    }

    List<FailCauseResponse.Option> options = new ArrayList<>();
    for (int i = 0; i < picked.size(); i++) {
      MissReason reason = picked.get(i).reason();
      options.add(
          new FailCauseResponse.Option(
              reason.toValue(), texts.get(i), FailCauseOptions.INNER_REASONS.contains(reason)));
    }
    return FailCauseResponse.of(options);
  }
}
