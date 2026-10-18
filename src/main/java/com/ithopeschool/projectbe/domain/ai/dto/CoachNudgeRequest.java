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

// "코치 알림 문장" 요청의 모양입니다. (2026-10-18)
// 앱 알림은 앱을 닫아도 와야 해서 휴대폰에 미리 예약합니다. 그래서 FE 가 하루 한 번(앱을 켤 때 · 밤 정리 뒤)
// 기기에서 계산한 숫자를 보내고, 그날 아침 · 밤에 보낼 문장을 미리 받아 예약합니다.
//   { "date": "2026-10-19",
//     "subjects": ["국어", "영어Ⅰ", "수학Ⅰ", "한국사", "체육", "미적분"],
//     "cards": [ { "startTime": "19:00", "title": "미적분 문제집", "minutes": 40 } ],
//     "yesterday": { "plannedCards": 3, "doneCards": 2, "studyMinutes": 95, "focusScore": 82,
// "phoneLifts": 4 },
//     "wrongQuizTopics": ["수열의 극한"],
//     "exam": { "name": "2학기 중간고사", "daysLeft": 14 },
//     "focus": { "kind": "SCHOOL_EXAM", "name": null } }
// - date            : 필수. 알림을 보낼 날. 오늘 또는 내일만 됩니다. (Service 가 확인, 하루의 기준은 새벽 4시)
// - subjects        : 선택. 그날 시간표 과목을 **1교시부터 순서대로** (8개 · 각 20자). AI 가 "6교시 미적분" 처럼 씁니다.
// - cards           : 선택. 그날 계획 카드 (10장 · 시작 시각 HH:MM · 제목 30자 · 5~120분)
// - yesterday       : 선택. date 의 전날 기록 (계획 카드 수 · 끝낸 카드 수 · 순공 분 · 집중도 · 폰 든 횟수)
// - wrongQuizTopics : 선택. 최근 틀린 확인 문제의 주제 (3개 · 각 30자)
// - exam            : 선택. 가까운 시험 (이름 30자, 남은 날 0~60)
// - focus           : 선택. 내가 준비하는 것 (AI 하루 계획과 같은 모양)
// 이름 · 학교 같은 개인정보는 받지 않습니다.
public record CoachNudgeRequest(
    @NotNull(message = "알림을 보낼 날짜를 보내 주세요") LocalDate date,
    @Size(max = 8, message = "과목은 8개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 과목이 있어요") @Size(max = 20, message = "과목 이름은 20자 이하로 보내 주세요")
                String>
            subjects,
    @Size(max = 10, message = "계획 카드는 10장까지 보낼 수 있어요") List<@Valid Card> cards,
    @Valid Yesterday yesterday,
    @Size(max = 3, message = "틀린 문제 주제는 3개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 주제가 있어요")
                @Size(max = 30, message = "틀린 문제 주제는 30자 이하로 보내 주세요") String>
            wrongQuizTopics,
    @Valid Exam exam,
    @Valid DayPlanRequest.Focus focus) {

  // 목록을 안 보냈으면 빈 목록으로 보고, 글의 앞뒤 공백을 지웁니다.
  public CoachNudgeRequest {
    subjects = subjects == null ? List.of() : subjects.stream().map(String::trim).toList();
    cards = cards == null ? List.of() : cards;
    wrongQuizTopics =
        wrongQuizTopics == null ? List.of() : wrongQuizTopics.stream().map(String::trim).toList();
  }

  // 그날 계획 카드 한 장
  public record Card(
      @NotNull(message = "카드 시작 시각을 보내 주세요")
          @Pattern(regexp = "\\d{2}:\\d{2}", message = "시작 시각은 HH:MM 으로 보내 주세요")
          String startTime,
      @NotBlank(message = "카드 제목을 보내 주세요") @Size(max = 30, message = "카드 제목은 30자 이하로 보내 주세요")
          String title,
      @NotNull(message = "카드 길이를 보내 주세요")
          @Min(value = 5, message = "카드 길이는 5 ~ 120분이에요")
          @Max(value = 120, message = "카드 길이는 5 ~ 120분이에요")
          Integer minutes) {

    // 제목의 앞뒤 공백을 지웁니다.
    public Card {
      title = title == null ? null : title.trim();
    }
  }

  // date 의 전날 기록. 앞의 셋은 꼭, 집중도 · 폰 든 횟수는 잰 날만 보냅니다.
  public record Yesterday(
      @NotNull(message = "전날 계획 카드 수를 보내 주세요")
          @Min(value = 0, message = "전날 계획 카드 수는 0 ~ 20 이에요")
          @Max(value = 20, message = "전날 계획 카드 수는 0 ~ 20 이에요")
          Integer plannedCards,
      @NotNull(message = "전날 끝낸 카드 수를 보내 주세요")
          @Min(value = 0, message = "전날 끝낸 카드 수는 0 ~ 20 이에요")
          @Max(value = 20, message = "전날 끝낸 카드 수는 0 ~ 20 이에요")
          Integer doneCards,
      @NotNull(message = "전날 공부 시간을 보내 주세요")
          @Min(value = 0, message = "전날 공부 시간은 0 ~ 1440분이에요")
          @Max(value = 1440, message = "전날 공부 시간은 0 ~ 1440분이에요")
          Integer studyMinutes,
      @Min(value = 0, message = "집중도는 0 ~ 100 이에요") @Max(value = 100, message = "집중도는 0 ~ 100 이에요")
          Integer focusScore,
      @Min(value = 0, message = "폰 든 횟수는 0 ~ 200 이에요")
          @Max(value = 200, message = "폰 든 횟수는 0 ~ 200 이에요")
          Integer phoneLifts) {}

  // 가까운 시험
  public record Exam(
      @NotBlank(message = "시험 이름을 보내 주세요") @Size(max = 30, message = "시험 이름은 30자 이하로 보내 주세요")
          String name,
      @NotNull(message = "시험까지 남은 날을 보내 주세요")
          @Min(value = 0, message = "시험까지 남은 날은 0 ~ 60 이에요")
          @Max(value = 60, message = "시험까지 남은 날은 0 ~ 60 이에요")
          Integer daysLeft) {

    // 이름의 앞뒤 공백을 지웁니다.
    public Exam {
      name = name == null ? null : name.trim();
    }
  }
}
