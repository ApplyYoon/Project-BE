package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingService;
import com.ithopeschool.projectbe.domain.stats.MissReasonService;
import com.ithopeschool.projectbe.domain.stats.dto.MissReasonStatsResponse;
import com.ithopeschool.projectbe.domain.suggestion.dto.ImprovementSuggestionsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 개선안 3개 제안의 로직입니다. (이슈 #51, 기획서 "자기 실험: 전략 카탈로그에서 쉬움·보통·강함 3개 제시")
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고른 개선안을 저장하고 실험을 시작하는 것은 #52 입니다.
//
// 순서 (고르는 것은 코드, 다듬는 것만 GPT)
//   1. 서버 DB 에 쌓인 내 최근 30일 실패 원인을 읽습니다. 실패 원인 분석 API(#39, MissReasonService)와 같은 계산입니다.
//   2. 기록이 모자라면 온보딩 설문의 "계획이 주로 언제 무너졌나"(#21)를 읽습니다. 설문을 안 했으면 없는 것으로 봅니다.
//   3. 원인 하나와 그 원인의 전략 3개(쉬움 · 보통 · 강함)를 코드가 고릅니다. (ImprovementPick)
//   4. GPT 가 학생 상황에 맞게 문장만 다듬습니다. 꺼짐 · 실패면 원래 문장을 그대로 씁니다.
//      다듬은 문장은 전략마다 검사해서, 숫자가 바뀌었거나 길거나 깎아내리는 말이면 그 전략은 원래 문장입니다. (StrategyPolish)
//   GPT 가 없어도 원래 문장 3개는 늘 나갑니다. (내 컴퓨터에서도 그대로 보입니다)
//
// 이 기능은 학생이 쓴 글을 받지 않아서 위기 표현 검사는 하지 않습니다. GPT 에게는 원인 이름 · 날 수 · 요일 · 전략 문장만 줍니다.
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽습니다.
// GPT 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (읽기는 각 Service 가 따로 합니다)
@Service
@RequiredArgsConstructor
public class ImprovementService {

  private final CurrentUserProvider currentUserProvider;
  private final MissReasonService missReasonService;
  private final OnboardingService onboardingService;
  private final AiClient aiClient;
  private final Clock clock;

  public ImprovementSuggestionsResponse suggest() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 최근 30일 실패 원인 (날 수가 많은 것부터 옵니다)
    List<ImprovementPick.RecentReason> recent = new ArrayList<>();
    for (MissReasonStatsResponse.Reason reason :
        missReasonService.getMissReasons().last30Days().reasons()) {
      recent.add(
          new ImprovementPick.RecentReason(
              MissReason.fromValue(reason.reason()), reason.dayCount(), reason.weekdayCounts()));
    }

    // 2) · 3) 원인 고르기 (최근 기록이 모자랄 때만 온보딩 답을 씁니다)
    ImprovementPick.Pick pick = ImprovementPick.causeOf(recent, Set.of());
    if (pick == null) {
      pick = ImprovementPick.causeOf(recent, onboardingCauses());
    }
    if (pick == null) {
      return ImprovementSuggestionsResponse.none();
    }
    List<Strategy> strategies =
        ImprovementPick.strategiesOf(pick.cause(), DayRule.dateOf(clock.instant()));

    // 4) 문장 다듬기
    Optional<StrategyPolish.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            StrategyPolish.question(pick.situation(), strategies),
            StrategyPolish.Answer.class);
    List<StrategyPolish.Polished> polished = StrategyPolish.from(answer.orElse(null), strategies);

    List<ImprovementSuggestionsResponse.Suggestion> suggestions = new ArrayList<>();
    for (int i = 0; i < strategies.size(); i++) {
      Strategy strategy = strategies.get(i);
      StrategyPolish.Polished text = polished.get(i);
      suggestions.add(
          new ImprovementSuggestionsResponse.Suggestion(
              strategy.key(),
              strategy.level().toValue(),
              text.ifText(),
              text.thenText(),
              text.polished(),
              new ImprovementSuggestionsResponse.Original(strategy.ifText(), strategy.thenText())));
    }
    return new ImprovementSuggestionsResponse(
        pick.cause().toValue(), pick.basis().name().toLowerCase(), suggestions);
  }

  // 온보딩 설문의 "계획이 주로 언제 무너졌나". 설문을 아직 안 했으면(404) 빈 목록입니다.
  private Set<FailureCause> onboardingCauses() {
    Set<FailureCause> causes = EnumSet.noneOf(FailureCause.class);
    try {
      for (String value : onboardingService.get().pastFailureCauses()) {
        FailureCause cause = FailureCause.fromValue(value);
        if (cause != null) {
          causes.add(cause);
        }
      }
    } catch (ApiException e) {
      // 설문을 안 한 사람은 404 입니다. 그때는 과거 실패 경험이 없는 것으로 봅니다. 다른 에러는 그대로 던집니다.
      if (e.getErrorCode() != ErrorCode.NOT_FOUND) {
        throw e;
      }
    }
    return causes;
  }
}
