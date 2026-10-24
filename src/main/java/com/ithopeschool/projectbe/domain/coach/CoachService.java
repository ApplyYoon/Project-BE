package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.CoachCommentRequest;
import com.ithopeschool.projectbe.domain.coach.dto.CoachParagraphResponse;
import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
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
//   3. 같은 숫자로 받아 둔 문단이 있는지 본다 (있으면 그 문단. GPT 를 부르지 않습니다)
//   4. GPT 를 부른다고 기록한다           (GptUsageGuard 가 DB 에 남깁니다, 이슈 #33)
//   5. 숫자만 주고 문단을 써 달라고 한다
//   6. 받은 문단을 검사한다                (지어낸 숫자·깎아내리는 말이 있으면 문단 없음)
//   7. 통과한 문단을 기억해 둔다           (CoachParagraphStore)
//
// 안전 브레이크가 켜져 있으면 사용자가 정한 한도와 상관없이 위로하는 말투만 씁니다. (이슈 #87)
// 켜졌는지는 FE 가 보낸 safetyBrakeOn 과, 서버의 보호 조치 기록(SafetyService.isActive: 최근 7일 안에 위기 표현이
// 감지된 체크인 · 통화 · 채팅이 있었는지, 이슈 #56) 중 하나라도 켜져 있으면 켜진 것으로 봅니다.
//
// "문단 없음" 은 에러가 아닙니다. { "paragraph": null } 을 돌려주면 FE 가 기본 문장을 보여줍니다.
// ⚠️ 받은 숫자와 AI 가 쓴 글은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CoachService {

  // 어제 한 줄·월간은 요청에 "어느 기간인지" 가 없어서, 기억해 둘 때 기간 자리를 비워 둡니다.
  private static final String NO_PERIOD = "";

  private final CurrentUserProvider currentUserProvider;
  private final CoachAiClient coachAiClient;
  // GPT 를 부른 것을 기록합니다. (이슈 #33)
  private final GptUsageGuard gptUsageGuard;
  private final CoachParagraphStore coachParagraphStore;
  // 서버에 남은 위기 보호 조치 기록을 봅니다. FE 가 기기에만 있는 기록으로 판정한 것을 한 번 더 확인합니다.
  private final SafetyService safetyService;

  // 주간 리포트의 코치 한마디
  public CoachParagraphResponse weeklyParagraph(WeeklyCoachRequest request) {
    return paragraph(
        CoachKind.WEEKLY,
        request.mondayKey(),
        request.numbers(),
        request.maxCareLevel(),
        request.safetyBrakeOn(),
        request.maxHarshness());
  }

  // 홈의 "어제 한 줄"
  public CoachParagraphResponse dailyParagraph(CoachCommentRequest request) {
    return paragraph(
        CoachKind.DAILY,
        NO_PERIOD,
        request.numbers(),
        request.maxCareLevel(),
        request.safetyBrakeOn(),
        request.maxHarshness());
  }

  // 월간 리포트의 코치 한마디
  public CoachParagraphResponse monthlyParagraph(CoachCommentRequest request) {
    return paragraph(
        CoachKind.MONTHLY,
        NO_PERIOD,
        request.numbers(),
        request.maxCareLevel(),
        request.safetyBrakeOn(),
        request.maxHarshness());
  }

  // 말투를 정할 때 쓸 관리 강도 한도를 돌려줍니다.
  // 안전 브레이크가 켜져 있으면 한도가 몇이든 0(회복)으로 봅니다. 그러면 말투 규칙이 "위로하는 말만" 이 됩니다.
  // (안전 브레이크는 어떤 설정보다 먼저입니다. CLAUDE.md 4장)
  //   한도 3, 브레이크 꺼짐  →  3
  //   한도 3, 브레이크 켜짐  →  0
  private static int careLevelLimit(int maxCareLevel, boolean safetyBrakeOn) {
    return safetyBrakeOn ? 0 : maxCareLevel;
  }

  // 어느 종류든 같은 순서로 처리합니다. 달라지는 것은 AI 에게 주는 지침(기간·문장 수·권하는 행동)뿐입니다.
  // periodKey: 어느 기간의 문단인지. 주간은 그 주의 월요일, 기간을 받지 않는 종류는 빈 글자입니다.
  private CoachParagraphResponse paragraph(
      CoachKind kind,
      String periodKey,
      List<WeeklyCoachRequest.Number> numbers,
      int requestedMaxCareLevel,
      Boolean safetyBrakeOn,
      int maxHarshness) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();

    if (!coachAiClient.isEnabled()) {
      return CoachParagraphResponse.none(numbers);
    }

    // FE 가 보낸 값(안 보냈으면 꺼짐) 또는 서버의 보호 조치 기록 중 하나라도 켜져 있으면 위로하는 말투입니다.
    boolean brakeOn = Boolean.TRUE.equals(safetyBrakeOn) || safetyService.isActive(userId);
    int maxCareLevel = careLevelLimit(requestedMaxCareLevel, brakeOn);

    // 같은 숫자·같은 한도로 받아 둔 문단이 있으면 그대로 돌려줍니다. GPT 를 부르지 않습니다.
    // signature 는 "이번에 받은 숫자와 한도" 를 글자로 이어 붙인 것입니다. 하나라도 달라지면 다른 글자가 됩니다.
    String signature = numbers + "|" + maxCareLevel + "|" + maxHarshness;
    Optional<String> saved = coachParagraphStore.find(userId, kind, periodKey, signature);
    if (saved.isPresent()) {
      return new CoachParagraphResponse(saved.get(), numbers);
    }

    gptUsageGuard.recordText(userId, GptUsageKind.COACH);

    Optional<String> paragraph =
        coachAiClient.ask(
            CoachPrompt.instructions(kind, maxCareLevel, maxHarshness),
            CoachPrompt.input(kind, numbers));

    if (paragraph.isEmpty() || !CoachParagraphCheck.isSafe(paragraph.get(), numbers)) {
      return CoachParagraphResponse.none(numbers);
    }
    // 검사를 통과한 문단만 기억합니다. 버린 문단은 기억하지 않아서, 다음에 다시 물어봅니다.
    coachParagraphStore.save(userId, kind, periodKey, signature, paragraph.get());
    return new CoachParagraphResponse(paragraph.get(), numbers);
  }
}
