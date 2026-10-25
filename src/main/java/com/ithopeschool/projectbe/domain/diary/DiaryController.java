package com.ithopeschool.projectbe.domain.diary;

import com.ithopeschool.projectbe.domain.diary.dto.DiaryCreateRequest;
import com.ithopeschool.projectbe.domain.diary.dto.DiaryResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "일기" API 의 입구입니다. 요청을 받아 DiaryService 를 부르기만 합니다. 로그인이 필요합니다. (이슈 #60)
// 일기 원문은 본인만 봅니다.
@Tag(name = "일기")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

  private final DiaryService diaryService;

  @Operation(summary = "일기 쓰기 (위기 표현 감지 후 저장 · 분석 동의 시 AI 가 감정·원인·키워드를 뽑음 · 토큰 필요)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DiaryResponse create(@Valid @RequestBody DiaryCreateRequest request) {
    return diaryService.create(request);
  }

  @Operation(summary = "기간 안의 내 일기 목록 (최근에 쓴 것부터 · from~to 포함 · 31일까지 · 토큰 필요)")
  @GetMapping
  public List<DiaryResponse> getDiaries(
      @Parameter(description = "시작 날짜(포함). 예: 2026-10-01", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "끝 날짜(포함). 예: 2026-10-24", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return diaryService.getDiaries(from, to);
  }

  // 지운 뒤에는 돌려줄 것이 없어서 204(본문 없음)입니다.
  @Operation(summary = "내 일기 하나 지우기 (뽑은 감정·원인·키워드도 같이 지움 · 되돌릴 수 없음 · 토큰 필요)")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@Parameter(description = "일기 번호", required = true) @PathVariable Long id) {
    diaryService.delete(id);
  }
}
