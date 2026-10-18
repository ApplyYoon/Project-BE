package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "긴 계획(30일)" 요청의 모양입니다. (2026-10-18)
//   { "goal": "11월 2일 중간고사 미적분 1~3단원", "endDate": "2026-11-02",
//     "dailyMinutes": 60, "startTime": "19:00", "restDays": ["SUN"],
//     "image": "data:image/jpeg;base64,...", "focus": { "kind": "SCHOOL_EXAM", "name": null } }
// - goal         : 필수. 학생이 채팅에 쓴 목표 한 줄 (100자)
// - endDate      : 필수. 시험 · 마감 날. 내일 ~ 30일 뒤 (Service 가 확인). 이 날은 공부 날에서 빠집니다.
// - startDate    : 선택. 계획을 시작할 날. 없으면 오늘. 오늘 ~ endDate 전날
// - dailyMinutes : 선택. 하루 공부 시간 30 ~ 300분. 없으면 60분 (카드 한 장이 30분 이상이라 하루도 30분부터, StudyBlock)
// - startTime    : 선택. 하루 공부를 시작할 시각 HH:MM. 없으면 19:00
// - restDays     : 선택. 쉬는 요일 (MON ~ SUN). 모든 요일은 안 됩니다.
// - image        : 선택. 교과서 · 목차 · 시험 범위 사진 한 장 (data URL). "사진으로 카드 만들기" 와 같은 규칙입니다.
//                  ⚠️ 사진은 저장하지 않습니다. AI 가 읽고 버립니다.
// - focus        : 선택. 내가 준비하는 것 (AI 하루 계획과 같은 뜻)
public record LongPlanRequest(
    @NotBlank(message = "무엇을 준비하는지 적어 주세요") @Size(max = 100, message = "목표는 100자 이하로 보내 주세요")
        String goal,
    @NotNull(message = "시험 · 마감 날짜를 보내 주세요") LocalDate endDate,
    LocalDate startDate,
    @Min(value = 30, message = "하루 공부 시간은 30 ~ 300분이에요")
        @Max(value = 300, message = "하루 공부 시간은 30 ~ 300분이에요")
        Integer dailyMinutes,
    @Pattern(regexp = LongPlanOutline.TIME_PATTERN, message = "시작 시각은 HH:MM 으로 보내 주세요")
        String startTime,
    @Size(max = 6, message = "모든 요일을 쉴 수는 없어요")
        List<
                @Pattern(
                    regexp = LongPlanOutline.DAY_PATTERN,
                    message = "쉬는 요일은 MON ~ SUN 으로 보내 주세요")
                String>
            restDays,
    @Size(max = CardPhotoRequest.MAX_IMAGE_CHARS, message = "사진이 너무 커요. 4MB 이하로 보내 주세요")
        String image,
    @Valid DayPlanRequest.Focus focus) {

  // 하루 공부 시간 · 시작 시각을 안 보냈을 때 쓰는 값
  public static final int DEFAULT_DAILY_MINUTES = 60;
  public static final String DEFAULT_START_TIME = "19:00";

  // 목표 앞뒤 공백을 지우고, 쉬는 요일은 같은 것을 하나만 남기고, 빈 사진은 없는 것(null)으로 봅니다.
  public LongPlanRequest {
    goal = goal == null ? null : goal.trim();
    restDays = restDays == null ? List.of() : restDays.stream().distinct().toList();
    image = image == null || image.isBlank() ? null : image;
  }
}
