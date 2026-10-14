package com.ithopeschool.projectbe.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 알림을 끌 때 FE 가 보내는 모양입니다.   { "endpoint": "https://fcm.googleapis.com/fcm/send/…" }
public record PushUnsubscribeRequest(
    @NotBlank(message = "구독 주소(endpoint)를 보내 주세요")
        @Size(max = 500, message = "구독 주소(endpoint)는 500자까지예요")
        String endpoint) {}
