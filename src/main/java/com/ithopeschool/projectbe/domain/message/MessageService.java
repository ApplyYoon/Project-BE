package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.message.dto.MessageResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI 메시지를 저장하고, 보여 주고, 읽음 표시를 하는 로직입니다. (이슈 #30)
//
// 사용자가 부르는 메서드(목록·읽음)는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이
// 나가고, 내 번호로만 찾기 때문에 다른 사람의 메시지는 보이지도, 바뀌지도 않습니다. (다른 사람의 것은 "없다" 고 답합니다)
//
// ⚠️ 메시지 내용은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class MessageService {

  // 목록을 한 번에 몇 개씩 주는지
  public static final int PAGE_SIZE = 20;

  // 메시지 내용의 최대 글자 수 (Message.text 의 길이와 같습니다)
  public static final int MAX_TEXT_LENGTH = 500;

  private final CurrentUserProvider currentUserProvider;
  private final MessageRepository messageRepository;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 내 메시지 목록 (번호가 큰 순서 = 최신순, 최대 20개)
  //
  // after: 이 번호보다 큰(= 나중에 온) 메시지만. 앱을 다시 열었을 때 "새로 온 것" 만 받으려고 씁니다.
  // before: 이 번호보다 작은(= 먼저 온) 메시지만. 20개 다음 "더 오래된 것" 을 받으려고 씁니다.
  // 둘 다 비워 두면 가장 최근 20개입니다. 둘 다 주면 그 사이의 메시지입니다.
  @Transactional(readOnly = true)
  public List<MessageResponse> getMessages(Long after, Long before) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 값이 없는 쪽은 "끝없이" 로 바꿔서, 비어 있는 조건을 SQL 에 넣지 않아도 되게 합니다.
    long lowerLimit = after == null ? 0L : after;
    long upperLimit = before == null ? Long.MAX_VALUE : before;

    return messageRepository
        .findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
            userId, lowerLimit, upperLimit, PageRequest.of(0, PAGE_SIZE))
        .stream()
        .map(MessageResponse::from)
        .toList();
  }

  // 읽음 표시. 이미 읽은 메시지에 또 불러도 에러가 아니고, 처음 읽은 시각은 그대로입니다.
  @Transactional
  public MessageResponse markRead(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    Message message =
        messageRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없어요"));

    // 값을 바꾸기만 하면, 이 메서드가 끝날 때 JPA 가 바뀐 것을 알아채고 DB 에 반영합니다.
    message.markRead(Instant.now(clock));
    return MessageResponse.from(message);
  }

  // 메시지를 저장합니다. 보낸 시각은 지금입니다.
  //
  // ⚠️ 주소(API)로 열지 않은 서버 안의 함수입니다. 예약 작업(이벤트 메시지 #31, 독촉 메시지 #66 등)이
  //    "누구에게 무슨 말을 보낼지" 를 정한 뒤에 부릅니다. FE 가 AI 메시지를 마음대로 만들 수 없게 하려는 것입니다.
  //    요청한 사람이 없는 상황에서 부르므로, 받을 사람의 번호를 직접 받습니다.
  @Transactional
  public Message save(Long userId, MessageKind kind, String text) {
    if (userId == null) {
      throw new IllegalArgumentException("메시지를 받을 사람(userId)이 있어야 해요");
    }
    if (kind == null) {
      throw new IllegalArgumentException("메시지 종류(kind)가 있어야 해요");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("메시지 내용이 비어 있어요");
    }
    if (text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("메시지 내용은 " + MAX_TEXT_LENGTH + "자 이하여야 해요");
    }
    return messageRepository.save(new Message(userId, kind, text, Instant.now(clock)));
  }
}
