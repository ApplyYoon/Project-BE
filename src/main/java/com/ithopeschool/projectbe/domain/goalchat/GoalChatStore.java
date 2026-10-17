package com.ithopeschool.projectbe.domain.goalchat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 열려 있는 '목표 만들기' 대화를 기억해 두는 곳입니다. (이슈 #239)
// 목표 대화는 통화 기록(call_records)에 남기지 않아서, "누가 연 대화인지" 만 서버 메모리에 잠깐 적어 둡니다.
// 대화 내용은 적지 않습니다. FE 가 들고 있다가 매번 보냅니다.
//
// ⚠️ 서버가 다시 켜지거나 2시간이 지나면 사라지고, 그러면 "새로 시작해 주세요"(404) 입니다. (상담 채팅의 ChatScriptStore 와 같은 시간)
@Component
@RequiredArgsConstructor
public class GoalChatStore {

  // 기억해 두는 대화의 최대 개수. 이만큼 차면 전부 비웁니다. (메모리가 끝없이 늘지 않게)
  static final int MAX_SIZE = 500;

  // 대화를 연 뒤 기억해 두는 시간
  static final Duration KEEP = Duration.ofHours(2);

  // 기억해 둔 대화 하나. 누구의 대화인지 같이 적어 두어 남의 대화 번호로는 이어갈 수 없게 합니다.
  private record Opened(Long userId, Instant openedAt) {}

  private final Map<String, Opened> openedByChatId = new HashMap<>();
  private final Clock clock;

  // 대화를 열고 대화 번호를 돌려줍니다. 번호는 짐작할 수 없는 무작위 글자입니다. (예: "3f2a…")
  // synchronized: 여러 요청이 동시에 와도 한 번에 하나씩만 들어옵니다. (ChatScriptStore 와 같은 이유)
  public synchronized String open(Long userId) {
    if (openedByChatId.size() >= MAX_SIZE) {
      openedByChatId.clear();
    }
    String chatId = UUID.randomUUID().toString();
    openedByChatId.put(chatId, new Opened(userId, clock.instant()));
    return chatId;
  }

  // 그 사람이 연 대화이고 아직 2시간이 지나지 않았으면 true
  public synchronized boolean isOpen(String chatId, Long userId) {
    Opened opened = openedByChatId.get(chatId);
    if (opened == null || !opened.userId().equals(userId)) {
      return false;
    }
    if (clock.instant().isAfter(opened.openedAt().plus(KEEP))) {
      openedByChatId.remove(chatId);
      return false;
    }
    return true;
  }

  // 대화가 끝나면 지웁니다.
  public synchronized void close(String chatId) {
    openedByChatId.remove(chatId);
  }
}
