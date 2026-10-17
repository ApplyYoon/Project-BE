package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.message.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI 메시지 API 의 입구입니다. 요청을 받아 MessageService 를 부르기만 합니다. (docs/API.md "AI 메시지")
// 메시지를 만드는 주소는 없습니다. 메시지는 서버가 만들고, 사용자는 보고 읽음 표시만 합니다.
@Tag(name = "메시지")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

  private final MessageService messageService;

  @Operation(summary = "내 AI 메시지 목록 (최신순 20개씩 · 토큰 필요)")
  @GetMapping
  public List<MessageResponse> getMessages(
      @Parameter(description = "이 번호보다 나중에 온 메시지만 (새로 온 것 받기). 예: 15")
          @RequestParam(required = false)
          Long after,
      @Parameter(description = "이 번호보다 먼저 온 메시지만 (더 오래된 것 받기). 예: 15")
          @RequestParam(required = false)
          Long before) {
    return messageService.getMessages(after, before);
  }

  @Operation(summary = "메시지 읽음 표시 (토큰 필요)")
  @PatchMapping("/{id}/read")
  public MessageResponse markRead(@PathVariable Long id) {
    return messageService.markRead(id);
  }
}
