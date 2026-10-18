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

// "AI 가 짜는 오늘의 미션" 요청의 모양입니다. (2026-10-18)
//   { "date": "2026-10-19",
//     "focus": { "kind": "SUNEUNG", "name": null },
//     "subjects": ["미적분Ⅰ", "문학", "영어Ⅰ"],
//     "exam": { "name": "2학기 2차 정기시험", "dDay": 14 },
//     "condition": 3 }
// - date      : 필수. 오늘 (하루의 기준은 새벽 4시, FE 가 계산)
// - focus     : 선택. 내가 준비하는 것
//               kind 는 SCHOOL_EXAM(내신) · SUNEUNG(수능) · CONTEST(대회 · 자격증) · OTHER(기타)
//               name 은 학생이 쓴 이름 한 줄(대회 · 기타일 때, 30자까지)
// - subjects  : 선택. 오늘 시간표의 과목들 (8개 · 과목마다 20자까지). 없으면 빈 목록
// - exam      : 선택. 가까운 시험 (이름 40자, 남은 날 0~60)
// - condition : 선택. 오늘 컨디션 1~5
public record DailyMissionsRequest(
    @NotNull(message = "날짜를 보내 주세요") LocalDate date,
    @Valid Focus focus,
    @Size(max = 8, message = "과목은 8개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 과목이 있어요") @Size(max = 20, message = "과목 이름은 20자 이하로 보내 주세요")
                String>
            subjects,
    @Valid Exam exam,
    @Min(value = 1, message = "컨디션은 1 ~ 5 로 보내 주세요")
        @Max(value = 5, message = "컨디션은 1 ~ 5 로 보내 주세요")
        Integer condition) {

  // 과목을 안 보냈으면 빈 목록으로 봅니다.
  public DailyMissionsRequest {
    subjects = subjects == null ? List.of() : subjects.stream().map(String::trim).toList();
  }

  // 내가 준비하는 것
  public record Focus(
      @NotNull(message = "준비하는 것의 종류를 보내 주세요")
          @Pattern(
              regexp = "SCHOOL_EXAM|SUNEUNG|CONTEST|OTHER",
              message = "준비하는 것은 SCHOOL_EXAM · SUNEUNG · CONTEST · OTHER 중 하나예요")
          String kind,
      @Size(max = 30, message = "준비하는 것의 이름은 30자 이하로 보내 주세요") String name) {

    // 빈 이름은 없는 것(null)으로 봅니다.
    public Focus {
      name = name == null || name.isBlank() ? null : name.trim();
    }
  }

  // 가까운 시험
  public record Exam(
      @NotBlank(message = "시험 이름을 보내 주세요") @Size(max = 40, message = "시험 이름은 40자 이하로 보내 주세요")
          String name,
      @NotNull(message = "시험까지 남은 날을 보내 주세요")
          @Min(value = 0, message = "시험까지 남은 날은 0 ~ 60 이에요")
          @Max(value = 60, message = "시험까지 남은 날은 0 ~ 60 이에요")
          Integer dDay) {}
}
