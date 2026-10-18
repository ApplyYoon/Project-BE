package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.school.SchoolTodayService;
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
//   1. 채팅 열기 (start)     통화와 같은 순서로 대본을 만들고(CallService.prepareScript, 학생 카드는 DB),
//                           오늘 학교 정보(시간표 · 급식 · 학사일정)를 붙여 AI 의 첫 인사를 받는다
//   2. 말 보내기 (send)      학생의 말을 위기 표현 검사 → 걸리면 GPT 를 부르지 않고 정해 둔 안내로 답한다
//                           채팅은 끝내지 않고 이어 갈 수 있다 (2026-10-18). 보호 조치는 이 채팅에서 한 번만 한다
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
  // '목표 만들기' 대화(GoalChatService)는 이 문장을 쓰고 대화를 끝냅니다.
  public static final String CRISIS_REPLY =
      "말해 줘서 고마워. 지금 많이 힘들구나. 공부 이야기는 여기서 멈출게."
          + " 혼자 견디지 말고 믿을 수 있는 어른에게 꼭 이야기해 줘. 지금 바로 이야기할 수 있는 곳도 아래에 있어.";

  // 상담 채팅은 위기 표현이 나와도 끝내지 않습니다. 그래서 "이어서 이야기해도 된다" 는 말을 붙입니다. (2026-10-18)
  public static final String CHAT_CRISIS_REPLY =
      "말해 줘서 고마워. 지금 많이 힘들구나. 공부 이야기는 잠시 내려놓자."
          + " 혼자 견디지 말고 믿을 수 있는 어른에게 꼭 이야기해 줘. 지금 바로 이야기할 곳은 아래에 있어."
          + " 나랑도 계속 이야기해도 괜찮아.";

  // 위기 표현이 나온 뒤의 대본에 덧붙이는 줄입니다. 남은 대화에서 공부 이야기를 꺼내지 않게 합니다.
  static final String CRISIS_FOLLOW_UP =
      "\n\n[지금]\n- 학생이 방금 힘든 마음을 말했어. 남은 대화에서는 공부 · 계획 · 숫자 이야기를 꺼내지 말고"
          + " 들어 주기만 해. 학생이 원하면 청소년상담 1388(24시간)에 이야기할 수 있다고 다시 알려 줘.";

  // AI 의 답에 깎아내리는 말이 섞였을 때 대신 돌려주는 말입니다. (CLAUDE.md 4장: 비하 · 비교 금지)
  static final String SAFE_REPLY = "내가 방금 한 말은 다시 할게. 오늘 하나만 해 본다면 무엇부터 할 것 같아?";

  // 빠른 답 버튼은 이만큼까지, 한 버튼은 이 글자 수까지
  static final int MAX_QUICK_REPLIES = 3;
  static final int MAX_QUICK_REPLY_LENGTH = 20;

  private final CurrentUserProvider currentUserProvider;
  private final ChatAiClient chatAiClient;
  private final GptUsageGuard gptUsageGuard;
  // 통화와 같은 대본을 만듭니다. (안전 브레이크 · DB 학생 카드 · 생각 습관 테스트 · 지난 통화)
  private final CallService callService;
  private final CallRecordService callRecordService;
  private final CallRecordRepository callRecordRepository;
  private final ChatScriptStore chatScriptStore;
  // 위기 표현이 감지되면 통화 마무리와 같은 보호 조치를 합니다. (관리 한도 0 · 앱 잠금 끄기 · 회복 메시지, 이슈 #56)
  private final SafetyService safetyService;
  // 대본에 붙일 오늘 학교 정보 (시간표 · 급식 · 학사일정)
  private final SchoolTodayService schoolTodayService;
  // 대본에 붙일 학생 성향 (가입 때 한 성향 검사, 이슈 #219)
  private final PsychTestRepository psychTestRepository;
  private final Clock clock;

  // 채팅을 엽니다. 요청 모양은 통화 시작(POST /realtime/session)과 같습니다. (학년 · 반 없이)
  public ChatReplyResponse start(CallSessionRequest request) {
    return start(request, null, null);
  }

  // 채팅을 엽니다. grade · classNo 를 주면 그 반의 오늘 시간표도 대본에 넣습니다.
  public ChatReplyResponse start(CallSessionRequest request, Integer grade, Integer classNo) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();
    checkAiReady(userId);

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    CallService.Script script =
        callService.prepareScript(userId, request, now, today, CallChannel.CHAT);

    // 통화와 같은 대본 끝에 "학생 성향" · "오늘 학교" 칸을 붙입니다. 없으면 붙이지 않습니다.
    // 성향은 학생마다 말 거는 방법을 다르게 하려고 넣습니다. (시작을 미루는 학생에게는 '5분만' 처럼)
    // 답이 거의 다 같아서 믿기 어려운 검사면 점수 대신 "대화로 하나만 확인해" 칸을 붙입니다. (2026-10-18)
    String psychPart =
        psychTestRepository
            .findFirstByUserIdOrderByIdDesc(userId)
            .map(
                test ->
                    test.isReliable()
                        ? CallScript.psychSection(PsychScore.scoresOf(test.getAnswers()))
                        : CallScript.unsurePsychSection())
            .orElse("");
    String instructions =
        script.instructions()
            + psychPart
            + CallScript.schoolSection(schoolTodayService.scriptLines(grade, classNo));
    ChatAiClient.ChatTurn first = askAi(instructions, List.of());
    // AI 의 첫 인사를 받은 뒤에 기록을 만듭니다. 못 받았으면 채팅이 열리지 않은 것이라 기록도 남기지 않습니다.
    CallRecord record =
        callRecordRepository.save(
            new CallRecord(userId, today, now, script.biasQuestion(), CallChannel.CHAT));
    chatScriptStore.save(record.getId(), userId, instructions);
    return ChatReplyResponse.withButtons(
        record.getId(), first.reply(), first.quickReplies(), first.action());
  }

  // 학생이 말을 보냅니다. lines 는 지금까지의 대화 전체이고, 맨 끝이 방금 학생이 쓴 말입니다.
  public ChatReplyResponse send(Long chatId, ChatMessageRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    CallRecord chat = findOpenChat(userId, chatId);

    List<CallFinishRequest.Line> lines = request.lines();
    if (!"me".equals(lines.get(lines.size() - 1).speaker())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "마지막 줄은 학생(me)의 말이어야 해요");
    }

    // 위기 표현 검사는 어떤 설정보다 먼저입니다. (CLAUDE.md 4장)
    //   - 아직 이 채팅에서 감지한 적이 없으면 학생이 쓴 모든 줄을 봅니다.
    //   - 이미 감지해서 안내했으면 방금 쓴 마지막 줄만 봅니다. (앞의 줄로 매번 다시 걸리지 않게)
    // 걸리면 GPT 를 부르지 않고 정해 둔 안내로 답합니다. 채팅은 끝내지 않습니다. (2026-10-18)
    List<String> studentTexts = new ArrayList<>();
    if (chat.isCrisisDetected()) {
      studentTexts.add(lines.get(lines.size() - 1).text());
    } else {
      for (CallFinishRequest.Line line : lines) {
        if ("me".equals(line.speaker())) {
          studentTexts.add(line.text());
        }
      }
    }
    if (CrisisText.hasCrisisText(studentTexts)) {
      return replyToCrisis(chat, userId);
    }

    String instructions =
        chatScriptStore
            .find(chatId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "채팅이 오래되어 끝났어요. 새로 시작해 주세요"));
    checkAiReady(userId);
    ChatAiClient.ChatTurn next = askAi(instructions, lines);
    return ChatReplyResponse.withButtons(chatId, next.reply(), next.quickReplies(), next.action());
  }

  // 위기 표현이 감지됐을 때. 보호 조치(관리 한도 0 · 회복 메시지)는 이 채팅에서 처음 감지했을 때 한 번만 합니다.
  // 그 뒤 7일 동안 통화 · 채팅은 위로하는 쪽이 됩니다. (CallService 가 crisisDetected 를 봅니다)
  // 남은 대화에서 공부 이야기를 꺼내지 않게 기억해 둔 대본에 한 칸을 덧붙입니다.
  private ChatReplyResponse replyToCrisis(CallRecord chat, Long userId) {
    if (!chat.isCrisisDetected()) {
      chat.markCrisis();
      callRecordRepository.save(chat);
      safetyService.activate(userId);
      chatScriptStore
          .find(chat.getId(), userId)
          .ifPresent(
              instructions ->
                  chatScriptStore.save(chat.getId(), userId, instructions + CRISIS_FOLLOW_UP));
    }
    return ChatReplyResponse.crisis(chat.getId(), CHAT_CRISIS_REPLY);
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

  // AI 의 다음 말과 버튼을 받습니다. 못 받았으면 503 입니다. 받은 것은 cleanTurn 으로 검사합니다.
  private ChatAiClient.ChatTurn askAi(String instructions, List<CallFinishRequest.Line> lines) {
    Optional<ChatAiClient.ChatTurn> turn = chatAiClient.turn(instructions, lines);
    if (turn.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    return cleanTurn(turn.get());
  }

  // AI 가 준 답을 검사합니다. (2026-10-18 빠른 답 · 행동 버튼)
  //   - 말에 깎아내리는 말이 섞였으면 정해 둔 말로 바꾸고 버튼은 모두 뺍니다.
  //   - 빠른 답: 비었거나 20자가 넘거나 깎아내리는 말이 있으면 빼고, 같은 것은 하나만, 3개까지.
  //   - 행동: cleanAction 의 규칙에 맞을 때만 넣습니다.
  static ChatAiClient.ChatTurn cleanTurn(ChatAiClient.ChatTurn turn) {
    if (ToneGuide.hasBannedExpression(turn.reply())) {
      return ChatAiClient.ChatTurn.textOnly(SAFE_REPLY);
    }
    List<String> quickReplies = new ArrayList<>();
    for (String quickReply : turn.quickReplies()) {
      String text = quickReply == null ? "" : quickReply.trim();
      boolean usable =
          !text.isEmpty()
              && text.length() <= MAX_QUICK_REPLY_LENGTH
              && !ToneGuide.hasBannedExpression(text)
              && !quickReplies.contains(text);
      if (usable) {
        quickReplies.add(text);
      }
      if (quickReplies.size() == MAX_QUICK_REPLIES) {
        break;
      }
    }
    return new ChatAiClient.ChatTurn(turn.reply(), quickReplies, cleanAction(turn.action()));
  }

  // 행동 버튼 검사. 맞지 않으면 null(버튼 없음)입니다.
  //   start_timer  : 숫자 · 제목 없이
  //   shrink_today : 5 ~ 60 분
  //   create_card  : 제목 1 ~ 20자(깎아내리는 말 없음), 분은 5 ~ 120 (없거나 벗어나면 25분)
  static ChatReplyResponse.Action cleanAction(ChatReplyResponse.Action action) {
    if (action == null || action.type() == null) {
      return null;
    }
    switch (action.type()) {
      case "start_timer":
        return new ChatReplyResponse.Action("start_timer", null, null);
      case "shrink_today":
        Integer minutes = action.minutes();
        return minutes != null && minutes >= 5 && minutes <= 60
            ? new ChatReplyResponse.Action("shrink_today", minutes, null)
            : null;
      case "create_card":
        String title = action.title() == null ? "" : action.title().trim();
        if (title.isEmpty() || title.length() > 20 || ToneGuide.hasBannedExpression(title)) {
          return null;
        }
        Integer cardMinutes = action.minutes();
        int safeMinutes =
            cardMinutes != null && cardMinutes >= 5 && cardMinutes <= 120 ? cardMinutes : 25;
        return new ChatReplyResponse.Action("create_card", safeMinutes, title);
      default:
        return null;
    }
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
