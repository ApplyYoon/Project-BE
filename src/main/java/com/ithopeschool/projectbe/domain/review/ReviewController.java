package com.ithopeschool.projectbe.domain.review;

import com.ithopeschool.projectbe.domain.review.dto.ReviewResponse;
import com.ithopeschool.projectbe.domain.review.dto.ReviewSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회고 API 의 입구입니다. 요청을 받아 ReviewService 를 부르기만 합니다. 로그인이 필요합니다. (docs/API.md "회고")
@Tag(name = "회고 (시험 후 · 주간)")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  // 새로 만들 때도, 덮어쓸 때도 200 을 돌려줍니다. (FE 는 어느 쪽인지 신경 쓰지 않아도 됩니다)
  @Operation(
      summary = "회고 저장 (type exam·weekly · 답 3개, 하나에 200자까지 · 같은 시험·같은 주에 다시 쓰면 덮어씀 · 토큰 필요)")
  @PostMapping
  public ReviewResponse save(@Valid @RequestBody ReviewSaveRequest request) {
    return reviewService.save(request);
  }
}
