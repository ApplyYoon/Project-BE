package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 가입 요청의 모양입니다. FE 의 가입 화면이 보내는 값과 이름이 같습니다. (FE src/lib/authApi.ts)
// 검사 규칙은 FE(src/lib/signup.ts)와 같습니다. FE 가 먼저 걸러 주지만, 서버도 반드시 한 번 더 검사합니다.
// (FE 를 거치지 않고 서버를 직접 부르는 사람도 있기 때문입니다)
public record SignupRequest(
    // @Email: 이메일 모양(@ 앞뒤에 글자가 있는지 등)인지 검사합니다. 진짜 있는 주소인지까지는 알 수 없습니다.
    @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "이메일 주소를 다시 확인해 주세요")
        @Size(max = 100, message = "이메일은 100자 이하로 적어 주세요")
        String email,
    // ".*[a-zA-Z].*" 는 "영문이 하나라도 있다", ".*[0-9].*" 는 "숫자가 하나라도 있다" 는 뜻입니다.
    @NotNull(message = "비밀번호를 입력해 주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 적어 주세요")
        @Pattern(regexp = ".*[a-zA-Z].*", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요")
        @Pattern(regexp = ".*[0-9].*", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요")
        String password,
    // 실명
    @NotNull(message = "이름을 입력해 주세요") @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하로 적어 주세요")
        String name,
    // 앱에서 부르는 이름 (users.nickname 에 저장합니다)
    @NotNull(message = "닉네임을 입력해 주세요")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 적어 주세요")
        String nickname,
    // 고른 고등학교의 번호와 이름. 고르지 않았으면 둘 다 null 입니다.
    @Size(max = 20, message = "학교 번호가 너무 길어요") String schoolCode,
    @Size(max = 100, message = "학교 이름이 너무 길어요") String schoolName,
    // @Valid 를 붙여야 안쪽(Consents)의 검사도 같이 돌아갑니다.
    @NotNull(message = "약관 동의 내용을 보내 주세요") @Valid Consents consents) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  // 비밀번호는 공백도 비밀번호의 일부일 수 있어서 건드리지 않습니다.
  public SignupRequest {
    email = email == null ? null : email.trim();
    name = name == null ? null : name.trim();
    nickname = nickname == null ? null : nickname.trim();
  }

  // 동의 항목 세 가지. true 면 동의했다는 뜻입니다. (FE 의 SignupConsents)
  public record Consents(
      @AssertTrue(message = "서비스 이용약관에 동의해 주세요") boolean terms,
      @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요") boolean privacy,
      // [선택] 일기·감정 기록을 분석에 쓰기. 동의하지 않아도 가입할 수 있습니다.
      boolean diaryAnalysis) {}
}
