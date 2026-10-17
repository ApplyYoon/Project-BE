package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

// "구글로 시작하기" 요청의 모양입니다. (FE src/lib/authApi.ts)
//   { "credential": "eyJhbGciOi…" }
// credential 은 구글이 FE 에게 준 ID 토큰입니다. 서버가 구글에 진짜인지 물어봅니다. (GoogleIdTokenVerifier)
public record GoogleLoginRequest(@NotBlank(message = "구글 확인 정보를 보내 주세요") String credential) {}
