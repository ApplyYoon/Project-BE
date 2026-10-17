package com.ithopeschool.projectbe.domain.plan.dto;

import com.ithopeschool.projectbe.domain.plan.PlanDraft;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// "말로 계획 세우기" 의 결과입니다. 아무것도 저장하지 않은 초안입니다.
//   { "showHelp": false,
//     "drafts": [ { "date": "2026-10-11", "subjectId": "3", "content": "문제집", "targetMinutes": 120,
//                   "startTime": null, "kind": "study" } ] }
// - drafts 의 한 줄은 계획 추가 API 의 요청과 같은 모양입니다. FE 는 미리보기를 보여 준 뒤 그대로 저장(#12)합니다.
// - subjectId 가 null 이면 GPT 가 내 과목에서 고르지 못한 것입니다. FE 가 과목을 고르게 합니다.
// - showHelp 가 true 면 문장에서 위기 표현이 감지된 것입니다. drafts 는 비어 있고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
public record PlanParseResponse(boolean showHelp, List<Draft> drafts) {

  // 계획 초안 한 줄. (PlanDraft 에 날짜를 붙인 것)
  public record Draft(
      LocalDate date,
      String subjectId,
      String content,
      int targetMinutes,
      String startTime,
      String kind) {}

  // 초안들에 날짜를 붙여 응답을 만듭니다.
  public static PlanParseResponse of(LocalDate date, List<PlanDraft> drafts) {
    List<Draft> withDate = new ArrayList<>();
    for (PlanDraft draft : drafts) {
      withDate.add(
          new Draft(
              date,
              draft.subjectId(),
              draft.content(),
              draft.targetMinutes(),
              draft.startTime(),
              draft.kind()));
    }
    return new PlanParseResponse(false, withDate);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. 초안은 만들지 않습니다.
  public static PlanParseResponse help() {
    return new PlanParseResponse(true, List.of());
  }
}
