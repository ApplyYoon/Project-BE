package com.ithopeschool.projectbe.domain.goalchat;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.call.ChatAiClient;
import com.ithopeschool.projectbe.domain.call.ChatService;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.goalchat.dto.GoalChatFinishResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// '목표 만들기' 대화 로직입니다. 상담 채팅 안에서 WOOP 순서로 목표 하나를 세웁니다. (이슈 #239, 기획서 「4.7 목표와 숲」)
//
//   1. 열기 (start)      정해 둔 첫 질문으로 시작합니다. (GPT 를 부르지 않음)
//   2. 말 보내기 (send)  학생의 말을 모두 위기 표현 검사 → 걸리면 GPT 를 부르지 않고 도움 안내로 끝냄
//                        아니면 지금까지의 대화를 주고 AI 의 다음 질문을 받습니다. (ChatAiClient, 한도는 상담 채팅 CHAT)
//   3. 끝내기 (finish)   대화를 읽고 목표 초안(goalDraft)을 돌려줍니다. (AiClient, 한도는 작은 AI 기능 묶음 ASSIST)
//                        FE 가 목표 저장 화면(POST /api/v1/goals, #221)에 채워 학생에게 확인받고 저장합니다.
//
// 상담 채팅(ChatService)과 다른 점: 통화 기록(call_records) · 요약 · 체크인을 남기지 않습니다. 목표만 만드는 짧은 대화라서입니다.
// 모든 메서드는 로그인한 사람의 번호(CurrentUserProvider)로 시작합니다. 남의 대화 번호로는 이어갈 수 없습니다(404).
//
// ⚠️ 대화 원문과 초안은 저장하지 않고 로그에도 찍지 않습니다. 학생이 확인한 목표만 #221 이 저장합니다. (CLAUDE.md 4장)
@Service
@RequiredArgsConstructor
public class GoalChatService {

  // AI 의 답에 깎아내리는 말이 섞였을 때 대신 돌려주는 말입니다. (CLAUDE.md 4장: 비하 · 비교 금지)
  static final String SAFE_REPLY = "내가 방금 한 말은 다시 할게. 이루고 싶은 목표를 한 줄로 말해 줄래?";

  private final CurrentUserProvider currentUserProvider;
  private final ChatAiClient chatAiClient;
  private final AiClient aiClient;
  private final GptUsageGuard gptUsageGuard;
  private final GoalChatStore goalChatStore;
  // 위기 표현이 감지되면 상담 채팅과 같은 보호 조치를 합니다. (관리 한도 0 · 앱 잠금 끄기 · 회복 메시지, 이슈 #56)
  private final SafetyService safetyService;
  private final Clock clock;

  // 대화를 엽니다. AI 가 꺼진 서버(내 컴퓨터)에서는 이어갈 수 없으니 여기서 503 입니다.
  public ChatReplyResponse start() {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다.
    Long userId = currentUserProvider.getCurrentUserId();
    if (!chatAiClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 목표 대화가 아직 켜져 있지 않아요");
    }
    String chatId = goalChatStore.open(userId);
    return ChatReplyResponse.of(chatId, GoalChatPrompt.OPENING);
  }

  // 학생이 말을 보냅니다. lines 는 지금까지의 대화 전체이고, 맨 끝이 방금 학생이 쓴 말입니다.
  public ChatReplyResponse send(String chatId, ChatMessageRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkOpen(chatId, userId);

    List<CallFinishRequest.Line> lines = request.lines();
    if (!"me".equals(lines.get(lines.size() - 1).speaker())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "마지막 줄은 학생(me)의 말이어야 해요");
    }

    // 위기 표현은 학생이 쓴 모든 줄에서 봅니다. 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
    if (CrisisText.hasCrisisText(studentTextsOf(lines))) {
      closeWithCrisis(chatId, userId);
      return ChatReplyResponse.crisis(chatId, ChatService.CRISIS_REPLY);
    }

    if (!chatAiClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 목표 대화가 아직 켜져 있지 않아요");
    }
    if (!gptUsageGuard.tryUseText(userId, GptUsageKind.CHAT)) {
      throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "오늘 AI 사용량을 다 썼어요. 목표는 직접 써 볼까요?");
    }
    LocalDate today = DayRule.dateOf(clock.instant());
    Optional<String> reply = chatAiClient.reply(GoalChatPrompt.instructions(today), lines);
    if (reply.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    String text = ToneGuide.hasBannedExpression(reply.get()) ? SAFE_REPLY : reply.get();
    return ChatReplyResponse.of(chatId, text);
  }

  // 대화를 끝내고 목표 초안을 받습니다. 초안을 못 만들어도 에러가 아니라 goalDraft 가 null 입니다.
  public GoalChatFinishResponse finish(String chatId, ChatMessageRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkOpen(chatId, userId);
    List<CallFinishRequest.Line> lines = request.lines();

    if (CrisisText.hasCrisisText(studentTextsOf(lines))) {
      closeWithCrisis(chatId, userId);
      return GoalChatFinishResponse.help();
    }

    goalChatStore.close(chatId);
    LocalDate today = DayRule.dateOf(clock.instant());
    // AiClient 가 꺼짐 · 하루 한도 · 실패를 모두 빈 값으로 돌려줍니다.
    Optional<GoalChatPrompt.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            GoalChatPrompt.draftQuestion(lines, today),
            GoalChatPrompt.Answer.class);
    return GoalChatFinishResponse.of(
        answer.map(found -> GoalDraft.from(found, today)).orElse(null));
  }

  // 내가 연 대화이고 아직 열려 있는지. 없거나 · 남의 것이거나 · 2시간이 지났으면 404 입니다.
  private void checkOpen(String chatId, Long userId) {
    if (!goalChatStore.isOpen(chatId, userId)) {
      throw new ApiException(ErrorCode.NOT_FOUND, "목표 대화를 찾을 수 없어요. 새로 시작해 주세요");
    }
  }

  // 위기 표현이 감지되면 대화를 닫고 보호 조치를 합니다. 그 뒤 7일 동안 통화 · 채팅은 위로하는 쪽이 됩니다. (SafetyService)
  private void closeWithCrisis(String chatId, Long userId) {
    goalChatStore.close(chatId);
    safetyService.activate(userId);
  }

  private static List<String> studentTextsOf(List<CallFinishRequest.Line> lines) {
    List<String> texts = new ArrayList<>();
    for (CallFinishRequest.Line line : lines) {
      if ("me".equals(line.speaker())) {
        texts.add(line.text());
      }
    }
    return texts;
  }
}
