package com.ithopeschool.projectbe.domain.call;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 상담 채팅의 대본(지침)을 채팅이 끝날 때까지 기억해 두는 곳입니다. (이슈 #238)
// 채팅을 열 때 한 번 만든 대본을, 말을 주고받는 동안 그대로 씁니다. 그래야 채팅 중간에 질문 순서가 바뀌지 않습니다.
//
// ⚠️ 서버의 메모리에만 있습니다. 서버가 다시 켜지거나 2시간이 지나면 사라지고, 그러면 "새로 시작해 주세요"(409) 입니다.
//    대본에는 숫자 · 정해진 분류값 · 지난 통화 요약만 있고, 학생이 이번 채팅에 쓴 글은 없습니다.
// ⚠️ 대본은 로그에 찍지 않습니다.
//
// "코치랑 이야기하기" 로 연 채팅은 주제(ChatTopic)도 같이 기억합니다. (2026-10-18)
// 말을 주고받을 때 AI 가 돌려준 이유 값(tags)을 그 주제에 맞는 값만 남기려고 씁니다. 잊으면 tags 는 빈 목록입니다.
// 방(ChatRoom)도 같이 기억합니다. 사진이 붙은 말에 방마다 다른 규칙을 붙이려고 씁니다. 잊으면 코치입니다.
@Component
@RequiredArgsConstructor
public class ChatScriptStore {

  // 기억해 두는 채팅의 최대 개수. 이만큼 차면 전부 비웁니다. (메모리가 끝없이 늘지 않게)
  static final int MAX_SIZE = 500;

  // 채팅을 연 뒤 대본을 기억해 두는 시간
  static final Duration KEEP = Duration.ofHours(2);

  // 기억해 둔 대본 하나. 누구의 채팅인지 같이 적어 두어 남의 채팅 번호로는 꺼낼 수 없게 합니다.
  // topic 은 주제 없이 연 채팅이면 null 입니다.
  private record Saved(
      Long userId, String instructions, ChatTopic topic, ChatRoom room, Instant savedAt) {}

  private final Map<Long, Saved> savedByChatId = new HashMap<>();
  private final Clock clock;

  // 채팅을 열 때 대본 · 주제 · 방을 기억합니다. 주제 없이 연 채팅이면 topic 은 null 입니다.
  // synchronized: 여러 요청이 동시에 와도 한 번에 하나씩만 들어옵니다. (CoachParagraphStore 와 같은 이유)
  public synchronized void save(
      Long chatId, Long userId, String instructions, ChatTopic topic, ChatRoom room) {
    if (!savedByChatId.containsKey(chatId) && savedByChatId.size() >= MAX_SIZE) {
      savedByChatId.clear();
    }
    savedByChatId.put(chatId, new Saved(userId, instructions, topic, room, clock.instant()));
  }

  // 코치 방으로 연 채팅 (방을 따로 고르지 않은 채팅)
  public synchronized void save(Long chatId, Long userId, String instructions, ChatTopic topic) {
    save(chatId, userId, instructions, topic, ChatRoom.COACH);
  }

  // 대본만 바꿔 기억합니다. 같은 사람의 같은 채팅에 기억해 둔 주제 · 방이 있으면 그대로 둡니다.
  // (위기 표현 뒤 대본에 한 칸을 덧붙일 때 씁니다. ChatService.replyToCrisis)
  public synchronized void save(Long chatId, Long userId, String instructions) {
    Saved before = savedByChatId.get(chatId);
    boolean same = before != null && before.userId().equals(userId);
    save(
        chatId,
        userId,
        instructions,
        same ? before.topic() : null,
        same ? before.room() : ChatRoom.COACH);
  }

  // 그 사람의 그 채팅 대본. 없거나, 남의 채팅이거나, 2시간이 지났으면 비어 있습니다.
  public synchronized Optional<String> find(Long chatId, Long userId) {
    return findSaved(chatId, userId).map(Saved::instructions);
  }

  // 그 사람의 그 채팅 주제. 주제 없이 연 채팅이거나 대본을 잊었으면 비어 있습니다.
  public synchronized Optional<ChatTopic> findTopic(Long chatId, Long userId) {
    return findSaved(chatId, userId).map(Saved::topic);
  }

  // 그 사람의 그 채팅 방. 대본을 잊었으면 비어 있습니다. (부르는 쪽이 코치로 봅니다)
  public synchronized Optional<ChatRoom> findRoom(Long chatId, Long userId) {
    return findSaved(chatId, userId).map(Saved::room);
  }

  // 기억해 둔 것 하나. 없거나, 남의 채팅이거나, 2시간이 지났으면 비어 있습니다. (지난 것은 지웁니다)
  private Optional<Saved> findSaved(Long chatId, Long userId) {
    Saved saved = savedByChatId.get(chatId);
    if (saved == null || !saved.userId().equals(userId)) {
      return Optional.empty();
    }
    if (clock.instant().isAfter(saved.savedAt().plus(KEEP))) {
      savedByChatId.remove(chatId);
      return Optional.empty();
    }
    return Optional.of(saved);
  }

  // 채팅이 끝나면 지웁니다.
  public synchronized void remove(Long chatId) {
    savedByChatId.remove(chatId);
  }
}
