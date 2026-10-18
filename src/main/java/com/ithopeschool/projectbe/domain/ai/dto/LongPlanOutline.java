package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "긴 계획(30일)" 의 뼈대입니다. 목표 · 기간 · 하루 공부 시간 · 할 일 목록만 있고, 날짜별 카드는 없습니다. (2026-10-18)
// 날짜별 카드(drafts)는 이 뼈대로 코드(LongPlanScheduler)가 매번 다시 나눕니다.
// 그래서 FE 는 받은 뼈대를 들고 있다가 고칠 때(/ai/long-plan/revise) 그대로 다시 보냅니다. 서버는 저장하지 않습니다.
//   { "goal": "11월 2일 중간고사 미적분 1~3단원", "startDate": "2026-10-19", "endDate": "2026-11-02",
//     "dailyMinutes": 60, "startTime": "19:00", "restDays": ["SAT", "SUN"],
//     "topics": [ { "title": "미적분 1단원 개념", "minutes": 90, "checklist": ["교과서 12~20쪽 읽기"] } ] }
// - endDate   : 시험 · 마감 날. 이 날은 공부 날에서 빠집니다. (그 전날까지 나눔)
// - restDays  : 쉬는 요일. MON TUE WED THU FRI SAT SUN 중에서 (모두 쉬는 것은 안 됨)
// - topics    : 공부 순서대로 할 일. AI 가 정하고, 학생의 요청으로 바뀝니다.
public record LongPlanOutline(
    @NotBlank(message = "계획의 목표를 보내 주세요") @Size(max = 100, message = "목표는 100자 이하로 보내 주세요")
        String goal,
    @NotNull(message = "계획의 시작 날짜를 보내 주세요") LocalDate startDate,
    @NotNull(message = "시험 · 마감 날짜를 보내 주세요") LocalDate endDate,
    @NotNull(message = "하루 공부 시간을 보내 주세요")
        @Min(value = 20, message = "하루 공부 시간은 20 ~ 300분이에요")
        @Max(value = 300, message = "하루 공부 시간은 20 ~ 300분이에요")
        Integer dailyMinutes,
    @NotNull(message = "시작 시각을 보내 주세요")
        @Pattern(regexp = LongPlanOutline.TIME_PATTERN, message = "시작 시각은 HH:MM 으로 보내 주세요")
        String startTime,
    @Size(max = 6, message = "모든 요일을 쉴 수는 없어요")
        List<
                @Pattern(
                    regexp = LongPlanOutline.DAY_PATTERN,
                    message = "쉬는 요일은 MON ~ SUN 으로 보내 주세요")
                String>
            restDays,
    @NotEmpty(message = "할 일을 보내 주세요") @Size(max = 20, message = "할 일은 20개까지 보낼 수 있어요")
        List<@Valid Topic> topics) {

  // 'HH:MM' (00:00 ~ 23:59)
  public static final String TIME_PATTERN = "([01]\\d|2[0-3]):[0-5]\\d";
  // 요일 세 글자
  public static final String DAY_PATTERN = "MON|TUE|WED|THU|FRI|SAT|SUN";

  // 쉬는 요일을 안 보냈으면 빈 목록으로 보고, 같은 요일은 하나만 남깁니다.
  public LongPlanOutline {
    restDays = restDays == null ? List.of() : restDays.stream().distinct().toList();
  }

  // 할 일 하나. minutes 는 이 할 일에 쓸 전체 시간이고, 코드가 여러 날로 나눌 수 있습니다.
  public record Topic(
      @NotBlank(message = "할 일 제목을 보내 주세요") @Size(max = 30, message = "할 일 제목은 30자 이하로 보내 주세요")
          String title,
      @NotNull(message = "할 일 시간을 보내 주세요")
          @Min(value = 20, message = "할 일 시간은 20 ~ 240분이에요")
          @Max(value = 240, message = "할 일 시간은 20 ~ 240분이에요")
          Integer minutes,
      @Size(max = 3, message = "체크리스트는 3개까지 보낼 수 있어요")
          List<
                  @NotBlank(message = "빈 체크리스트 항목이 있어요")
                  @Size(max = 50, message = "체크리스트 항목은 50자 이하로 보내 주세요") String>
              checklist) {

    // 체크리스트를 안 보냈으면 빈 목록으로 봅니다.
    public Topic {
      checklist = checklist == null ? List.of() : checklist;
    }
  }
}
