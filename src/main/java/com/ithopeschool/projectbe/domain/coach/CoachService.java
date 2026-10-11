package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 주간 리포트 맨 위의 "코치 한 문단" 을 AI 에게 부탁하는 로직입니다. (이슈 #87 · FE src/lib/weeklyCoach.ts)
//
// 순서
//   1. 로그인한 사람인지 확인한다          (아니면 401)
//   2. AI 를 쓸 수 있는 서버인지 본다      (내 컴퓨터에서는 꺼져 있습니다 → 문단 없음)
//   3. 오늘 부른 횟수가 한도 안인지 본다   (넘었으면 문단 없음)
//   4. 숫자만 주고 문단을 써 달라고 한다
//   5. 받은 문단을 검사한다                (지어낸 숫자·깎아내리는 말이 있으면 문단 없음)
//
// "문단 없음" 은 에러가 아닙니다. { "paragraph": null } 을 돌려주면 FE 가 기본 문장을 보여줍니다.
// ⚠️ 받은 숫자와 AI 가 쓴 글은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CoachService {

  private final CurrentUserProvider currentUserProvider;
  private final CoachAiClient coachAiClient;
  private final CoachDailyLimit coachDailyLimit;

  public WeeklyCoachResponse weeklyParagraph(WeeklyCoachRequest request) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();

    if (!coachAiClient.isEnabled()) {
      return WeeklyCoachResponse.none();
    }
    if (!coachDailyLimit.tryUse(userId)) {
      return WeeklyCoachResponse.none();
    }

    Optional<String> paragraph =
        coachAiClient.ask(
            CoachPrompt.instructions(request.maxCareLevel(), request.maxHarshness()),
            CoachPrompt.input(request.numbers()));

    if (paragraph.isEmpty() || !CoachParagraphCheck.isSafe(paragraph.get(), request.numbers())) {
      return WeeklyCoachResponse.none();
    }
    return new WeeklyCoachResponse(paragraph.get());
  }
}
