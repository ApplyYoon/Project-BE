package com.ithopeschool.projectbe.domain.consent;

import com.ithopeschool.projectbe.domain.consent.dto.ConsentAgreeRequest;
import com.ithopeschool.projectbe.domain.consent.dto.ConsentStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
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

// "개인정보 동의" API 의 입구입니다. 두 주소 모두 로그인이 필요합니다. (docs/API.md "동의")
// 주소가 /auth 아래인 이유: 가입·내 정보와 같은 "계정" 이야기라서, FE 가 한 곳에서 찾게 하려는 것입니다.
@Tag(name = "동의")
@RestController
@RequestMapping("/api/v1/auth/consent")
@RequiredArgsConstructor
public class ConsentController {

  private final ConsentService consentService;

  @Operation(summary = "내 동의 상태 (지금 받는 문구 버전 · 내가 동의한 버전 · 다시 동의가 필요한지)")
  @GetMapping
  public ConsentStatusResponse getStatus() {
    return consentService.getStatus();
  }

  @Operation(summary = "다시 동의하기 (문구가 바뀌었을 때 · 새 동의 기록이 쌓임 · 이미 최신 문구에 동의했으면 409)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConsentStatusResponse agreeAgain(@Valid @RequestBody ConsentAgreeRequest request) {
    return consentService.agreeAgain(request);
  }
}
