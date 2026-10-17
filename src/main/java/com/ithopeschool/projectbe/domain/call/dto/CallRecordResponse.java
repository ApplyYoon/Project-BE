package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import java.time.LocalDate;

// 지난 통화 하나를 기록 화면에 보여 주는 모양입니다. (마무리한 통화만, 위기 표현이 감지된 통화는 빼고 보냅니다)
//   { "callId": "12", "date": "2026-10-14", "summary": "폰 때문에 늦게 시작했다고 함. …",
//     "bias": { "key": "FUTURE_SELF", "label": "미래의 나 믿기", … },
//     "missReason": "phone", "promiseIf": "학원에서 오면", "promiseThen": "폰을 서랍에 넣기", "promiseKept":
// false }
// - 모르는 칸은 null 입니다. 누구의 통화인지(userId)는 담지 않습니다.
// - 확인하려던 습관(askedBias)과 AI 가 한 질문은 분석용이라 담지 않습니다.
public record CallRecordResponse(
    String callId,
    LocalDate date,
    String summary,
    CallFinishResponse.Bias bias,
    String missReason,
    String promiseIf,
    String promiseThen,
    Boolean promiseKept) {

  // missReason 은 FE 글자('phone' 등)로 바꿔서 넘겨받습니다.
  public static CallRecordResponse of(CallRecord call, String missReason) {
    return new CallRecordResponse(
        String.valueOf(call.getId()),
        call.getCallDate(),
        call.getSummary(),
        call.getFoundBias() == null
            ? null
            : CallFinishResponse.Bias.of(call.getFoundBias(), call.getFoundEvidence()),
        missReason,
        call.getPromiseIf(),
        call.getPromiseThen(),
        call.getPromiseKept());
  }
}
