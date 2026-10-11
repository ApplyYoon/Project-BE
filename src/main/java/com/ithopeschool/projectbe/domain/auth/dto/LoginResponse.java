package com.ithopeschool.projectbe.domain.auth.dto;

// 로그인에 성공했을 때의 응답 모양입니다. (FE src/lib/authApi.ts 의 AuthResult)
//   { "token": "Zx3k…", "user": { "id": "1", "name": "김티맥" } }
// token 은 FE 가 저장해 두었다가 요청마다 `Authorization: Bearer 토큰` 으로 같이 보냅니다.
public record LoginResponse(String token, UserResponse user) {}
