package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.CoachCommentRequest;
import com.ithopeschool.projectbe.domain.coach.dto.CoachParagraphResponse;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 숫자를 주고 "코치 한마디" 를 AI 에게 부탁하는 로직입니다. (이슈 #87 · FE src/lib/weeklyCoach.ts 등)
// 어제 한 줄(홈) · 주간 리포트 · 월간 리포트, 세 자리가 같이 씁니다.
//
// 순서
//   1. 로그인한 사람인지 확인한다          (아니면 401)
//   2. AI 를 쓸 수 있는 서버인지 본다      (내 컴퓨터에서는 꺼져 있습니다 → 문단 없음)
//   3. 오늘 부른 횟수가 한도 안인지 본다   (넘었으면 문단 없음)
//   4. 숫자만 주고 문단을 써 달라고 한다
//   5. 받은 문단을 검사한다                (지어낸 숫자·깎아내리는 말이 있으면 문단 없음)
//
// 안전 브레이크가 켜져 있으면(safetyBrakeOn) 사용자가 정한 한도와 상관없이 위로하는 말투만 씁니다.
//
// "문단 없음" 은 에러가 아닙니다. { "paragraph": null } 을 돌려주면 FE 가 기본 문장을 보여줍니다.
// ⚠️ 받은 숫자와 AI 가 쓴 글은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CoachService {

  private final CurrentUserProvider currentUserProvider;
  private final CoachAiClient coachAiClient;
  private final CoachDailyLimit coachDailyLimit;

  // 주간 리포트의 코치 한마디
  public CoachParagraphResponse weeklyParagraph(WeeklyCoachRequest request) {
    return paragraph(
        CoachKind.WEEKLY,
        request.numbers(),
        careLevelLimit(request.maxCareLevel(), request.safetyBrakeOn()),
        request.maxHarshness());
  }

  // 홈의 "어제 한 줄"
  public CoachParagraphResponse dailyParagraph(CoachCommentRequest request) {
    return paragraph(
        CoachKind.DAILY,
        request.numbers(),
        careLevelLimit(request.maxCareLevel(), request.safetyBrakeOn()),
        request.maxHarshness());
  }

  // 월간 리포트의 코치 한마디
  public CoachParagraphResponse monthlyParagraph(CoachCommentRequest request) {
    return paragraph(
        CoachKind.MONTHLY,
        request.numbers(),
        careLevelLimit(request.maxCareLevel(), request.safetyBrakeOn()),
        request.maxHarshness());
  }

  // 말투를 정할 때 쓸 관리 강도 한도를 돌려줍니다.
  // 안전 브레이크가 켜져 있으면 한도가 몇이든 0(회복)으로 봅니다. 그러면 말투 규칙이 "위로하는 말만" 이 됩니다.
  // (안전 브레이크는 어떤 설정보다 먼저입니다. CLAUDE.md 4장)
  //   한도 3, 브레이크 꺼짐(false 또는 안 보냄)  →  3
  //   한도 3, 브레이크 켜짐                      →  0
  private static int careLevelLimit(int maxCareLevel, Boolean safetyBrakeOn) {
    return Boolean.TRUE.equals(safetyBrakeOn) ? 0 : maxCareLevel;
  }

  // 어느 종류든 같은 순서로 처리합니다. 달라지는 것은 AI 에게 주는 지침(기간·문장 수·권하는 행동)뿐입니다.
  private CoachParagraphResponse paragraph(
      CoachKind kind, List<WeeklyCoachRequest.Number> numbers, int maxCareLevel, int maxHarshness) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();

    if (!coachAiClient.isEnabled()) {
      return CoachParagraphResponse.none();
    }
    if (!coachDailyLimit.tryUse(userId)) {
      return CoachParagraphResponse.none();
    }

    Optional<String> paragraph =
        coachAiClient.ask(
            CoachPrompt.instructions(kind, maxCareLevel, maxHarshness),
            CoachPrompt.input(kind, numbers));

    if (paragraph.isEmpty() || !CoachParagraphCheck.isSafe(paragraph.get(), numbers)) {
      return CoachParagraphResponse.none();
    }
    return new CoachParagraphResponse(paragraph.get());
  }
}
