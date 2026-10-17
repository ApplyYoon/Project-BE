package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;

// "탈퇴" 요청의 모양입니다. 되돌릴 수 없는 일이라, 본인이 맞는지 한 번 더 확인합니다. (이슈 #69)
//   이메일로 가입한 사람:  { "password": "study2026" }        비밀번호를 다시 받습니다
//   구글로 가입한 사람:    { "credential": "eyJhbGciOi…" }   구글 로그인을 한 번 더 하고 받은 구글 ID 토큰
// 어느 쪽을 보내야 하는지는 계정마다 정해져 있어서 Service 에서 검사합니다. (구글 계정은 비밀번호가 없습니다)
public record WithdrawRequest(String password, String credential) {

  // 둘 다 비어 있으면 400 입니다. (Bean Validation 은 is 로 시작하는 메서드도 검사합니다)
  @AssertTrue(message = "비밀번호를 입력해 주세요")
  public boolean isConfirmationGiven() {
    return !isBlank(password) || !isBlank(credential);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
