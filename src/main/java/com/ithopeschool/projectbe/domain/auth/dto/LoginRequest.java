package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인 요청의 모양입니다. (FE src/lib/authApi.ts 의 loginWithPassword)
// 여기서는 "비어 있지 않은지" 만 봅니다. 맞는 이메일·비밀번호인지는 AuthService 가 DB 와 비교해서 확인합니다.
public record LoginRequest(
    @NotBlank(message = "이메일을 입력해 주세요") String email,
    @NotBlank(message = "비밀번호를 입력해 주세요") String password) {}
