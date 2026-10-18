package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CoachNudgeResponse;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "코치 알림 문장" 의 로직입니다. 그날 아침 · 밤에 코치가 먼저 말을 거는 알림 문장을 AI 가 미리 씁니다. (2026-10-18)
// ⚠️ 아무것도 저장하지 않고, 알림 문장을 로그에 찍지 않습니다. FE 가 받아서 휴대폰 알림으로 예약합니다.
//
// 순서
//   1. 날짜가 오늘 또는 내일이 아니면 400 (하루의 기준은 새벽 4시)
//   2. 안전이 먼저입니다. 아래 중 하나면 AI 를 부르지 않고 정해 둔 위로 문장을 줍니다. (comfort: true)
//      - 보호 조치 중 (SafetyService.isActive)
//      - 최근 7일(오늘 포함) 안에 위기 표현이 감지된 통화 · 채팅이 있음 (HabitNudgeJob · CallService 와 같은 규칙)
//      - 보낸 글(과목 · 카드 제목 · 주제 · 시험 이름 · 준비하는 것)에 위기 표현이 있음 (#56)
//   3. AI 를 부릅니다. 꺼짐 · 하루 한도 · 실패면 둘 다 null 입니다. (한도는 ASSIST)
//   4. 받은 문장을 검사합니다. (CoachNudge.from — 길이 · 말투 · 위기 표현 · 지어낸 숫자)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 한도는 사람마다 셉니다.
@Service
@RequiredArgsConstructor
public class CoachNudgeService {

  // 위기 표현이 있던 통화가 이 기간(일, 오늘 포함) 안에 있으면 위로 문장만 줍니다. (통화의 위로 기간과 같습니다)
  static final int CRISIS_PAUSE_DAYS = 7;

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;
  private final SafetyService safetyService;
  private final CallRecordRepository callRecordRepository;
  private final Clock clock;

  // AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (CardPhotoService 와 같음)
  public CoachNudgeResponse make(CoachNudgeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 날짜
    LocalDate today = DayRule.dateOf(clock.instant());
    if (!request.date().equals(today) && !request.date().equals(today.plusDays(1))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "알림 날짜는 오늘이나 내일로 보내 주세요");
    }

    // 2) 안전 (보호 조치 · 최근 위기 표현 · 보낸 글의 위기 표현)
    if (safetyService.isActive(userId)
        || hasRecentCrisis(userId, today)
        || CrisisText.hasCrisisText(textsOf(request))) {
      return CoachNudgeResponse.comforting();
    }

    // 3) AI → 4) 검사
    String input = CoachNudge.input(request);
    return aiClient
        .ask(userId, GptUsageKind.ASSIST, CoachNudge.question(request), CoachNudge.Answer.class)
        .map(answer -> CoachNudgeResponse.of(CoachNudge.from(answer, input)))
        .orElse(CoachNudgeResponse.notMade());
  }

  // 오늘을 넣어 최근 7일 안에 위기 표현이 감지된 통화 · 채팅이 있으면 true
  private boolean hasRecentCrisis(Long userId, LocalDate today) {
    List<CallRecord> recentCalls =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, today.minusDays(CRISIS_PAUSE_DAYS - 1));
    for (CallRecord call : recentCalls) {
      if (call.isCrisisDetected()) {
        return true;
      }
    }
    return false;
  }

  // 위기 표현을 찾아볼 글들 (학생이 쓴 것일 수 있는 칸만)
  private static List<String> textsOf(CoachNudgeRequest request) {
    List<String> texts = new ArrayList<>(request.subjects());
    for (CoachNudgeRequest.Card card : request.cards()) {
      texts.add(card.title());
    }
    texts.addAll(request.wrongQuizTopics());
    if (request.exam() != null) {
      texts.add(request.exam().name());
    }
    if (request.focus() != null && request.focus().name() != null) {
      texts.add(request.focus().name());
    }
    return texts;
  }
}
