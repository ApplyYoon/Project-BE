package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.ai.CardPhotoService;
import com.ithopeschool.projectbe.domain.ai.LongPlan;
import com.ithopeschool.projectbe.domain.ai.StudyBlock;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatAbout;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumContext;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumService;
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
import java.time.format.DateTimeParseException;
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
// "코치랑 이야기하기" (2026-10-18): 열 때 주제(request.about — 공부 끝 후기 · 못 한 카드 · 주간 리포트 등)가 오면
// 대본 끝에 [이번 대화의 주제] 칸을 붙여 AI 가 그 주제로 첫 말을 꺼내게 하고, 주제를 채팅이 끝날 때까지 기억합니다.
// 후기 · 못 한 카드 주제면 AI 가 학생 말에서 짐작한 이유 값(tags)을 그 주제의 값만 남겨 돌려줍니다. (ChatTopic)
//
// 방 3개 (2026-10-18 "📅 계획 · 💬 상담 · 📚 과목 쌤"): 열 때 request.room 으로 방을 고릅니다. 없으면 상담(COACH)입니다.
// 방은 대본 끝에 [이번 방] 칸을 붙여 바꾸고(ChatRoom.section), 과목 쌤은 학생 성향 칸을 붙이지 않습니다.
//
// 사진 질문 (2026-10-18): 말 보내기에 사진(request.image)이 붙으면 그 한 번의 답에만 사진을 AI 에 같이 보내고,
// 대본 끝에 방마다 다른 [이번 말에 붙은 사진] 칸(ChatRoom.imageRule)을 잠깐 붙입니다. 사진은 저장 · 로그 안 함.
//
// 과목 쌤의 교육과정 · 찾아보기 (2026-10-18 "어느 정도 RAG 하고, 어느 정도 지식은 너가 찾는 방식으로"):
//   - 열 때: 학년 · 교육과정 버전 · 오늘 과목으로 [교육과정] · [찾아보기] 칸을 붙이고 교육과정을 기억합니다. (CurriculumService)
//   - 말마다: 학생의 마지막 말로 고른 성취기준 몇 줄([관련 성취기준])을 그 한 번의 답에만 붙입니다. (가벼운 RAG)
//   - 말마다: 오늘 검색 한도가 남았으면 웹 검색 도구를 붙이고, 답에 붙은 출처(sources)를 검사해 돌려줍니다. (TutorWebSearch)
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
  // 행동 버튼 make_plan 의 목표 · start_quiz 의 주제는 이 글자 수까지
  static final int MAX_ACTION_TOPIC_LENGTH = 30;
  // AI 가 짐작한 이유 값(tags)은 이만큼까지
  static final int MAX_TAGS = 3;

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
  // 과목 쌤 방의 교육과정 · 성취기준 (2026-10-18)
  private final CurriculumService curriculumService;
  // 과목 쌤 방의 웹 검색 한도 · 출처 검사 (2026-10-18)
  private final TutorWebSearch tutorWebSearch;
  private final Clock clock;

  // 채팅을 엽니다. 요청 모양은 통화 시작(POST /realtime/session)과 같습니다. (학년 · 반 없이)
  public ChatReplyResponse start(CallSessionRequest request) {
    return start(request, null, null);
  }

  // 채팅을 엽니다. grade · classNo 를 주면 그 반의 오늘 시간표도 대본에 넣습니다.
  // request.about 이 있으면 그 주제로 엽니다. ("코치랑 이야기하기", 2026-10-18)
  public ChatReplyResponse start(CallSessionRequest request, Integer grade, Integer classNo) {
    // 로그인하지 않은 요청은 여기서 401 이 나갑니다. 아무나 불러서 GPT 비용을 쓰게 두면 안 됩니다.
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);

    // 주제의 사실 줄(카드 제목 · 틀린 문제 등 학생 기기의 글)에 위기 표현이 있으면 AI 를 부르지 않습니다.
    // 위기 표현 검사는 어떤 설정보다 먼저입니다. (CLAUDE.md 4장) 채팅은 열고, 이어서 이야기할 수 있습니다.
    ChatAbout about = request.about();
    if (about != null && CrisisText.hasCrisisText(about.lines())) {
      return startInCrisis(userId, request, now, today);
    }
    ChatTopic topic = about == null ? null : ChatTopic.from(about.kind()).orElse(null);
    // 방은 고르지 않았으면 코치입니다. 주제와 방이 어울리지 않아도(예: 틀린 문제를 코치 방에서) 그대로 엽니다.
    ChatRoom room = ChatRoom.from(request.room()).orElse(ChatRoom.COACH);

    checkAiReady(userId);
    CallService.Script script =
        callService.prepareScript(userId, request, now, today, CallChannel.CHAT);

    // 통화와 같은 대본 끝에 "학생 성향" · "오늘 학교" 칸을 붙입니다. 없으면 붙이지 않습니다.
    // 성향은 학생마다 말 거는 방법을 다르게 하려고 넣습니다. (시작을 미루는 학생에게는 '5분만' 처럼)
    // 답이 거의 다 같아서 믿기 어려운 검사면 점수 대신 "대화로 하나만 확인해" 칸을 붙입니다. (2026-10-18)
    // 과목 쌤 방은 붙이지 않습니다. (ChatRoom.usesPsychSection)
    String psychPart =
        !room.usesPsychSection()
            ? ""
            : psychTestRepository
                .findFirstByUserIdOrderByIdDesc(userId)
                .map(
                    test ->
                        test.isReliable()
                            ? CallScript.psychSection(PsychScore.scoresOf(test.getAnswers()))
                            : CallScript.unsurePsychSection())
                .orElse("");
    // 방 칸([이번 방])을 붙이고, 주제가 있으면 맨 끝에 [이번 대화의 주제] 칸을 붙입니다. 첫 말이 그 주제로 시작합니다.
    String topicPart = topic == null ? "" : CallScript.topicSection(topic, about.lines());
    // 과목 쌤 방은 그 학생의 교육과정을 정해 [교육과정] · [찾아보기] 칸을 붙입니다. 학년을 모르면 [찾아보기] 만.
    CurriculumContext curriculum =
        room == ChatRoom.TUTOR
            ? curriculumService
                .contextOf(userId, grade, schoolTodayService.todaySubjects(grade, classNo))
                .orElse(null)
            : null;
    String curriculumPart =
        room == ChatRoom.TUTOR ? CurriculumService.tutorSection(curriculum) : "";
    String instructions =
        script.instructions()
            + psychPart
            + CallScript.schoolSection(schoolTodayService.scriptLines(grade, classNo))
            + room.section()
            + curriculumPart
            + topicPart;
    ChatAiClient.ChatTurn first =
        room == ChatRoom.TUTOR
            ? askTutor(
                userId,
                // 주제(틀린 문제 등)로 열었으면 그 사실 줄로 성취기준을 고릅니다.
                instructions
                    + curriculumService.standardsSection(
                        curriculum, about == null ? "" : String.join(" ", about.lines())),
                List.of(),
                topic,
                null)
            : askAi(instructions, List.of(), topic);
    // AI 의 첫 인사를 받은 뒤에 기록을 만듭니다. 못 받았으면 채팅이 열리지 않은 것이라 기록도 남기지 않습니다.
    CallRecord record =
        callRecordRepository.save(
            new CallRecord(userId, today, now, script.biasQuestion(), CallChannel.CHAT));
    chatScriptStore.save(record.getId(), userId, instructions, topic, room, curriculum);
    return ChatReplyResponse.withButtons(
        record.getId(),
        first.reply(),
        first.quickReplies(),
        first.action(),
        first.tags(),
        first.sources());
  }

  // 주제의 사실 줄에 위기 표현이 있을 때 채팅을 엽니다. AI 를 부르지 않고 정해 둔 안내로 답합니다.
  // 말 보내기에서 처음 감지했을 때(replyToCrisis)와 같이 기록에 "위기 표현 감지" 를 남기고 보호 조치를 한 번 합니다.
  // 대본에는 주제 칸을 붙이지 않고 "들어 주기만" 칸을 붙여서, 이어지는 대화가 공부 이야기로 가지 않게 합니다.
  private ChatReplyResponse startInCrisis(
      Long userId, CallSessionRequest request, Instant now, LocalDate today) {
    CallService.Script script =
        callService.prepareScript(userId, request, now, today, CallChannel.CHAT);
    CallRecord record = new CallRecord(userId, today, now, script.biasQuestion(), CallChannel.CHAT);
    record.markCrisis();
    record = callRecordRepository.save(record);
    safetyService.activate(userId);
    chatScriptStore.save(
        record.getId(),
        userId,
        script.instructions() + CRISIS_FOLLOW_UP,
        null,
        ChatRoom.from(request.room()).orElse(ChatRoom.COACH));
    return ChatReplyResponse.crisis(record.getId(), CHAT_CRISIS_REPLY);
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
    // 주제 없이 연 채팅이면 null 이고, 그러면 tags 는 빈 목록입니다.
    ChatTopic topic = chatScriptStore.findTopic(chatId, userId).orElse(null);
    // 사진은 모양 · 크기부터 봅니다(틀리면 400). AI 한도를 쓰기 전에 거릅니다.
    String image = request.image();
    if (image != null) {
      CardPhotoService.checkImage(image);
    }
    checkAiReady(userId);
    // 방을 잊었으면 코치로 봅니다. 사진 규칙 · 성취기준 칸은 이번 한 번의 답에만 붙이고 기억해 둔 대본에는 남기지 않습니다.
    ChatRoom room = chatScriptStore.findRoom(chatId, userId).orElse(ChatRoom.COACH);
    ChatAiClient.ChatTurn next;
    if (room == ChatRoom.TUTOR) {
      // 과목 쌤: 학생의 마지막 말로 성취기준을 고르고, 검색 한도가 남았으면 웹 검색 도구를 붙입니다.
      String standards =
          curriculumService.standardsSection(
              chatScriptStore.findCurriculum(chatId, userId).orElse(null),
              lines.get(lines.size() - 1).text());
      String imageRule = image == null ? "" : room.imageRule();
      next = askTutor(userId, instructions + standards + imageRule, lines, topic, image);
    } else if (image == null) {
      next = askAi(instructions, lines, topic);
    } else {
      next = askAiWithImage(instructions + room.imageRule(), lines, topic, image);
    }
    return ChatReplyResponse.withButtons(
        chatId, next.reply(), next.quickReplies(), next.action(), next.tags(), next.sources());
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
  // topic 은 이 채팅의 주제(없으면 null)이고, 이유 값(tags)을 거르는 데 씁니다.
  private ChatAiClient.ChatTurn askAi(
      String instructions, List<CallFinishRequest.Line> lines, ChatTopic topic) {
    Optional<ChatAiClient.ChatTurn> turn = chatAiClient.turn(instructions, lines);
    if (turn.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    return cleanTurn(turn.get(), DayRule.dateOf(clock.instant()), topic);
  }

  // askAi 와 같고, 학생의 마지막 말에 붙은 사진 한 장을 함께 보냅니다.
  private ChatAiClient.ChatTurn askAiWithImage(
      String instructions, List<CallFinishRequest.Line> lines, ChatTopic topic, String image) {
    Optional<ChatAiClient.ChatTurn> turn = chatAiClient.turnWithImage(instructions, lines, image);
    if (turn.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    return cleanTurn(turn.get(), DayRule.dateOf(clock.instant()), topic);
  }

  // 과목 쌤 방의 답. 오늘 검색 한도가 남았으면 웹 검색 도구를 붙이고, AI 가 실제로 검색했으면 한 번 셉니다.
  // image 는 학생의 마지막 말에 붙은 사진(없으면 null)입니다. 못 받았으면 503 입니다.
  private ChatAiClient.ChatTurn askTutor(
      Long userId,
      String instructions,
      List<CallFinishRequest.Line> lines,
      ChatTopic topic,
      String image) {
    boolean webSearch = tutorWebSearch.canSearch(userId);
    Optional<ChatAiClient.ChatTurn> turn =
        chatAiClient.turnForTutor(instructions, lines, image, webSearch);
    if (turn.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
    if (turn.get().searched()) {
      tutorWebSearch.recordSearch(userId);
    }
    return cleanTurn(turn.get(), DayRule.dateOf(clock.instant()), topic);
  }

  // AI 가 준 답을 검사합니다. (2026-10-18 빠른 답 · 행동 버튼)
  //   - 말에 깎아내리는 말이 섞였으면 정해 둔 말로 바꾸고 버튼은 모두 뺍니다.
  //   - 빠른 답: 비었거나 20자가 넘거나 깎아내리는 말이 있으면 빼고, 같은 것은 하나만, 3개까지.
  //   - 행동: cleanAction 의 규칙에 맞을 때만 넣습니다. (today 는 make_plan 의 마감 날을 검사하는 기준)
  //   - 이유 값(tags): cleanTags 의 규칙대로 그 주제의 값만 남깁니다. 주제가 없으면 빈 목록입니다.
  //   - 출처(sources, 과목 쌤의 웹 검색): TutorWebSearch.cleanSources 로 거릅니다. 링크는 앱이 따로 보여 주니
  //     말 안의 링크 표시([글](주소) · 주소)는 글만 남깁니다. (withoutLinks)
  static ChatAiClient.ChatTurn cleanTurn(ChatAiClient.ChatTurn turn, LocalDate today) {
    return cleanTurn(turn, today, null);
  }

  static ChatAiClient.ChatTurn cleanTurn(
      ChatAiClient.ChatTurn turn, LocalDate today, ChatTopic topic) {
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
    return new ChatAiClient.ChatTurn(
        withoutLinks(turn.reply()),
        quickReplies,
        cleanAction(turn.action(), today),
        cleanTags(turn.tags(), topic),
        TutorWebSearch.cleanSources(turn.sources()),
        turn.searched());
  }

  // 말 안의 링크 표시를 글만 남깁니다. 검색한 답에 AI 가 출처를 말 안에 적는 경우가 있어서입니다.
  //   "극한은 [위키백과](https://ko.wikipedia.org/…)에 따르면 …" → "극한은 위키백과에 따르면 …"
  //   "자세한 건 https://ebs.co.kr/abc 에" → "자세한 건  에" 처럼 주소만 지웁니다.
  static String withoutLinks(String reply) {
    if (reply == null) {
      return null;
    }
    String text = reply.replaceAll("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)", "$1");
    text = text.replaceAll("\\(?https?://[^\\s)]+\\)?", "");
    return text.trim();
  }

  // AI 가 짐작한 이유 값 검사. 그 주제(ChatTopic.tags)에 있는 값만, 같은 것은 하나만, 3개까지 남깁니다.
  // 주제가 없거나(주제 없는 채팅 · 서버가 주제를 잊음) 이유 값을 쓰지 않는 주제면 늘 빈 목록입니다.
  //
  //   SESSION_REVIEW, ["phone", "PHONE", "sick", "phone", "tired"]  →  ["phone", "tired"]
  //     ("PHONE" 은 글자가 달라서, "sick" 은 후기의 이유가 아니라서, 두 번째 "phone" 은 겹쳐서 뺌)
  //   CARD_FAIL,      ["sick", "procrastinated"]                     →  ["sick", "procrastinated"]
  //   WEEKLY_REPORT,  ["phone"]                                      →  []
  static List<String> cleanTags(List<String> raw, ChatTopic topic) {
    if (topic == null || raw == null) {
      return List.of();
    }
    List<String> tags = new ArrayList<>();
    for (String value : raw) {
      String tag = value == null ? "" : value.trim();
      if (topic.tags().containsKey(tag) && !tags.contains(tag)) {
        tags.add(tag);
      }
      if (tags.size() == MAX_TAGS) {
        break;
      }
    }
    return tags;
  }

  // 행동 버튼 검사. 맞지 않으면 null(버튼 없음)입니다.
  //   start_timer  : 숫자 · 제목 없이
  //   shrink_today : 30 ~ 60 분 (FE 는 분을 쓰지 않고 오늘 계획을 가볍게 다시 짜서 같이 고칩니다)
  //   create_card  : 제목 1 ~ 20자(깎아내리는 말 없음), 분은 30 ~ 120 (없으면 50분, 30분보다 짧으면 30분, 120분보다 길면 120분)
  //                  30분 밑은 공부 블록으로 보지 않습니다 (StudyBlock, 2026-10-18 멘토 결정 "누가 10분씩 해 공부를")
  //   make_plan    : 목표 1 ~ 30자(깎아내리는 말 없음). 마감 날은 내일 ~ 30일 뒤만 (긴 계획 /ai/long-plan 의 범위).
  //                  모양이 틀리거나 범위 밖이면 버튼은 두고 date 만 null (FE 가 날짜를 다시 물음)
  //   start_quiz   : 주제 1 ~ 30자(깎아내리는 말 없음)
  //
  //   today = 2026-10-18
  //   make_plan "중간고사 미적분", "2026-11-02"  →  그대로
  //   make_plan "중간고사 미적분", "2026-12-25"  →  date 만 null (30일 넘음)
  //   start_quiz "  "                           →  null
  static ChatReplyResponse.Action cleanAction(ChatReplyResponse.Action action, LocalDate today) {
    if (action == null || action.type() == null) {
      return null;
    }
    switch (action.type()) {
      case "start_timer":
        return new ChatReplyResponse.Action("start_timer", null, null);
      case "shrink_today":
        Integer minutes = action.minutes();
        return minutes != null && minutes >= StudyBlock.MIN_MINUTES && minutes <= 60
            ? new ChatReplyResponse.Action("shrink_today", minutes, null)
            : null;
      case "create_card":
        String title = action.title() == null ? "" : action.title().trim();
        if (title.isEmpty() || title.length() > 20 || ToneGuide.hasBannedExpression(title)) {
          return null;
        }
        Integer cardMinutes = action.minutes();
        int safeMinutes =
            cardMinutes == null || cardMinutes <= 0
                ? StudyBlock.DEFAULT_MINUTES
                : Math.max(StudyBlock.MIN_MINUTES, Math.min(StudyBlock.MAX_MINUTES, cardMinutes));
        return new ChatReplyResponse.Action("create_card", safeMinutes, title);
      case "make_plan":
        String goal = topicOf(action.title());
        return goal == null
            ? null
            : new ChatReplyResponse.Action(
                "make_plan", null, goal, deadlineOf(action.date(), today));
      case "start_quiz":
        String topic = topicOf(action.title());
        return topic == null ? null : new ChatReplyResponse.Action("start_quiz", null, topic);
      default:
        return null;
    }
  }

  // 목표 · 주제 한 줄. 비었거나 30자를 넘거나 깎아내리는 말이 있으면 null
  private static String topicOf(String raw) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()
        || text.length() > MAX_ACTION_TOPIC_LENGTH
        || ToneGuide.hasBannedExpression(text)) {
      return null;
    }
    return text;
  }

  // 마감 날 "YYYY-MM-DD". 내일 ~ 30일 뒤(LongPlan.MAX_DAYS)가 아니거나 모양이 틀리면 null
  private static String deadlineOf(String raw, LocalDate today) {
    if (raw == null) {
      return null;
    }
    try {
      LocalDate date = LocalDate.parse(raw.trim());
      boolean inRange = date.isAfter(today) && !date.isAfter(today.plusDays(LongPlan.MAX_DAYS));
      return inRange ? date.toString() : null;
    } catch (DateTimeParseException e) {
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
