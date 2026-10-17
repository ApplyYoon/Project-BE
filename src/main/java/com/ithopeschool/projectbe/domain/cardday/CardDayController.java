package com.ithopeschool.projectbe.domain.cardday;

import com.ithopeschool.projectbe.domain.cardday.dto.CardDayMarkResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.CardMarkResultResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.FailRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.LateEntryRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeLimitResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 카드 하루치 처리(미루기 · 실패 · 사후 입력) API 의 입구입니다. CardDayService 를 부르기만 합니다. (이슈 #225)
// docs/API.md "미루기 · 실패 · 사후 입력". 모든 주소가 로그인이 필요합니다.
@Tag(name = "미루기 · 실패 · 사후 입력")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CardDayController {

  private final CardDayService cardDayService;

  @Operation(summary = "그 카드·그 날짜를 몇 번 더 미룰 수 있는지 (반복 카드 월 5회 · 1회성 2회 · 절대 0회 · 토큰 필요)")
  @GetMapping("/cards/{id}/postpone-limit")
  public PostponeLimitResponse getPostponeLimit(
      @PathVariable Long id,
      @Parameter(description = "어느 날의 카드인지. 예: 2026-10-12", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return cardDayService.getPostponeLimit(id, date);
  }

  @Operation(summary = "카드 미루기 (이유 100자 이상 + 새 날짜·시각 · 한도 넘으면 409 · 토큰 필요)")
  @PostMapping("/cards/{id}/postpone")
  @ResponseStatus(HttpStatus.CREATED)
  public CardMarkResultResponse postpone(
      @PathVariable Long id, @Valid @RequestBody PostponeRequest request) {
    return cardDayService.postpone(id, request);
  }

  @Operation(summary = "카드를 실패로 기록 (원인 태그 필수 · 그날 한 번만 · 토큰 필요)")
  @PostMapping("/cards/{id}/fail")
  @ResponseStatus(HttpStatus.CREATED)
  public CardMarkResultResponse fail(
      @PathVariable Long id, @Valid @RequestBody FailRequest request) {
    return cardDayService.fail(id, request);
  }

  @Operation(summary = "사후 입력 (미리 만든 카드 · 그날 다음 통화 전까지 · 카드 길이 이하 · 토큰 필요)")
  @PostMapping("/cards/{id}/late-entry")
  @ResponseStatus(HttpStatus.CREATED)
  public StudySessionResponse lateEntry(
      @PathVariable Long id, @Valid @RequestBody LateEntryRequest request) {
    return cardDayService.lateEntry(id, request);
  }

  // from · to 는 꼭 보내야 합니다. 안 보내거나 모양이 틀리면 400 입니다.
  @Operation(summary = "기간 안의 내 미루기·실패 표시 목록 (from~to 포함 · 31일까지 · 토큰 필요)")
  @GetMapping("/card-day-marks")
  public List<CardDayMarkResponse> getMarks(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-31", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return cardDayService.getMarks(from, to);
  }
}
