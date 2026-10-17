package com.ithopeschool.projectbe.domain.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 알림을 켤 때 FE 가 보내는 구독 정보입니다. 브라우저가 준 PushSubscription.toJSON() 을 그대로 보냅니다.
//   { "endpoint": "https://fcm.googleapis.com/fcm/send/…",
//     "expirationTime": null,
//     "keys": { "p256dh": "BNc…", "auth": "tBH…" } }
// expirationTime 처럼 쓰지 않는 칸은 무시합니다.
public record PushSubscribeRequest(
    // 푸시를 보낼 주소. 브라우저 회사의 https 주소입니다.
    @NotBlank(message = "구독 주소(endpoint)를 보내 주세요")
        @Size(max = 500, message = "구독 주소(endpoint)는 500자까지예요")
        @Pattern(regexp = "(https://\\S+)?", message = "구독 주소(endpoint)는 https:// 로 시작해야 해요")
        String endpoint,
    @NotNull(message = "구독 열쇠(keys)를 보내 주세요") @Valid Keys keys) {

  // 알림 내용을 잠그는 데 쓰는 열쇠 두 개. 브라우저가 base64url 글자로 줍니다.
  // 모양 검사(@Pattern)는 빈 글자도 통과시킵니다. 빈 글자는 @NotBlank 가 먼저 알려 주기 때문입니다.
  // (둘 다 걸리면 어느 안내 문장이 나갈지 그때그때 달라져서, 안내가 늘 하나로 정해지게 나눴습니다)
  public record Keys(
      @NotBlank(message = "keys.p256dh 를 보내 주세요")
          @Size(max = 200, message = "keys.p256dh 는 200자까지예요")
          @Pattern(regexp = "[A-Za-z0-9_\\-]*=*", message = "keys.p256dh 의 모양이 올바르지 않아요")
          String p256dh,
      @NotBlank(message = "keys.auth 를 보내 주세요")
          @Size(max = 100, message = "keys.auth 는 100자까지예요")
          @Pattern(regexp = "[A-Za-z0-9_\\-]*=*", message = "keys.auth 의 모양이 올바르지 않아요")
          String auth) {}
}
