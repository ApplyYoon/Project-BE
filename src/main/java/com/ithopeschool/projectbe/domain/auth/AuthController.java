package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleLoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleSignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginResponse;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 가입·로그인 API 의 입구입니다. 요청을 받아 검사(@Valid)하고 AuthService 를 부르기만 합니다. (docs/API.md "가입 · 로그인")
// 주소가 /auths 가 아니라 /auth 인 이유: 목록이 있는 "무엇" 이 아니라 가입·로그인이라는 "동작" 이라서입니다.
//
// @SecurityRequirements: 가입과 로그인은 로그인하기 전에 부르는 주소라서 토큰이 필요 없습니다. (Swagger 의 자물쇠 표시를 뺍니다)
@Tag(name = "가입·로그인")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "이메일로 가입하기 (계정만 만들고 로그인은 하지 않음)")
  @SecurityRequirements
  @PostMapping("/signup")
  // 새로 만들었으므로 200 이 아니라 201 을 돌려줍니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse signup(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  @Operation(summary = "이메일·비밀번호로 로그인하기 (로그인 토큰을 돌려줌)")
  @SecurityRequirements
  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @Operation(summary = "구글로 시작하기 (가입한 구글 계정이면 로그인 토큰, 처음이면 가입 필요)")
  @SecurityRequirements
  @PostMapping("/google")
  public GoogleLoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
    return authService.googleLogin(request);
  }

  @Operation(summary = "구글로 가입하기 (약관 동의를 받아 가입하고 바로 로그인 토큰을 돌려줌)")
  @SecurityRequirements
  @PostMapping("/google/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public LoginResponse googleSignup(@Valid @RequestBody GoogleSignupRequest request) {
    return authService.googleSignup(request);
  }

  // 로그인이 필요한 주소입니다. Swagger 에서는 오른쪽 위 Authorize 에 토큰을 넣고 부릅니다.
  @Operation(summary = "지금 로그인한 사람이 누구인지 확인하기 (토큰 필요)")
  @GetMapping("/me")
  public UserResponse me() {
    return authService.me();
  }
}
