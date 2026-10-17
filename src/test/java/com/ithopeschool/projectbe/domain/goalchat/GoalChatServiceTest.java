package com.ithopeschool.projectbe.domain.goalchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.call.ChatAiClient;
import com.ithopeschool.projectbe.domain.call.ChatService;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.goalchat.dto.GoalChatFinishResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: GPT(ChatAiClient · AiClient) 와 보호 조치를 가짜로 바꿔서 '목표 만들기' 대화의 흐름만 확인합니다. (이슈 #239)
class GoalChatServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ChatAiClient chatAiClient = mock(ChatAiClient.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  // 열린 대화를 기억하는 곳은 진짜 코드를 씁니다.
  private final GoalChatStore store = new GoalChatStore(clock);
  private final GoalChatService service =
      new GoalChatService(
          currentUserProvider, chatAiClient, aiClient, gptUsageGuard, store, safetyService, clock);

  @BeforeEach
  void loggedInAndAiOn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(chatAiClient.isEnabled()).willReturn(true);
    given(gptUsageGuard.tryUseText(USER_ID, GptUsageKind.CHAT)).willReturn(true);
  }

  private static ChatMessageRequest said(String... studentTexts) {
    List<Line> lines = new ArrayList<>();
    lines.add(new Line("ai", GoalChatPrompt.OPENING));
    for (String text : studentTexts) {
      lines.add(new Line("me", text));
    }
    return new ChatMessageRequest(lines);
  }

  @Test
  @DisplayName("열면 정해 둔 첫 질문으로 시작하고, GPT 는 부르지 않는다")
  void startsWithFixedOpening() {
    ChatReplyResponse reply = service.start();

    assertThat(reply.reply()).isEqualTo(GoalChatPrompt.OPENING);
    assertThat(reply.showHelp()).isFalse();
    assertThat(store.isOpen(reply.chatId(), USER_ID)).isTrue();
    verify(chatAiClient, never()).reply(anyString(), anyList());
    verifyNoInteractions(gptUsageGuard);
  }

  @Test
  @DisplayName("AI 가 꺼진 서버(내 컴퓨터)에서는 열 때 503")
  void startIs503WhenAiOff() {
    given(chatAiClient.isEnabled()).willReturn(false);

    assertThatThrownBy(service::start)
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  @DisplayName("말을 보내면 오늘 날짜가 든 WOOP 지침으로 AI 의 다음 질문을 받고, 한도를 하나 쓴다")
  void sendsToAi() {
    String chatId = service.start().chatId();
    given(
            chatAiClient.reply(
                eq(GoalChatPrompt.instructions(LocalDate.of(2026, 10, 17))), anyList()))
        .willReturn(Optional.of("좋아. 언제까지 이루고 싶어?"));

    ChatReplyResponse reply = service.send(chatId, said("11월 학평 수학 3등급"));

    assertThat(reply.reply()).isEqualTo("좋아. 언제까지 이루고 싶어?");
    verify(gptUsageGuard).tryUseText(USER_ID, GptUsageKind.CHAT);
  }

  @Test
  @DisplayName("AI 의 답에 깎아내리는 말이 섞이면 정해 둔 말로 바꾼다")
  void replacesPutDowns() {
    String chatId = service.start().chatId();
    given(chatAiClient.reply(anyString(), anyList())).willReturn(Optional.of("그것도 못 하다니 한심하네"));

    assertThat(service.send(chatId, said("영단어장 1회독")).reply())
        .isEqualTo(GoalChatService.SAFE_REPLY);
  }

  @Test
  @DisplayName("학생의 말에 위기 표현이 있으면 GPT 를 부르지 않고 도움 안내로 끝내고, 보호 조치를 켠다")
  void crisisStopsTheChat() {
    String chatId = service.start().chatId();

    ChatReplyResponse reply = service.send(chatId, said("목표 같은 거 없어 그냥 죽고 싶어"));

    assertThat(reply.showHelp()).isTrue();
    assertThat(reply.reply()).isEqualTo(ChatService.CRISIS_REPLY);
    assertThat(reply.helpLines()).isNotEmpty();
    verify(safetyService).activate(USER_ID);
    verify(chatAiClient, never()).reply(anyString(), anyList());
    // 대화는 닫혀서 더 이어갈 수 없습니다.
    assertThat(store.isOpen(chatId, USER_ID)).isFalse();
  }

  @Test
  @DisplayName("남이 연 대화 · 없는 번호면 404, 마지막 줄이 학생 말이 아니면 400")
  void rejectsOthersAndBadLines() {
    String chatId = service.start().chatId();

    given(currentUserProvider.getCurrentUserId()).willReturn(8L);
    assertThatThrownBy(() -> service.send(chatId, said("수학")))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThatThrownBy(() -> service.finish("없는-번호", said("수학")))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);

    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    ChatMessageRequest endsWithAi = new ChatMessageRequest(List.of(new Line("ai", "뭐야?")));
    assertThatThrownBy(() -> service.send(chatId, endsWithAi))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("하루 한도를 다 쓰면 429")
  void limitReached() {
    String chatId = service.start().chatId();
    given(gptUsageGuard.tryUseText(USER_ID, GptUsageKind.CHAT)).willReturn(false);

    assertThatThrownBy(() -> service.send(chatId, said("수학")))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
  }

  @Test
  @DisplayName("끝내면 작은 AI 기능 한도(ASSIST)로 초안을 받아 검사한 뒤 돌려주고, 대화를 닫는다")
  void finishReturnsCheckedDraft() {
    String chatId = service.start().chatId();
    GoalChatPrompt.Answer answer =
        new GoalChatPrompt.Answer(
            "score", "11월 학평 수학 3등급", "2026-11-19", "5등급", 300, "쪽", "좋은 점", "안 하면", "폰", "폰을 치운다");
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(GoalChatPrompt.Answer.class)))
        .willReturn(Optional.of(answer));

    GoalChatFinishResponse response = service.finish(chatId, said("수학 3등급", "11월 19일"));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.goalDraft().title()).isEqualTo("11월 학평 수학 3등급");
    // 점수형이라 완주형 칸은 버립니다. (GoalDraft)
    assertThat(response.goalDraft().totalAmount()).isNull();
    assertThat(store.isOpen(chatId, USER_ID)).isFalse();
  }

  @Test
  @DisplayName("AI 가 꺼짐 · 한도 · 실패로 답이 없으면 에러가 아니라 goalDraft 가 null")
  void finishWithoutAnswer() {
    String chatId = service.start().chatId();
    given(aiClient.ask(any(), any(), any(), eq(GoalChatPrompt.Answer.class)))
        .willReturn(Optional.empty());

    GoalChatFinishResponse response = service.finish(chatId, said("수학"));

    assertThat(response.goalDraft()).isNull();
    assertThat(response.showHelp()).isFalse();
  }

  @Test
  @DisplayName("끝낼 때도 위기 표현을 먼저 보고, 있으면 초안 대신 도움 안내")
  void finishChecksCrisisFirst() {
    String chatId = service.start().chatId();

    GoalChatFinishResponse response = service.finish(chatId, said("다 끝내고 사라지고 싶어"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.goalDraft()).isNull();
    verify(safetyService).activate(USER_ID);
    verifyNoInteractions(aiClient);
  }
}
