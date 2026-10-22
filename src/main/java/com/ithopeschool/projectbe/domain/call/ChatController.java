package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 상담 채팅 API 의 입구입니다. 로그인이 필요합니다. (이슈 #238, docs/API.md "상담 채팅")
// 채팅은 "글로 하는 통화" 라서, 끝낸 채팅은 내 통화 기록(GET /api/v1/calls)에도 같이 나옵니다.
@Tag(name = "상담 채팅")
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

  private final ChatService chatService;

  // 채팅 기록을 하나 만들기는 하지만, 응답의 중심이 "AI 의 첫 말" 이라 말 보내기와 같이 200 으로 답합니다.
  // grade · classNo 를 주면 그 반의 오늘 시간표도 대본에 넣습니다. (반은 서버에 저장하지 않고 FE 가 보냅니다)
  // greet=false 면 AI 의 첫 인사 없이 채팅 번호만 만듭니다. 학생이 먼저 말을 보내는 화면에서 씁니다. (채팅 수정 1)
  @Operation(
      summary =
          "상담 채팅 열기 (통화와 같은 대본 + 오늘 학교 정보 · 숫자는 서버 기록 · AI 의 첫 인사(greet=false 면 생략) · 배포 서버에서만 켜짐)")
  @PostMapping
  public ChatReplyResponse start(
      @Valid @RequestBody CallSessionRequest request,
      @RequestParam(required = false) Integer grade,
      @RequestParam(required = false) Integer classNo,
      @RequestParam(defaultValue = "true") boolean greet) {
    return chatService.start(request, grade, classNo, greet);
  }

  @Operation(summary = "상담 채팅에 말 보내기 (지금까지의 대화 전체를 보냄 · 위기 표현이면 도움 안내 · 대화는 저장 안 함)")
  @PostMapping("/{id}/messages")
  public ChatReplyResponse send(
      @PathVariable Long id, @Valid @RequestBody ChatMessageRequest request) {
    return chatService.send(id, request);
  }

  @Operation(summary = "상담 채팅 끝내기 (통화 마무리와 같이 요약·생각 습관·약속·체크인 저장 · 대화는 저장 안 함)")
  @PostMapping("/{id}/finish")
  public CallFinishResponse finish(
      @PathVariable Long id, @Valid @RequestBody CallFinishRequest request) {
    return chatService.finish(id, request);
  }
}
