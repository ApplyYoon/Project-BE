package com.ithopeschool.projectbe.domain.card;

import com.ithopeschool.projectbe.domain.card.dto.CardCreateRequest;
import com.ithopeschool.projectbe.domain.card.dto.CardResponse;
import com.ithopeschool.projectbe.domain.card.dto.CardUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 카드 API 의 입구입니다. 요청을 받아 CardService 를 부르기만 합니다. (docs/API.md "카드", 이슈 #222)
// 네 주소 모두 로그인이 필요합니다.
@Tag(name = "카드")
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

  private final CardService cardService;

  // date 는 보내지 않아도 됩니다. 보냈는데 모양이 틀리면(2026/10/12) Spring 이 막고,
  // GlobalExceptionHandler 가 400 VALIDATION_ERROR 로 바꿔 답합니다.
  @Operation(summary = "내 카드 목록 (date 를 보내면 그날 하는 카드만 시작 시각 순 · 안 보내면 전부 만든 순 · 토큰 필요)")
  @GetMapping
  public List<CardResponse> getCards(
      @Parameter(description = "이 날짜에 하는 카드만. 예: 2026-10-12 (안 보내면 전부)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return cardService.getCards(date);
  }

  @Operation(summary = "카드 만들기 (제목·날짜·길이 1~600분·체크리스트 1~20개 필수 · 반복 요일 0~6)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CardResponse create(@Valid @RequestBody CardCreateRequest request) {
    return cardService.create(request);
  }

  @Operation(summary = "카드 바꾸기 (바꿀 것만 보냄 · 목록은 통째로 바꿈 · 빈 값으로 지우기)")
  @PatchMapping("/{id}")
  public CardResponse update(@PathVariable Long id, @Valid @RequestBody CardUpdateRequest request) {
    return cardService.update(id, request);
  }

  @Operation(summary = "카드 지우기")
  @DeleteMapping("/{id}")
  // 돌려줄 내용이 없어서 204 로 답합니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    cardService.delete(id);
  }
}
