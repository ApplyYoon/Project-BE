package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.message.dto.MessageResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

// MessageService 의 로직만 확인합니다. DB 와 로그인 확인은 가짜로 바꿔 끼웁니다.
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private static final Long ME = 1L;
  // 시계를 이 순간에 멈춰 둡니다. (읽은 시각·보낸 시각이 이 값이 되어야 합니다)
  private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
  private static final Instant EARLIER = Instant.parse("2026-10-12T01:00:00Z");

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private MessageRepository messageRepository;

  private MessageService messageService;

  @BeforeEach
  void setUp() {
    messageService =
        new MessageService(
            currentUserProvider, messageRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private Message messageWithId(Long id, Long userId, MessageKind kind) {
    Message message = new Message(userId, kind, "내용 " + id, EARLIER);
    // id 는 DB 가 만들어 주는 값이라 테스트에서는 직접 넣어야 합니다.
    ReflectionTestUtils.setField(message, "id", id);
    return message;
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록: 값을 안 주면 내 번호로 처음부터 끝까지, 20개 한도로 찾는다")
  void listDefault() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(
            messageRepository.findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
                eq(ME), eq(0L), eq(Long.MAX_VALUE), any(Pageable.class)))
        .willReturn(List.of());

    List<MessageResponse> result = messageService.getMessages(null, null);

    assertThat(result).isEmpty();
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(messageRepository)
        .findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
            eq(ME), eq(0L), eq(Long.MAX_VALUE), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("목록: after 와 before 는 찾기 조건으로 그대로 넘어간다")
  void listWithAfterAndBefore() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(
            messageRepository.findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
                eq(ME), eq(10L), eq(30L), any(Pageable.class)))
        .willReturn(List.of());

    messageService.getMessages(10L, 30L);

    verify(messageRepository)
        .findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
            eq(ME), eq(10L), eq(30L), any(Pageable.class));
  }

  @Test
  @DisplayName("목록: 찾은 메시지를 응답 모양으로 바꿔 순서 그대로 돌려준다")
  void listMapsToResponse() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    Message newer = messageWithId(7L, ME, MessageKind.STICK);
    Message older = messageWithId(5L, ME, MessageKind.CARROT);
    given(
            messageRepository.findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
                eq(ME), eq(0L), eq(Long.MAX_VALUE), any(Pageable.class)))
        .willReturn(List.of(newer, older));

    List<MessageResponse> result = messageService.getMessages(null, null);

    assertThat(result).extracting(MessageResponse::id).containsExactly("7", "5");
    assertThat(result).extracting(MessageResponse::kind).containsExactly("stick", "carrot");
    assertThat(result).extracting(MessageResponse::sentAt).containsOnly(EARLIER.toEpochMilli());
  }

  @Test
  @DisplayName("목록: 로그인하지 않았으면 401 이 나가고 DB 는 건드리지 않는다")
  void listRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> messageService.getMessages(null, null))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verifyNoInteractions(messageRepository);
  }

  // ---- 읽음 표시 ----

  @Test
  @DisplayName("읽음: 지금 시각이 읽은 시각으로 저장되고 read 가 true 가 된다")
  void markReadSetsNow() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    Message message = messageWithId(7L, ME, MessageKind.NUDGE);
    given(messageRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(message));

    MessageResponse result = messageService.markRead(7L);

    assertThat(message.getReadAt()).isEqualTo(NOW);
    assertThat(result.read()).isTrue();
    assertThat(result.readAt()).isEqualTo(NOW.toEpochMilli());
  }

  @Test
  @DisplayName("읽음: 이미 읽은 메시지는 에러 없이 처음 읽은 시각을 그대로 둔다")
  void markReadTwiceKeepsFirstTime() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    Message message = messageWithId(7L, ME, MessageKind.NUDGE);
    message.markRead(EARLIER);
    given(messageRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(message));

    MessageResponse result = messageService.markRead(7L);

    assertThat(message.getReadAt()).isEqualTo(EARLIER);
    assertThat(result.readAt()).isEqualTo(EARLIER.toEpochMilli());
  }

  @Test
  @DisplayName("읽음: 없거나 다른 사람의 메시지는 404 '메시지를 찾을 수 없어요'")
  void markReadNotFound() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(messageRepository.findByIdAndUserId(99L, ME)).willReturn(Optional.empty());

    assertThatThrownBy(() -> messageService.markRead(99L))
        .isInstanceOf(ApiException.class)
        .hasMessage("메시지를 찾을 수 없어요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("읽음: 로그인하지 않았으면 401 이 나가고 DB 는 건드리지 않는다")
  void markReadRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> messageService.markRead(7L)).isInstanceOf(ApiException.class);
    verifyNoInteractions(messageRepository);
  }

  // ---- 저장 (서버 안에서 부르는 함수) ----

  @Test
  @DisplayName("저장: 받을 사람·종류·내용을 담고, 보낸 시각은 지금으로 저장한다")
  void saveUsesNowAsSentAt() {
    given(messageRepository.save(any(Message.class))).willAnswer(call -> call.getArgument(0));

    Message saved = messageService.save(2L, MessageKind.RECOVERY, "오늘은 쉬어 가자");

    assertThat(saved.getUserId()).isEqualTo(2L);
    assertThat(saved.getKind()).isEqualTo(MessageKind.RECOVERY);
    assertThat(saved.getText()).isEqualTo("오늘은 쉬어 가자");
    assertThat(saved.getSentAt()).isEqualTo(NOW);
    assertThat(saved.isRead()).isFalse();
    // 서버 안에서 부르는 함수라 "지금 요청한 사용자" 를 묻지 않습니다.
    verifyNoInteractions(currentUserProvider);
  }

  @Test
  @DisplayName("저장: 내용이 500자면 저장하고, 501자면 거절한다")
  void saveTextLimit() {
    given(messageRepository.save(any(Message.class))).willAnswer(call -> call.getArgument(0));

    assertThat(messageService.save(2L, MessageKind.EVENT, "가".repeat(500)).getText()).hasSize(500);
    assertThatThrownBy(() -> messageService.save(2L, MessageKind.EVENT, "가".repeat(501)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("500");
  }

  @Test
  @DisplayName("저장: 받을 사람이 없거나, 종류가 없거나, 내용이 비어 있으면 거절하고 저장하지 않는다")
  void saveRejectsBadInput() {
    assertThatThrownBy(() -> messageService.save(null, MessageKind.NUDGE, "내용"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> messageService.save(2L, null, "내용"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> messageService.save(2L, MessageKind.NUDGE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> messageService.save(2L, MessageKind.NUDGE, "   "))
        .isInstanceOf(IllegalArgumentException.class);

    verify(messageRepository, never()).save(any());
  }
}
