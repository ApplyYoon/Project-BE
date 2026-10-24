package com.ithopeschool.projectbe.domain.answercheck.dto;

import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.answercheck.AnswerCheckMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// 사진 채점 요청의 모양입니다.
//   { "mode": "UNMARKED", "image": "data:image/jpeg;base64,/9j/4AAQ...", "date": "2026-10-24" }
// - mode: MARKED(채점했어요) 또는 UNMARKED(아직 안 했어요). 학생이 폰에서 고릅니다.
// - image: 사진 한 장을 data URL 로 (jpeg · png · webp). 4MB 까지.
//          모양 · 크기 검사는 CardPhotoService.checkImage 를 같이 씁니다.
// - date: 어느 날 공부한 것인지 (보통 오늘)
public record AnswerCheckCreateRequest(
    @NotNull(message = "채점 모드(MARKED 또는 UNMARKED)를 골라 주세요") AnswerCheckMode mode,
    @NotBlank(message = "사진을 보내 주세요")
        @Size(max = CardPhotoRequest.MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @NotNull(message = "날짜를 보내 주세요") LocalDate date) {}
