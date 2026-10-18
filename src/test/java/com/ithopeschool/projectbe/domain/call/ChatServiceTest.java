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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
}
