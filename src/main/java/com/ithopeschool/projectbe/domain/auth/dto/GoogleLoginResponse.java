package com.ithopeschool.projectbe.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// "구글로 시작하기" 의 응답 모양입니다. 두 가지 중 하나입니다.
//   이미 가입한 구글 계정 : { "status": "LOGGED_IN", "token": "Zx3k…", "user": { "id": "1", "name": "티맥이" }
// }
//   처음 온 구글 계정     : { "status": "SIGNUP_REQUIRED", "name": "김티맥", "email": "tmax@gmail.com" }
// 처음 온 사람은 아직 약관에 동의하지 않아서 계정을 만들지 않습니다. FE 가 가입 화면에서 동의를 받은 뒤
// POST /auth/google/signup 으로 가입합니다. name · email 은 가입 화면을 미리 채우는 데 씁니다.
//
// @JsonInclude(NON_NULL): 비어 있는 칸(null)은 응답에 넣지 않습니다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleLoginResponse(
    String status, String token, UserResponse user, String name, String email) {

  public static final String LOGGED_IN = "LOGGED_IN";
  public static final String SIGNUP_REQUIRED = "SIGNUP_REQUIRED";

  public static GoogleLoginResponse loggedIn(LoginResponse login) {
    return new GoogleLoginResponse(LOGGED_IN, login.token(), login.user(), null, null);
  }

  public static GoogleLoginResponse signupRequired(String name, String email) {
    return new GoogleLoginResponse(SIGNUP_REQUIRED, null, null, name, email);
  }
}
