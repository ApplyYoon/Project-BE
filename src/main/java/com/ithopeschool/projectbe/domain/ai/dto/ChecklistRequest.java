package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 체크리스트 추천 요청의 모양입니다. (이슈 #236)
//   { "title": "수학 문제집", "goal": "6월 모의고사 수학 2등급" }
// - title: 카드 제목. 200자까지 (공부 계획의 내용과 같은 길이)
// - goal : 연결한 목표의 이름. 없으면 빼거나 null. 목표 테이블(#221)이 생기면 목표 번호로 바꿉니다.
// 지난 기록은 보내지 않습니다. 서버가 DB 에 쌓인 내 기록(같은 제목의 지난 계획·타이머 기록)에서 찾습니다.
public record ChecklistRequest(
    @NotBlank(message = "카드 제목을 적어 주세요") @Size(max = 200, message = "카드 제목은 200자 이하로 적어 주세요")
        String title,
    @Size(max = 100, message = "목표는 100자 이하로 적어 주세요") String goal) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 목표가 빈 글이면 없는 것(null)으로 봅니다.
  public ChecklistRequest {
    title = title == null ? null : title.trim();
    goal = goal == null || goal.isBlank() ? null : goal.trim();
  }
}
