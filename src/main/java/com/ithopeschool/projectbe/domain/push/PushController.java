package com.ithopeschool.projectbe.domain.push;

import com.ithopeschool.projectbe.domain.push.dto.PushPublicKeyResponse;
import com.ithopeschool.projectbe.domain.push.dto.PushSubscribeRequest;
import com.ithopeschool.projectbe.domain.push.dto.PushUnsubscribeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 웹 푸시(앱을 닫아도 오는 알림) API 의 입구입니다. 세 주소 모두 로그인이 필요합니다. (docs/API.md "웹 푸시")
@Tag(name = "웹 푸시")
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

  private final PushSubscriptionService pushSubscriptionService;

  @Operation(summary = "구독할 때 쓰는 서버 공개 키 (배포 서버에서만 켜짐 · 꺼져 있으면 503)")
  @GetMapping("/public-key")
  public PushPublicKeyResponse getPublicKey() {
    return pushSubscriptionService.getPublicKey();
  }

  // 같은 기기가 다시 보내도 결과가 같아서(덮어씀) 201 대신 204 로 답합니다.
  @Operation(summary = "알림 켜기 (브라우저 구독 저장 · 같은 기기는 덮어씀)")
  @PostMapping("/subscriptions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void subscribe(@Valid @RequestBody PushSubscribeRequest request) {
    pushSubscriptionService.subscribe(request);
  }

  @Operation(summary = "알림 끄기 (구독 지우기 · 없어도 204)")
  @DeleteMapping("/subscriptions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
    pushSubscriptionService.unsubscribe(request);
  }
}
