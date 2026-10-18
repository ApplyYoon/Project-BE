package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatAbout;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.safety.HelpLine;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.school.SchoolTodayService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: GPT(ChatAiClient) · 대본 만들기(CallService) · DB 를 가짜로 바꿔서 채팅의 흐름만 확인합니다. (이슈 #238)
class ChatServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long CHAT_ID = 12L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final String SCRIPT = "채팅 대본";
  private static final CallSessionRequest START = new CallSessionRequest(2, 3, null, null, null);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ChatAiClient chatAiClient = mock(ChatAiClient.class);
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final CallService callService = mock(CallService.class);
  private final CallRecordService callRecordService = mock(CallRecordService.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  // 대본을 기억하는 곳은 진짜 코드를 씁니다.
  private final ChatScriptStore chatScriptStore = new ChatScriptStore(clock);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final SchoolTodayService schoolTodayService = mock(SchoolTodayService.class);
  private final PsychTestRepository psychTestRepository = mock(PsychTestRepository.class);
  private final ChatService service =
      new ChatService(
          currentUserProvider,
          chatAiClient,
          gptUsageGuard,
          callService,
          callRecordService,
          callRecordRepository,
          chatScriptStore,
          safetyService,
          schoolTodayService,
          psychTestRepository,
          clock);

  @BeforeEach
  void loggedInAndAiOn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(chatAiClient.isEnabled()).willReturn(true);
    given(gptUsageGuard.tryUseText(USER_ID, GptUsageKind.CHAT)).willReturn(true);
    given(callService.prepareScript(eq(USER_ID), any(), eq(NOW), eq(TODAY), eq(CallChannel.CHAT)))
        .willReturn(new CallService.Script(SCRIPT, null));
    given(callRecordRepository.save(any()))
        .willAnswer(
            invocation -> {
              CallRecord record = invocation.getArgument(0);
              ReflectionTestUtils.setField(record, "id", CHAT_ID);
              return record;
            });
  }

  private static ChatMessageRequest said(String... studentTexts) {
    List<Line> lines = new ArrayList<>();
    lines.add(new Line("ai", "어제 어땠어?"));
    for (String text : studentTexts) {
      lines.add(new Line("me", text));
    }
    return new ChatMessageRequest(lines);
  }

  // 열려 있는 내 채팅 하나를 DB 에 둡니다.
  private CallRecord givenOpenChat() {
    CallRecord chat = new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.CHAT);
    ReflectionTestUtils.setField(chat, "id", CHAT_ID);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(chat));
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT);
    return chat;
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  // ---- 채팅 열기 ----

  @Test
  @DisplayName("오늘 학교 정보가 있으면 대본 끝에 [오늘 학교] 칸을 붙여 AI 에 건네고 기억한다")
  void startAddsSchoolLines() {
    List<String> schoolLines = List.of("- 오늘 중식: 흑미밥, 나주곰탕");
    given(schoolTodayService.scriptLines(2, 1)).willReturn(schoolLines);
    String withSchool = SCRIPT + CallScript.schoolSection(schoolLines);
    given(chatAiClient.turn(withSchool, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("오늘 급식 나주곰탕이네!")));

    ChatReplyResponse response = service.start(START, 2, 1);

    assertThat(response.reply()).isEqualTo("오늘 급식 나주곰탕이네!");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(withSchool);
  }

  @Test
  @DisplayName("성향 검사가 있으면 대본에 [학생 성향] 칸을 붙인다 (시작 저항이 높으면 '5분만')")
  void startAddsPsychSection() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      // 시작 저항 문장만 7점, 나머지는 4점(보통)
      answers.put(item, item.scale() == PsychScale.START_RESISTANCE ? 7 : 4);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    String withPsych = SCRIPT + CallScript.psychSection(PsychScore.scoresOf(answers));
    given(chatAiClient.turn(withPsych, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("5분만 먼저 해 볼까?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.reply()).isEqualTo("5분만 먼저 해 볼까?");
    assertThat(withPsych).contains("[학생 성향]").contains("5분만");
  }

  @Test
  @DisplayName("성향 검사 답이 거의 다 같으면 점수 대신 '대화로 하나만 물어봐' 칸을 붙인다")
  void startAddsUnsurePsychSection() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      // 시작 저항이 7 이어도, 35개가 모두 7 이면 믿기 어렵습니다.
      answers.put(item, 7);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    String withUnsure = SCRIPT + CallScript.unsurePsychSection();
    given(chatAiClient.turn(withUnsure, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("요즘 시작하기 어때?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.reply()).isEqualTo("요즘 시작하기 어때?");
    assertThat(withUnsure).contains("[학생 성향]").contains("믿기 어려워").doesNotContain("5분만");
  }

  @Test
  @DisplayName("채팅을 열면 통화와 같은 대본(채팅 버전)으로 AI 의 첫 인사를 받고, 채팅 기록을 만든다")
  void startsAChat() {
    given(chatAiClient.turn(SCRIPT, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("안녕! 어제는 어땠어?")));

    ChatReplyResponse response = service.start(START);

    assertThat(response.chatId()).isEqualTo("12");
    assertThat(response.reply()).isEqualTo("안녕! 어제는 어땠어?");
    assertThat(response.showHelp()).isFalse();
    ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
    verify(callRecordRepository).save(saved.capture());
    assertThat(saved.getValue().getChannel()).isEqualTo(CallChannel.CHAT);
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    // 다음 말부터 같은 대본을 씁니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("AI 가 꺼져 있으면 503, 오늘 한도를 다 썼으면 429 이고 채팅 기록을 만들지 않는다")
  void startNeedsAiAndQuota() {
    given(chatAiClient.isEnabled()).willReturn(false);
    assertError(() -> service.start(START), ErrorCode.SERVICE_UNAVAILABLE, "AI 상담이 아직 켜져 있지 않아요");

    given(chatAiClient.isEnabled()).willReturn(true);
    given(gptUsageGuard.tryUseText(USER_ID, GptUsageKind.CHAT)).willReturn(false);
    assertError(
        () -> service.start(START), ErrorCode.TOO_MANY_REQUESTS, "오늘 AI 사용량을 다 썼어요. 내일 다시 이야기해요");

    verify(callRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("AI 의 첫 인사를 못 받으면 503 이고 채팅 기록을 만들지 않는다")
  void startFailsWithoutFirstReply() {
    given(chatAiClient.turn(any(), anyList())).willReturn(Optional.empty());

    assertError(
        () -> service.start(START),
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
    verify(callRecordRepository, never()).save(any());
  }

  // ---- 말 보내기 ----

  @Test
  @DisplayName("말을 보내면 지금까지의 대화와 기억해 둔 대본으로 AI 의 다음 말을 받는다")
  void sendsAMessage() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 보다가 늦게 시작했어");
    given(chatAiClient.turn(SCRIPT, request.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("그때 폰은 어디 있었어?")));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("그때 폰은 어디 있었어?");
    assertThat(response.showHelp()).isFalse();
  }

  @Test
  @DisplayName("학생의 말에 위기 표현이 있으면 GPT 를 부르지 않고 도움 안내로 답하고, 채팅은 끝내지 않는다")
  void crisisKeepsTheChatOpen() {
    CallRecord chat = givenOpenChat();

    ChatReplyResponse response = service.send(CHAT_ID, said("그냥 다 그만두고 죽고 싶어"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.reply()).isEqualTo(ChatService.CHAT_CRISIS_REPLY);
    assertThat(response.helpLines()).isEqualTo(HelpLine.ALL);
    verify(chatAiClient, never()).reply(any(), anyList());
    verify(gptUsageGuard, never()).tryUseText(any(), any());
    // "위기 표현이 감지됨" 표시는 남기지만 채팅은 끝내지 않습니다. 그 뒤 7일 동안 통화·채팅이 위로하는 쪽이 됩니다.
    assertThat(chat.isCrisisDetected()).isTrue();
    assertThat(chat.isFinished()).isFalse();
    verify(callRecordRepository).save(chat);
    // 남은 대화에서 공부 이야기를 꺼내지 않게 대본에 한 칸이 붙습니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID))
        .contains(SCRIPT + ChatService.CRISIS_FOLLOW_UP);
    // 통화 마무리와 같은 보호 조치(관리 한도 0 · 회복 메시지)를 합니다.
    verify(safetyService).activate(USER_ID);
  }

  @Test
  @DisplayName("안내한 뒤에는 앞의 말로 다시 걸리지 않고 AI 와 이어서 이야기한다 (보호 조치는 한 번만)")
  void afterCrisisTheChatContinues() {
    CallRecord chat = givenOpenChat();
    service.send(CHAT_ID, said("죽고 싶어"));

    ChatMessageRequest next = said("죽고 싶어", "그냥 오늘 너무 힘들었어");
    given(chatAiClient.turn(SCRIPT + ChatService.CRISIS_FOLLOW_UP, next.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("많이 힘들었구나. 무슨 일이 있었어?")));
    ChatReplyResponse response = service.send(CHAT_ID, next);

    assertThat(response.showHelp()).isFalse();
    assertThat(response.reply()).isEqualTo("많이 힘들었구나. 무슨 일이 있었어?");
    assertThat(chat.isFinished()).isFalse();

    // 마지막 말에 또 위기 표현이 있으면 다시 안내하지만, 보호 조치는 다시 하지 않습니다.
    assertThat(service.send(CHAT_ID, said("죽고 싶어", "괜찮아", "사라지고 싶어")).showHelp()).isTrue();
    verify(safetyService, times(1)).activate(USER_ID);
  }

  @Test
  @DisplayName("앞에서 한 말에 위기 표현이 있어도 잡는다 (마지막 줄만 보지 않음)")
  void crisisInAnEarlierLineIsCaught() {
    givenOpenChat();

    ChatReplyResponse response = service.send(CHAT_ID, said("사라지고 싶었어", "그래도 오늘은 했어"));

    assertThat(response.showHelp()).isTrue();
  }

  @Test
  @DisplayName("AI 의 답에 깎아내리는 말이 섞이면 정해 둔 말로 바꾼다")
  void bannedExpressionIsReplaced() {
    givenOpenChat();
    given(chatAiClient.turn(any(), anyList()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이것도 못 하다니 한심해.")));

    assertThat(service.send(CHAT_ID, said("못 했어")).reply()).isEqualTo(ChatService.SAFE_REPLY);
  }

  @Test
  @DisplayName("마지막 줄이 학생의 말이 아니면 400")
  void lastLineMustBeTheStudent() {
    givenOpenChat();

    assertError(
        () -> service.send(CHAT_ID, new ChatMessageRequest(List.of(new Line("ai", "안녕")))),
        ErrorCode.VALIDATION_ERROR,
        "마지막 줄은 학생(me)의 말이어야 해요");
  }

  @Test
  @DisplayName("내 채팅이 아니거나 음성 통화 번호면 404, 이미 끝난 채팅이면 409")
  void onlyMyOpenChats() {
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.empty());
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.NOT_FOUND, "채팅을 찾을 수 없어요");

    CallRecord voice = new CallRecord(USER_ID, TODAY, NOW, null);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(voice));
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.NOT_FOUND, "채팅을 찾을 수 없어요");

    CallRecord ended = new CallRecord(USER_ID, TODAY, NOW, null, CallChannel.CHAT);
    ended.finishWithCrisis(NOW);
    given(callRecordRepository.findByIdAndUserId(CHAT_ID, USER_ID)).willReturn(Optional.of(ended));
    assertError(() -> service.send(CHAT_ID, said("응")), ErrorCode.CONFLICT, "이미 끝난 채팅이에요");
  }

  @Test
  @DisplayName("서버가 다시 켜져 대본을 잊었으면 409 (새로 시작)")
  void forgottenScriptMeansStartAgain() {
    givenOpenChat();
    chatScriptStore.remove(CHAT_ID);

    assertError(
        () -> service.send(CHAT_ID, said("응")), ErrorCode.CONFLICT, "채팅이 오래되어 끝났어요. 새로 시작해 주세요");
  }

  // ---- 끝내기 ----

  @Test
  @DisplayName("끝내기는 통화 마무리와 똑같이 정리한다 (CallRecordService.finish)")
  void finishUsesTheCallFinish() {
    givenOpenChat();
    CallFinishRequest request = new CallFinishRequest(said("폰 때문이었어").lines());

    service.finish(CHAT_ID, request);

    verify(callRecordService).finish(CHAT_ID, request);
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("AI 가 준 빠른 답 · 행동을 검사해서 넘긴다 (빈 것 · 20자 넘는 것 · 겹치는 것은 빼고 3개까지)")
  void sendsCleanedButtons() {
    givenOpenChat();
    ChatMessageRequest request = said("오늘 좀 피곤해");
    given(chatAiClient.turn(SCRIPT, request.lines()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "그럼 10분만 해 볼까?",
                    List.of(
                        "좋아, 10분",
                        " ",
                        "좋아, 10분",
                        "이건 스무 글자가 넘는 아주 아주 긴 빠른 답이야",
                        "오늘은 쉴래",
                        "그래도 다 할래",
                        "네 번째"),
                    new ChatReplyResponse.Action("shrink_today", 10, null))));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("그럼 10분만 해 볼까?");
    assertThat(response.quickReplies()).containsExactly("좋아, 10분", "오늘은 쉴래", "그래도 다 할래");
    assertThat(response.action()).isEqualTo(new ChatReplyResponse.Action("shrink_today", 10, null));
  }

  @Test
  @DisplayName("행동 검사: 줄이기는 5~60분만, 카드는 제목이 있어야 하고 분이 이상하면 25분, 모르는 종류는 버림")
  void cleansActions() {
    assertThat(ChatService.cleanAction(new ChatReplyResponse.Action("start_timer", 99, "x"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("start_timer", null, null));
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("shrink_today", 90, null), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("create_card", 0, "영어 단어"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("create_card", 25, "영어 단어"));
    assertThat(ChatService.cleanAction(new ChatReplyResponse.Action("create_card", 30, " "), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("delete_all", null, null), TODAY))
        .isNull();
    assertThat(ChatService.cleanAction(null, TODAY)).isNull();
  }

  @Test
  @DisplayName("행동 검사: 긴 계획은 목표가 있어야 하고 마감 날은 내일 ~ 30일 뒤만 (밖이면 date 만 비움)")
  void cleansMakePlan() {
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", 40, " 중간고사 미적분 ", "2026-11-02"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "중간고사 미적분", "2026-11-02"));
    // 내일과 30일 뒤는 됨
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("make_plan", null, "수행평가", "2026-10-18"), TODAY)
                .date())
        .isEqualTo("2026-10-18");
    assertThat(
            ChatService.cleanAction(
                    new ChatReplyResponse.Action("make_plan", null, "수행평가", "2026-11-16"), TODAY)
                .date())
        .isEqualTo("2026-11-16");
    // 오늘 · 31일 뒤 · 모양이 틀림 · 없음 → 버튼은 두고 date 만 null
    for (String date : new String[] {"2026-10-17", "2026-11-17", "11월 2일", null}) {
      assertThat(
              ChatService.cleanAction(
                  new ChatReplyResponse.Action("make_plan", null, "수행평가", date), TODAY))
          .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "수행평가", null));
    }
    // 목표가 비었거나 30자를 넘으면 버튼 없음
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", null, " ", "2026-11-02"), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("make_plan", null, "가".repeat(31), "2026-11-02"),
                TODAY))
        .isNull();
  }

  @Test
  @DisplayName("행동 검사: 확인 문제는 주제 1~30자, 분 · 날짜는 버림")
  void cleansStartQuiz() {
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", 30, " 수열의 극한 ", "2026-11-02"), TODAY))
        .isEqualTo(new ChatReplyResponse.Action("start_quiz", null, "수열의 극한"));
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", null, "가".repeat(30)), TODAY))
        .isNotNull();
    assertThat(
            ChatService.cleanAction(
                new ChatReplyResponse.Action("start_quiz", null, "가".repeat(31)), TODAY))
        .isNull();
    assertThat(
            ChatService.cleanAction(new ChatReplyResponse.Action("start_quiz", null, null), TODAY))
        .isNull();
  }

  // ---- 코치랑 이야기하기 (주제가 있는 채팅, 2026-10-18) ----

  private static CallSessionRequest startAbout(String kind, String... facts) {
    return new CallSessionRequest(
        2, 3, null, null, null, new ChatAbout(kind, List.of(facts)), null);
  }

  private static CallSessionRequest startIn(String room, ChatAbout about) {
    return new CallSessionRequest(2, 3, null, null, null, about, room);
  }

  @Test
  @DisplayName("주제와 함께 열면 대본 끝에 [이번 대화의 주제] 칸을 붙이고, 주제를 기억하고, 그 주제의 이유 값만 돌려준다")
  void startWithTopic() {
    List<String> facts = List.of("카드: 미적분 3단원 40분", "집중도 82%, 폰 3번 들었음");
    String withTopic = SCRIPT + CallScript.topicSection(ChatTopic.SESSION_REVIEW, facts);
    given(chatAiClient.turn(withTopic, List.of()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "40분 했네! 폰을 3번 들었던데 어땠어?",
                    List.of("폰이 신경 쓰였어"),
                    null,
                    List.of("phone", "sick", "phone"))));

    ChatReplyResponse response =
        service.start(startAbout("SESSION_REVIEW", facts.get(0), facts.get(1)));

    assertThat(response.reply()).isEqualTo("40분 했네! 폰을 3번 들었던데 어땠어?");
    // "sick" 은 후기의 이유 값이 아니라서, 두 번째 "phone" 은 겹쳐서 빠집니다.
    assertThat(response.tags()).containsExactly("phone");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(withTopic);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).contains(ChatTopic.SESSION_REVIEW);
  }

  @Test
  @DisplayName("주제로 연 채팅은 말을 주고받을 때도 그 주제의 이유 값만 돌려준다")
  void sendKeepsTopicTags() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, ChatTopic.CARD_FAIL);
    ChatMessageRequest request = said("어제 미루다가 그냥 잤어");
    given(chatAiClient.turn(SCRIPT, request.lines()))
        .willReturn(
            Optional.of(
                new ChatAiClient.ChatTurn(
                    "미루다 보니 늦어졌구나. 이유: 미루다 보니 · 피곤 — 맞아?",
                    List.of(),
                    null,
                    List.of("procrastinated", "stuck", "tired", "phone", "sick"))));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    // "stuck" 은 못 한 카드의 이유 값이 아니라서 빠지고, 3개까지만 남습니다.
    assertThat(response.tags()).containsExactly("procrastinated", "tired", "phone");
  }

  @Test
  @DisplayName("주제 없이 연 채팅 · 서버가 주제를 잊은 채팅은 이유 값이 늘 빈 목록이다")
  void noTopicMeansNoTags() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 봤어");
    given(chatAiClient.turn(SCRIPT, request.lines()))
        .willReturn(
            Optional.of(new ChatAiClient.ChatTurn("그랬구나", List.of(), null, List.of("phone"))));

    assertThat(service.send(CHAT_ID, request).tags()).isEmpty();
  }

  @Test
  @DisplayName("주제의 사실 줄에 위기 표현이 있으면 AI 를 부르지 않고 안내로 열고, 보호 조치를 한 번 한다")
  void startWithCrisisInTopic() {
    ChatReplyResponse response = service.start(startAbout("QUIZ_WRONG", "문제: 죽고 싶다는 말의 뜻은?"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.reply()).isEqualTo(ChatService.CHAT_CRISIS_REPLY);
    assertThat(response.chatId()).isEqualTo(String.valueOf(CHAT_ID));
    verify(chatAiClient, never()).turn(any(), anyList());
    verify(gptUsageGuard, never()).tryUseText(any(), any());
    verify(safetyService).activate(USER_ID);
    ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
    verify(callRecordRepository).save(saved.capture());
    assertThat(saved.getValue().isCrisisDetected()).isTrue();
    // 이어지는 대화는 주제 없이 "들어 주기만" 하는 대본으로 갑니다.
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID))
        .contains(SCRIPT + ChatService.CRISIS_FOLLOW_UP);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).isEmpty();
  }

  @Test
  @DisplayName("이유 값 검사: 그 주제의 값만, 겹치지 않게, 3개까지. 주제가 없거나 이유 값을 안 쓰는 주제면 빈 목록")
  void cleansTags() {
    assertThat(
            ChatService.cleanTags(
                List.of("phone", "PHONE", " tired ", "sick", "phone"), ChatTopic.SESSION_REVIEW))
        .containsExactly("phone", "tired");
    assertThat(ChatService.cleanTags(List.of("phone"), ChatTopic.WEEKLY_REPORT)).isEmpty();
    assertThat(ChatService.cleanTags(List.of("phone"), null)).isEmpty();
    assertThat(ChatService.cleanTags(null, ChatTopic.CARD_FAIL)).isEmpty();
    List<String> withNull = new ArrayList<>();
    withNull.add(null);
    withNull.add("sick");
    assertThat(ChatService.cleanTags(withNull, ChatTopic.CARD_FAIL)).containsExactly("sick");
  }

  @Test
  @DisplayName("AI 의 말에 깎아내리는 말이 있으면 이유 값도 같이 뺀다")
  void bannedReplyDropsTags() {
    ChatAiClient.ChatTurn turn =
        new ChatAiClient.ChatTurn("이것도 못 하다니 한심해.", List.of(), null, List.of("phone"));

    assertThat(ChatService.cleanTurn(turn, TODAY, ChatTopic.SESSION_REVIEW).tags()).isEmpty();
  }

  // ---- 방 3개: 코치 · 목표 메이트 · 과목 쌤 (2026-10-18) ----

  @Test
  @DisplayName("과목 쌤 방은 대본 끝에 [이번 방: 과목 쌤] 칸을 붙이고, 학생 성향 칸은 붙이지 않고, 방을 기억한다")
  void startInTutorRoom() {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, item.scale() == PsychScale.START_RESISTANCE ? 7 : 4);
    }
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new PsychTest(USER_ID, NOW, answers)));
    String tutorScript = SCRIPT + ChatRoom.TUTOR.section();
    given(chatAiClient.turn(tutorScript, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("어떤 문제가 막혔어?")));

    ChatReplyResponse response = service.start(startIn("TUTOR", null));

    assertThat(response.reply()).isEqualTo("어떤 문제가 막혔어?");
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(tutorScript);
    assertThat(tutorScript).doesNotContain("[학생 성향]");
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.TUTOR);
  }

  @Test
  @DisplayName("목표 메이트 방에 이번 주 숫자를 주제로 열면 [이번 방] 칸 뒤에 [이번 대화의 주제] 칸이 붙는다")
  void startInGoalMateRoomWithWeeklyReport() {
    List<String> facts = List.of("이번 주 순공 6시간 10분, 계획의 72%");
    String script =
        SCRIPT
            + ChatRoom.GOAL_MATE.section()
            + CallScript.topicSection(ChatTopic.WEEKLY_REPORT, facts);
    given(chatAiClient.turn(script, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("이번 주 72% 했네. 다음 주엔 뭘 바꿔 볼까?")));

    ChatReplyResponse response =
        service.start(startIn("GOAL_MATE", new ChatAbout("WEEKLY_REPORT", facts)));

    assertThat(response.reply()).isEqualTo("이번 주 72% 했네. 다음 주엔 뭘 바꿔 볼까?");
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.GOAL_MATE);
    assertThat(chatScriptStore.findTopic(CHAT_ID, USER_ID)).contains(ChatTopic.WEEKLY_REPORT);
  }

  @Test
  @DisplayName("방을 고르지 않으면 코치 방이고 대본은 지금과 같다 (주제와 방이 안 맞아도 그대로 연다)")
  void startWithoutRoomIsCoach() {
    List<String> facts = List.of("문제: lim 1/n / 학생 답: 1 / 정답: 0");
    String script = SCRIPT + CallScript.topicSection(ChatTopic.QUIZ_WRONG, facts);
    given(chatAiClient.turn(script, List.of()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("왜 1 이라고 생각했어?")));

    service.start(startIn(null, new ChatAbout("QUIZ_WRONG", facts)));

    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(script);
    assertThat(chatScriptStore.findRoom(CHAT_ID, USER_ID)).contains(ChatRoom.COACH);
  }

  // ---- 사진 질문 (2026-10-18) ----

  // 진짜로 읽을 수 있는 아주 작은 사진 글자 (내용은 상관없고, base64 로 풀리기만 하면 됩니다)
  private static final String IMAGE =
      "data:image/jpeg;base64,"
          + Base64.getEncoder().encodeToString("사진".getBytes(StandardCharsets.UTF_8));

  @Test
  @DisplayName("사진이 붙은 말은 그 한 번만 사진과 함께 보내고, 과목 쌤 방의 사진 규칙을 잠깐 붙인다 (기억한 대본은 그대로)")
  void sendWithImageInTutorRoom() {
    givenOpenChat();
    chatScriptStore.save(CHAT_ID, USER_ID, SCRIPT, null, ChatRoom.TUTOR);
    ChatMessageRequest request = new ChatMessageRequest(said("이 문제 어디서 틀렸어?").lines(), IMAGE);
    given(chatAiClient.turnWithImage(SCRIPT + ChatRoom.TUTOR.imageRule(), request.lines(), IMAGE))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("3번 문제 맞지? 어디까지 풀었어?")));

    ChatReplyResponse response = service.send(CHAT_ID, request);

    assertThat(response.reply()).isEqualTo("3번 문제 맞지? 어디까지 풀었어?");
    verify(chatAiClient, never()).turn(any(), anyList());
    assertThat(chatScriptStore.find(CHAT_ID, USER_ID)).contains(SCRIPT);
  }

  @Test
  @DisplayName("방을 잊은 채팅에 사진이 오면 코치 방의 사진 규칙을 쓴다")
  void sendWithImageWhenRoomIsLost() {
    givenOpenChat();
    ChatMessageRequest request = new ChatMessageRequest(said("이거 내 계획표야").lines(), IMAGE);
    given(chatAiClient.turnWithImage(SCRIPT + ChatRoom.COACH.imageRule(), request.lines(), IMAGE))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("계획표 봤어!")));

    assertThat(service.send(CHAT_ID, request).reply()).isEqualTo("계획표 봤어!");
  }

  @Test
  @DisplayName("사진이 없으면 사진 없이 보낸다")
  void sendWithoutImage() {
    givenOpenChat();
    ChatMessageRequest request = said("폰 봤어");
    given(chatAiClient.turn(SCRIPT, request.lines()))
        .willReturn(Optional.of(ChatAiClient.ChatTurn.textOnly("그랬구나")));

    service.send(CHAT_ID, request);

    verify(chatAiClient, never()).turnWithImage(any(), anyList(), any());
  }

  @Test
  @DisplayName("사진 모양이 틀리면 400 이고 AI 한도를 쓰지 않는다")
  void rejectsBadImage() {
    givenOpenChat();
    ChatMessageRequest gif =
        new ChatMessageRequest(said("이거 봐").lines(), "data:image/gif;base64,R0lGOD");
    ChatMessageRequest broken =
        new ChatMessageRequest(said("이거 봐").lines(), "data:image/jpeg;base64,@@@");

    assertError(
        () -> service.send(CHAT_ID, gif),
        ErrorCode.VALIDATION_ERROR,
        "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    assertError(
        () -> service.send(CHAT_ID, broken), ErrorCode.VALIDATION_ERROR, "사진을 읽을 수 없어요. 다시 찍어 주세요");
    verify(gptUsageGuard, never()).tryUseText(any(), any());
  }
}
