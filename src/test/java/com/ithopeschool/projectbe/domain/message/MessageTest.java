package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// messages 테이블을 실제 DB(H2)에 만들고, 넣고 꺼내 보며 설계한 규칙이 지켜지는지 확인합니다.
@DataJpaTest
class MessageTest {

  private static final Instant SENT = Instant.parse("2026-10-12T01:00:00Z");

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("저장하면 번호가 생기고, DB 에서 다시 꺼내도 값이 같다")
  void saveAndFind() {
    Message saved = em.persistFlushFind(new Message(1L, MessageKind.NUDGE, "지금 25분만 해 보자", SENT));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getKind()).isEqualTo(MessageKind.NUDGE);
    assertThat(saved.getText()).isEqualTo("지금 25분만 해 보자");
    assertThat(saved.getSentAt()).isEqualTo(SENT);
  }

  @Test
  @DisplayName("새 메시지는 안 읽은 상태이고, 읽은 시각은 비어 있다")
  void newMessageIsUnread() {
    Message saved = em.persistFlushFind(new Message(1L, MessageKind.CARROT, "잘했어", SENT));

    assertThat(saved.isRead()).isFalse();
    assertThat(saved.getReadAt()).isNull();
  }

  @Test
  @DisplayName("읽음 표시를 하면 읽은 시각이 저장되고, DB 에서 다시 꺼내도 남아 있다")
  void markReadIsSaved() {
    Message saved = em.persistFlushFind(new Message(1L, MessageKind.CARROT, "잘했어", SENT));
    Instant readAt = SENT.plusSeconds(60);

    saved.markRead(readAt);
    em.flush();
    em.clear();

    Message found = em.find(Message.class, saved.getId());
    assertThat(found.isRead()).isTrue();
    assertThat(found.getReadAt()).isEqualTo(readAt);
  }

  @Test
  @DisplayName("이미 읽은 메시지를 또 읽음 표시해도 처음 읽은 시각이 그대로다")
  void markReadKeepsFirstTime() {
    Message message = new Message(1L, MessageKind.CARROT, "잘했어", SENT);
    message.markRead(SENT.plusSeconds(60));

    message.markRead(SENT.plusSeconds(3600));

    assertThat(message.getReadAt()).isEqualTo(SENT.plusSeconds(60));
  }

  @ParameterizedTest
  @EnumSource(MessageKind.class)
  @DisplayName("정해 둔 종류 다섯 가지는 모두 저장하고 꺼낼 수 있다")
  void everyKindCanBeSaved(MessageKind kind) {
    Message saved = em.persistFlushFind(new Message(1L, kind, "내용", SENT));

    assertThat(saved.getKind()).isEqualTo(kind);
  }

  @Test
  @DisplayName("종류는 숫자가 아니라 글자(NUDGE)로 저장된다")
  void kindIsStoredAsText() {
    Message saved = em.persistFlushFind(new Message(1L, MessageKind.NUDGE, "내용", SENT));

    Object stored =
        em.getEntityManager()
            .createNativeQuery("select cast(kind as varchar) from messages where id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
    assertThat(stored).isEqualTo("NUDGE");
  }

  @Test
  @DisplayName("정해진 종류 밖의 값은 SQL 로 직접 넣어도 DB 가 거절한다")
  void dbRejectsUnknownKind() {
    assertThatThrownBy(
            () ->
                em.getEntityManager()
                    .createNativeQuery(
                        "insert into messages (user_id, kind, text, sent_at) values (1, 'SHOUT', '내용', current_timestamp)")
                    .executeUpdate())
        .isInstanceOf(DataException.class)
        .hasMessageContaining("SHOUT");
  }

  @Test
  @DisplayName("메시지 내용은 500자까지 저장된다")
  void textMaxLength() {
    Message saved = em.persistFlushFind(new Message(1L, MessageKind.EVENT, "가".repeat(500), SENT));

    assertThat(saved.getText()).hasSize(500);
  }

  @Test
  @DisplayName("메시지 내용이 501자면 DB 가 거절한다")
  void textTooLong() {
    Message tooLong = new Message(1L, MessageKind.EVENT, "가".repeat(501), SENT);

    assertThatThrownBy(() -> em.persistAndFlush(tooLong)).isInstanceOf(DataException.class);
  }

  @Test
  @DisplayName("주인(userId)·종류·내용·보낸 시각은 비어 있으면 DB 가 거절한다")
  void requiredColumns() {
    assertThatThrownBy(() -> em.persistAndFlush(new Message(null, MessageKind.NUDGE, "내용", SENT)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("종류가 비어 있으면 DB 가 거절한다")
  void kindRequired() {
    assertThatThrownBy(() -> em.persistAndFlush(new Message(1L, null, "내용", SENT)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("내용이 비어 있으면(null) DB 가 거절한다")
  void textRequired() {
    assertThatThrownBy(() -> em.persistAndFlush(new Message(1L, MessageKind.NUDGE, null, SENT)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("보낸 시각이 비어 있으면 DB 가 거절한다")
  void sentAtRequired() {
    assertThatThrownBy(() -> em.persistAndFlush(new Message(1L, MessageKind.NUDGE, "내용", null)))
        .isInstanceOf(ConstraintViolationException.class);
  }
}
