package com.ithopeschool.projectbe.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// "회고 저장" 요청의 모양입니다. 시험 후 회고와 주간 회고를 type 으로 구분합니다.
//   시험 후: { "type": "exam", "examId": "7", "answers": ["4", "시간 분배를 못 했다", "시간 재고 풀기"] }
//   주간:   { "type": "weekly", "weekStart": "2026-10-12", "answers": ["매일 계획 지킴", "", "토요일 9시 시작"] }
// - answers 는 질문 순서대로 꼭 3개입니다. 주간 회고에서 건너뛴 질문은 빈 글자("")로 보냅니다.
// - 답 하나는 200자까지입니다. (앞뒤 공백을 지운 뒤에 셉니다)
// - type 이 exam · weekly 중 하나인지, 종류에 맞는 칸을 보냈는지는 Service 에서 검사합니다.
public record ReviewSaveRequest(
    @NotBlank(message = "회고 종류를 골라 주세요") String type,
    // 시험 후 회고일 때만 보냅니다. 18자리까지만 받습니다. (그보다 길면 서버의 숫자(Long)에 안 들어갑니다)
    @Pattern(regexp = "\\d{1,18}", message = "시험 번호가 올바르지 않아요") String examId,
    // 주간 회고일 때만 보냅니다. 그 주의 월요일 날짜입니다.
    LocalDate weekStart,
    @NotNull(message = "회고 답을 보내 주세요") @Size(min = 3, max = 3, message = "회고 답은 질문 순서대로 3개를 보내 주세요")
        List<
                @NotNull(message = "회고 답을 보내 주세요")
                @Size(max = 200, message = "회고 답은 하나에 200자까지 적을 수 있어요") String>
            answers) {

  // 값이 들어오는 순간에 답마다 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public ReviewSaveRequest {
    if (answers != null) {
      // toList() 는 null 도 그대로 담아서, 빠진 답은 위의 @NotNull 검사가 잡아냅니다.
      answers = answers.stream().map(answer -> answer == null ? null : answer.trim()).toList();
    }
  }
}
