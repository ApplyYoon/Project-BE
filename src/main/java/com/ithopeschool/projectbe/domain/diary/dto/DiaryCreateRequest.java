package com.ithopeschool.projectbe.domain.diary.dto;

import com.ithopeschool.projectbe.domain.diary.Diary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 일기 쓰기 요청입니다.   { "content": "오늘은 폰을 보다가 수학을 못 했다." }
// 날짜·시각·주인은 서버가 정합니다.
public record DiaryCreateRequest(
    @NotBlank(message = "일기 내용을 적어 주세요")
        @Size(max = Diary.MAX_CONTENT_LENGTH, message = "일기는 2000자까지 쓸 수 있어요")
        String content) {}
