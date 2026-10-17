package com.ithopeschool.projectbe.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// AI 자기 분석 요청의 모양입니다. (FE#419 표 14번, 이슈 #237)
//   { "facts": [ { "key": "slotRate", "group": "tendency", "label": "시간대별 실행률",
//                  "value": "아침 95% · 밤 63%", "basis": "카드 64번" }, … ] }
//
// ⚠️ FE 가 브라우저에 쌓인 기록(카드 하루치 · 공부 세션 · 실패 표시 · 내 가설 · 예측)으로 **계산한 숫자** 만 보냅니다.
//    그 기록이 아직 서버에 다 올라오지 않아서입니다. 서버에 이미 있는 것(통화에서 보인 생각 습관)은 서버가 DB 에서 직접 읽어 더합니다.
//    미루기 사유 · 일기 같은 글 원문은 받지 않습니다. 카드 이름 같은 짧은 이름만 값 안에 들어올 수 있어서,
//    쓸 수 있는 글자를 좁혀 두었습니다. (이 값들은 그대로 AI 에게 건네지므로, 엉뚱한 지시문을 끼워 넣지 못하게 하려는 것이기도 합니다)
public record AnalysisRequest(
    @NotEmpty(message = "숫자(facts)를 하나 이상 보내 주세요")
        @Size(max = 30, message = "숫자(facts)는 30개까지 보낼 수 있어요")
        @Valid
        List<Fact> facts) {

  // 숫자 하나. FE 의 src/lib/analysisFacts.ts 의 AnalysisFact 와 같습니다.
  // 서버가 DB 에서 읽어 더하는 숫자(AnalysisResponse.serverFacts)도 같은 모양입니다.
  public record Fact(
      // 숫자를 구분하는 이름. AI 가 "이 숫자를 근거로 썼다" 고 알려 줄 때 씁니다. 예: "slotRate"
      // 빈 글자는 @NotBlank 가 잡습니다. (@Pattern 은 빈 글자를 통과시켜서 안내 문장이 하나만 나가게 했습니다)
      @NotBlank(message = "숫자의 key 를 보내 주세요")
          @Pattern(regexp = "[a-zA-Z0-9]{0,30}", message = "숫자의 key 는 영문·숫자 30자까지예요")
          String key,
      // 어느 묶음의 숫자인지 (SelfAnalysis.GROUPS)
      @NotBlank(message = "숫자의 묶음(group)을 보내 주세요")
          @Pattern(
              regexp = "(tendency|distortion|willpower|efficiency|failure)?",
              message = "group 은 tendency · distortion · willpower · efficiency · failure 중 하나예요")
          String group,
      // 무슨 숫자인지. 예: "시간대별 실행률"
      @NotBlank(message = "숫자의 이름(label)을 보내 주세요")
          @Size(max = 30, message = "숫자의 이름(label)은 30자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 이름(label)에 쓸 수 없는 글자가 있어요")
          String label,
      // 화면에 보여주는 값. 예: "아침 95% · 밤 63%", "예상 50분 → 실제 23분"
      @NotBlank(message = "숫자의 값(value)을 보내 주세요")
          @Size(max = 80, message = "숫자의 값(value)은 80자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 값(value)에 쓸 수 없는 글자가 있어요")
          String value,
      // 몇 개의 기록으로 계산했는지. 예: "카드 64번", "12일"
      @NotBlank(message = "숫자의 근거(basis)를 보내 주세요")
          @Size(max = 30, message = "숫자의 근거(basis)는 30자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 근거(basis)에 쓸 수 없는 글자가 있어요")
          String basis) {

    // 쓸 수 있는 글자: 한글, 영문, 숫자, 띄어쓰기, 그리고 % · ( ) → , . : / ~ + ' - 뿐입니다.
    private static final String ALLOWED_TEXT = "[가-힣a-zA-Z0-9 %·()→,.:/~+'\\-]*";
  }
}
