package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 상담 채팅 로직입니다. "글로 하는 통화" 입니다. (이슈 #238, 기획서 4.5 「상담」)
// 통화를 못 받은 날에도 같은 흐름이 이어지도록, 통화와 같은 대본(CallScript)과 같은 기록(call_records)을 씁니다.
//
//   1. 채팅 열기 (start)     통화와 같은 순서로 대본을 만들고(CallService.prepareScript, 학생 카드는 DB), AI 의 첫 인사를 받는다
//   2. 말 보내기 (send)      학생의 말을 모두 위기 표현 검사 → 걸리면 GPT 를 부르지 않고 정해 둔 안내로 끝냄
//                           아니면 지금까지의 대화를 주고 AI 의 다음 말을 받는다
//   3. 끝내기 (finish)       통화 마무리(CallRecordService.finish)와 똑같이 요약 · 생각 습관 · 체크인을 저장한다
//
// AI 의 답은 한 번마다 GPT 글 호출 하루 한도(GptUsageGuard, 이슈 #33)를 하나씩 씁니다.
// 모든 메서드는 로그인한 사람의 번호(CurrentUserProvider)로 시작하고, 내 채팅만 찾습니다. 남의 채팅 번호는 404 입니다.
//
// ⚠️ 대화 원문은 저장하지 않습니다. FE 가 들고 있다가 매번 보내고, 서버는 답을 받는 데만 씁니다. 로그에도 찍지 않습니다.
// @Transactional 을 붙이지 않은 이유: GPT 를 최대 15초 기다리는 동안 DB 연결을 붙잡지 않으려고. (CallRecordService.finish 와 같음)
@Service
@RequiredArgsConstructor
public class ChatService {

  // 학생의 말에서 위기 표현이 감지됐을 때 AI 대신 돌려주는 정해 둔 말입니다. 도움받을 곳은 응답의 helpLines 에 따로 담습니다.
  // ⚠️ 문구와 연결처는 전문가 검토(Tmax-IT-Hope-School/Project#8) 뒤에 확정합니다. (HelpLine 과 같음)
  static final String CRISIS_REPLY =
      "말해 줘서 고마워. 지금 많이 힘들구나. 공부 이야기는 여기서 멈출게."
          + " 혼자 견디지 말고 믿을 수 있는 어른에게 꼭 이야기해 줘. 지금 바로 이야기할 수 있는 곳도 아래에 있어.";

  // AI 의 답에 깎아내리는 말이 섞였을 때 대신 돌려주는 말입니다. (CLAUDE.md 4장: 비하 · 비교 금지)
  static final String SAFE_REPLY = "내가 방금 한 말은 다시 할게. 오늘 하나만 해 본다면 무엇부터 할 것 같아?";

  private final CurrentUserProvider currentUserProvider;
  private final ChatAiClient chatAiClient;
  private final GptUsageGuard gptUsageGuard;
  // 통화와 같은 대본을 만듭니다. (안전 브레이크 · DB 학생 카드 · 생각 습관 테스트 · 지난 통화)
  private final CallService callService;
  private final CallRecordService callRecordService;
  private final CallRecordRepository callRecordRepository;
  private final ChatScriptStore chatScriptStore;
  private final Clock clock;

  // 채팅을 엽니다. 요청 모양은 통화 시작(POST /realtime/session)과 같습니다.
  public ChatReplyResponse start(CallSessionRequest request) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();
    checkAiReady(userId);

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    CallService.Script script =
        callService.prepareScript(userId, request, now, today, CallChannel.CHAT);

    String reply = askAi(script.instructions(), List.of());
    // AI 의 첫 인사를 받은 뒤에 기록을 만듭니다. 못 받았으면 채팅이 열리지 않은 것이라 기록도 남기지 않습니다.
    CallRecord record =
        callRecordRepository.save(
            new CallRecord(userId, today, now, script.biasQuestion(), CallChannel.CHAT));
    chatScriptStore.save(record.getId(), userId, script.instructions());
    return ChatReplyResponse.of(record.getId(), reply);
  }

  // 학생이 말을 보냅니다. lines 는 지금까지의 대화 전체이고, 맨 끝이 방금 학생이 쓴 말입니다.
  public ChatReplyResponse send(Long chatId, ChatMessageRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    CallRecord chat = findOpenChat(userId, chatId);

    List<CallFinishRequest.Line> lines = request.lines();
    if (!"me".equals(lines.get(lines.size() - 1).speaker())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "마지막 줄은 학생(me)의 말이어야 해요");
    }

    // 위기 표현은 학생이 쓴 모든 줄에서 봅니다. 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
    // 걸리면 GPT 를 부르지 않고, 채팅을 "위기 표현이 감지된 통화" 로 끝냅니다. 그 뒤 7일 동안 통화·채팅은 위로하는 쪽이 됩니다.
    List<String> studentTexts = new ArrayList<>();
    for (CallFinishRequest.Line line : lines) {
      if ("me".equals(line.speaker())) {
        studentTexts.add(line.text());
      }
    }
    if (CrisisText.hasCrisisText(studentTexts)) {
      chat.finishWithCrisis(clock.instant());
      callRecordRepository.save(chat);
      chatScriptStore.remove(chatId);
      return ChatReplyResponse.crisis(chatId, CRISIS_REPLY);
    }

    String instructions =
        chatScriptStore
            .find(chatId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "채팅이 오래되어 끝났어요. 새로 시작해 주세요"));
    checkAiReady(userId);
    return ChatReplyResponse.of(chatId, askAi(instructions, lines));
  }

  // 채팅을 끝냅니다. 통화 마무리와 똑같이 정리해서 저장합니다. (요약 카드 · 체크인, 이슈 #29)
  public CallFinishResponse finish(Long chatId, CallFinishRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    findOpenChat(userId, chatId);
    chatScriptStore.remove(chatId);
    return callRecordService.finish(chatId, request);
  }

  // AI 를 쓸 수 있는 서버인지, 오늘 한도가 남았는지 봅니다. 한도가 남았으면 한 번을 씁니다.
  private void checkAiReady(Long userId) {
    if (!chatAiClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 상담이 아직 켜져 있지 않아요");
    }
    if (!gptUsageGuard.tryUseText(userId, GptUsageKind.CHAT)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "오늘 AI 사용량을 다 썼어요. 내일 다시 이야기해요");
    }
  }

  // AI 의 다음 말을 받습니다. 못 받았으면 503, 깎아내리는 말이 섞였으면 정해 둔 말로 바꿉니다.
  private String askAi(String instructions, List<CallFinishRequest.Line> lines) {
    Optional<String> reply = chatAiClient.reply(instructions, lines);
    if (reply.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    return ToneGuide.hasBannedExpression(reply.get()) ? SAFE_REPLY : reply.get();
  }

  // 내 채팅 중 아직 끝나지 않은 것. 없거나 남의 것이거나 음성 통화면 404, 이미 끝났으면 409 입니다.
  private CallRecord findOpenChat(Long userId, Long chatId) {
    CallRecord record =
        callRecordRepository
            .findByIdAndUserId(chatId, userId)
            .filter(found -> found.getChannel() == CallChannel.CHAT)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "채팅을 찾을 수 없어요"));
    if (record.isFinished()) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 끝난 채팅이에요");
    }
    return record;
  }
}
