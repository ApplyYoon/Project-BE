package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 시연 코드 로그인 요청의 모양입니다. (FE src/lib/authApi.ts 의 loginWithDemoCode, 2026-10-18)
//   { "code": "시연 때 알려 준 코드" }
// 여기서는 "비어 있지 않은지" 와 길이만 봅니다. 맞는 코드인지는 DemoLoginService 가 확인합니다.
public record DemoLoginRequest(
    @NotBlank(message = "시연 코드를 입력해 주세요") @Size(max = 100, message = "시연 코드가 너무 길어요")
        String code) {}
