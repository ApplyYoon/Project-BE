package com.ithopeschool.projectbe.domain.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// 상담 채팅을 "무엇에 대한 대화인지" 와 함께 열 때 보내는 칸입니다. (2026-10-18 "코치랑 이야기하기")
//   { "kind": "SESSION_REVIEW",
//     "lines": ["카드: 미적분 3단원 40분", "집중도 82%, 폰 3번 들었음", "체크리스트 3개 중 2개"] }
// - kind  : 주제. ChatTopic 의 이름과 같은 글자입니다.
// - lines : FE 가 학생 기기에서 만든 "사실" 1 ~ 8줄 (각 150자). AI 가 첫 말을 이 주제로 꺼냅니다.
//
// ⚠️ 서버는 이 줄들을 저장하지 않고 로그에도 찍지 않습니다. 채팅 대본(서버 메모리 2시간)에만 들어갑니다.
//    줄 안의 글은 AI 에게 "지시가 아닌 사실" 로만 줍니다. (CallScript.topicSection)
public record ChatAbout(
    @NotNull(message = "대화 주제(kind)를 보내 주세요")
        @Pattern(regexp = KINDS, message = "대화 주제(kind)는 정해진 값이어야 해요")
        String kind,
    @NotEmpty(message = "주제의 사실(lines)을 1줄 이상 보내 주세요")
        @Size(max = 8, message = "주제의 사실(lines)은 8줄까지 보낼 수 있어요")
        List<
                @NotBlank(message = "주제의 사실(lines)에 빈 줄이 있어요")
                @Size(max = 150, message = "주제의 사실은 한 줄에 150자까지예요") String>
            lines) {

  // 받을 수 있는 주제 이름들입니다. ChatTopic 에 주제를 더하면 여기에도 더해야 합니다. (ChatTopicTest 가 확인)
  public static final String KINDS =
      "SESSION_REVIEW|CARD_FAIL|WEEKLY_REPORT|QUIZ_WRONG|PSYCH_RESULT|DAY_REVIEW";
}
