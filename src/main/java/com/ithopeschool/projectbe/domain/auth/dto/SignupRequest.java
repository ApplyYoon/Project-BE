package com.ithopeschool.projectbe.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
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
    // 호환용 자리입니다. 값은 읽지 않으며 항상 null입니다. (#71)
    @JsonIgnore @Schema(hidden = true) String name,
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
    // 예전 FE가 name을 보내도 실명은 로직에 전달하거나 저장하지 않습니다.
    name = null;
    nickname = nickname == null ? null : nickname.trim();
  }

  // 동의 항목과 만 14세 확인, 동의한 문구의 버전. true 면 동의(확인)했다는 뜻입니다. (FE 의 SignupConsents)
  // 구글 가입(GoogleSignupRequest)과 다시 동의하기(ConsentAgreeRequest)도 같은 규칙을 씁니다.
  // 필수 true/false 칸을 false 로 보내면 아래 문장의 400 이 됩니다. 칸을 아예 안 보내면 값을 읽을 수 없는 요청이라
  // 기본 문장("보낸 값을 다시 확인해 주세요")의 400 이 됩니다. 어느 쪽이든 가입은 끝나지 않습니다.
  public record Consents(
      @AssertTrue(message = "서비스 이용약관에 동의해 주세요") boolean terms,
      @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요") boolean privacy,
      // [선택] 일기·감정 기록을 분석에 쓰기. 동의하지 않아도 가입할 수 있습니다.
      boolean diaryAnalysis,
      // [필수] 만 14세 이상인지 확인. 아니면 가입할 수 없습니다. (생년월일은 받지 않고 "네" 만 확인합니다)
      @AssertTrue(message = "만 14세 이상만 가입할 수 있어요") boolean over14,
      // [필수] 화면에 보여 준 동의 문구의 버전. 지금 받는 버전(ConsentPolicy)과 같아야 합니다.
      @NotBlank(message = "동의 문구 버전(version)을 보내 주세요") @Size(max = 20, message = "동의 문구 버전이 너무 길어요")
          String version) {}
}
