package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// "구글로 가입하기" 요청의 모양입니다. 이메일 가입(SignupRequest)과 같고, 이메일·비밀번호 대신 구글 확인 정보를 받습니다.
// 이메일은 구글이 확인한 값을 쓰기 때문에 받지 않습니다. (FE 가 바꿔 보내도 소용없게)
// 검사 규칙은 SignupRequest 와 같습니다.
public record GoogleSignupRequest(
    @NotBlank(message = "구글 확인 정보를 보내 주세요") String credential,
    // 실명. 선택입니다. 안 보내도 가입됩니다. (SignupRequest 와 같음. 이슈 #71)
    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하로 적어 주세요") String name,
    // 앱에서 부르는 이름 (users.nickname 에 저장합니다)
    @NotNull(message = "닉네임을 입력해 주세요")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 적어 주세요")
        String nickname,
    // 고른 고등학교의 번호와 이름. 고르지 않았으면 둘 다 null 입니다.
    @Size(max = 20, message = "학교 번호가 너무 길어요") String schoolCode,
    @Size(max = 100, message = "학교 이름이 너무 길어요") String schoolName,
    @NotNull(message = "약관 동의 내용을 보내 주세요") @Valid SignupRequest.Consents consents) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public GoogleSignupRequest {
    name = name == null || name.isBlank() ? null : name.trim();
    nickname = nickname == null ? null : nickname.trim();
  }
}
