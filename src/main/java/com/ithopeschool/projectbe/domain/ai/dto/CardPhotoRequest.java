package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// "시험 범위 사진으로 카드 만들기" 요청의 모양입니다. (2026-10-18, Project-App 요청)
//   { "image": "data:image/jpeg;base64,/9j/4AAQ...", "date": "2026-10-18",
//     "examDate": "2026-11-02", "note": "수학만" }
// - image: 사진 한 장을 data URL 로 (jpeg · png · webp). FE 가 긴 변 1600px 로 줄여서 보냅니다.
//          글자 수 한도(5,600,000)는 사진 4MB 를 base64 로 바꾼 길이입니다. 모양 · 크기 검사는 CardPhotoService 가 합니다.
// - date: 기준 날짜 (보통 오늘). 카드는 이 날부터 둡니다.
// - examDate: (선택) 시험 날짜. 있으면 그 전날까지 나눠 둡니다.
// - note: (선택) 한 줄 메모 (예: "수학만"). 100자까지
public record CardPhotoRequest(
    @NotBlank(message = "사진을 보내 주세요")
        @Size(max = MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @NotNull(message = "기준 날짜를 보내 주세요") LocalDate date,
    LocalDate examDate,
    @Size(max = 100, message = "메모는 100자 이하로 보내 주세요") String note) {

  // 사진 4MB 를 base64 로 바꾼 길이(약 5,592,406자)에 "data:image/jpeg;base64," 를 더한 정도
  public static final int MAX_IMAGE_CHARS = 5_600_000;

  // 값이 들어오는 순간에 메모의 앞뒤 공백을 지우고, 비었으면 없는 것(null)으로 둡니다.
  public CardPhotoRequest {
    note = note == null || note.isBlank() ? null : note.trim();
  }
}
