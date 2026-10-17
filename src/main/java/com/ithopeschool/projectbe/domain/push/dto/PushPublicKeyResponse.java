package com.ithopeschool.projectbe.domain.push.dto;

// 브라우저가 구독할 때 쓰는 서버의 공개 키입니다.   { "publicKey": "BEl62…" }
// 공개 키는 비밀값이 아닙니다. (짝이 되는 개인 키는 서버 밖으로 나가지 않습니다)
public record PushPublicKeyResponse(String publicKey) {}
