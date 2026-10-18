package com.ithopeschool.projectbe.domain.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "시험 등록" 요청의 모양입니다. (FE 의 Exam 에서 id 와 실제 점수를 뺀 것)
//   { "type": "mock", "name": "10월 모의고사", "date": "2026-10-20",
//     "subjectScores": [ { "subjectId": "3", "expectedScore": 85 }, { "subjectId": "4",
// "expectedScore": 70 } ],
//     "goalId": "4" }
// - type 이 mock · school · quiz · etc 중 하나인지는 Service 에서 검사합니다.
// - 과목은 하나 이상, 같은 과목은 한 번만 보냅니다. 내 과목인지도 Service 에서 검사합니다.
// - goalId 는 선택입니다. 보내면 내 점수형 목표인지 Service 에서 검사합니다. (이슈 #228)
public record ExamCreateRequest(
    @NotBlank(message = "시험 종류를 골라 주세요") String type,
    @NotBlank(message = "시험 이름을 입력해 주세요") @Size(max = 50, message = "시험 이름은 50자 이하로 적어 주세요")
        String name,
    @NotNull(message = "시험 날짜를 보내 주세요") LocalDate date,
    // 50개까지만 받습니다. (한 번에 너무 많은 과목을 보내서 서버가 오래 걸리지 않게 하려는 한도입니다)
    // @Valid: 목록 안의 과목 점수 하나하나도 똑같이 검사합니다.
    @NotEmpty(message = "과목 하나 이상에 예상 점수를 적어 주세요")
        @Size(max = 50, message = "과목은 한 시험에 50개까지 적을 수 있어요")
        List<@Valid ExamExpectedScoreRequest> subjectScores,
    // (선택) 이 시험을 이을 점수형 목표의 번호. 안 보내면 목표 없이 저장합니다. (이슈 #228)
    @Pattern(regexp = "\\d{1,18}", message = "목표 번호가 올바르지 않아요") String goalId) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public ExamCreateRequest {
    name = name == null ? null : name.trim();
  }

  // 목표 없이 등록할 때 (goalId 가 생기기 전의 모양)
  public ExamCreateRequest(
      String type, String name, LocalDate date, List<ExamExpectedScoreRequest> subjectScores) {
    this(type, name, date, subjectScores, null);
  }
}
