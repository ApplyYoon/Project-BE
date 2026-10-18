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

// AI 하루 계획 요청의 모양입니다.
//   { "date": "2026-10-18" }
//   { "date": "2026-10-18", "focus": { "kind": "SUNEUNG", "name": null },
//     "subjects": ["미적분Ⅰ"], "condition": 2 }
// - date      : 필수. 계획을 짤 날짜. 오늘부터 14일 안이어야 합니다. (Service 가 확인)
// - focus     : 선택. 내가 준비하는 것 (코치 채팅에서 고른 것, 기기에만 있어서 FE 가 보냄. 2026-10-18)
//               kind 는 SCHOOL_EXAM(내신) · SUNEUNG(수능) · CONTEST(대회 · 자격증) · OTHER(기타)
//               name 은 이름 한 줄(30자)
// - subjects  : 선택. 그날 시간표 과목 (8개 · 각 20자). AI 가 카드 글을 쓸 때 참고합니다.
// - condition : 선택. 오늘 컨디션 1~5. 2 이하면 총량을 줄여 가벼운 계획을 짭니다.
// 고정 일정 · 카드 · 목표 · 기록 · 설문 · 심리검사는 보내지 않습니다. 서버가 DB 에서 읽습니다.
public record DayPlanRequest(
    @NotNull(message = "계획을 짤 날짜를 보내 주세요") LocalDate date,
    @Valid Focus focus,
    @Size(max = 8, message = "과목은 8개까지 보낼 수 있어요")
        List<
                @NotBlank(message = "빈 과목이 있어요") @Size(max = 20, message = "과목 이름은 20자 이하로 보내 주세요")
                String>
            subjects,
    @Min(value = 1, message = "컨디션은 1 ~ 5 로 보내 주세요")
        @Max(value = 5, message = "컨디션은 1 ~ 5 로 보내 주세요")
        Integer condition) {

  // 날짜만 보내는 예전 모양 (테스트 · 다른 코드에서 씀)
  public DayPlanRequest(LocalDate date) {
    this(date, null, null, null);
  }

  // 과목을 안 보냈으면 빈 목록으로 봅니다.
  public DayPlanRequest {
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

    // AI 에게 주는 한 줄. 예) "수능", "대회 · 자격증 — 정보올림피아드"
    public String describe() {
      String label =
          switch (kind) {
            case "SCHOOL_EXAM" -> "내신(학교 시험)";
            case "SUNEUNG" -> "수능";
            case "CONTEST" -> "대회 · 자격증";
            default -> "기타";
          };
      return name == null ? label : label + " — " + name;
    }
  }
}
