package com.ithopeschool.projectbe.domain.goalchat;

import com.ithopeschool.projectbe.domain.call.dto.ChatMessageRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.goalchat.dto.GoalChatFinishResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// '목표 만들기' 대화 API 의 입구입니다. 로그인이 필요합니다. (이슈 #239, docs/API.md "목표 만들기 대화")
// 말을 주고받는 모양은 상담 채팅(/api/v1/chats)과 같아서 FE 의 채팅 화면을 그대로 씁니다.
@Tag(name = "목표 만들기 대화")
@RestController
@RequestMapping("/api/v1/goal-chats")
@RequiredArgsConstructor
public class GoalChatController {

  private final GoalChatService goalChatService;

  // 상담 채팅 열기와 같이 200 으로 답합니다. (응답의 중심이 "AI 의 첫 말" 입니다)
  @Operation(summary = "목표 만들기 대화 열기 (WOOP 순서 · 정해 둔 첫 질문 · 배포 서버에서만 켜짐)")
  @PostMapping
  public ChatReplyResponse start() {
    return goalChatService.start();
  }

  @Operation(summary = "목표 만들기 대화에 말 보내기 (지금까지의 대화 전체를 보냄 · 위기 표현이면 도움 안내 · 대화는 저장 안 함)")
  @PostMapping("/{id}/messages")
  public ChatReplyResponse send(
      @PathVariable String id, @Valid @RequestBody ChatMessageRequest request) {
    return goalChatService.send(id, request);
  }

  @Operation(summary = "목표 만들기 대화 끝내기 (목표 초안을 돌려줌 · 저장은 FE 가 확인받은 뒤 POST /goals)")
  @PostMapping("/{id}/finish")
  public GoalChatFinishResponse finish(
      @PathVariable String id, @Valid @RequestBody ChatMessageRequest request) {
    return goalChatService.finish(id, request);
  }
}
