package com.ithopeschool.projectbe.domain.coach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// 주간 코치 문단을 부탁할 때 FE 가 보내는 모양입니다. (FE src/lib/weeklyCoach.ts 의 WeeklyCoachRequest)
//
// ⚠️ FE 는 "계산된 숫자" 만 보냅니다. 계획 내용·일기·이름 같은 글 원문은 받지 않습니다.
//    그래서 숫자 하나의 글자 수와 쓸 수 있는 글자를 좁게 정해 두었습니다. 긴 글이나 이상한 기호는 400 으로 돌려보냅니다.
//    (이 값들은 그대로 AI 에게 건네지기 때문에, 엉뚱한 지시문을 끼워 넣지 못하게 하려는 것이기도 합니다)
public record WeeklyCoachRequest(
    // 어느 주인지. 그 주의 월요일을 'YYYY-MM-DD' 로 적습니다. 예: "2026-10-05"
    // @NotNull 은 "아예 안 보냄" 을, @Pattern 은 "보냈는데 모양이 틀림(빈 글자 포함)" 을 잡습니다.
    // 둘이 한꺼번에 걸리지 않게 나눠서, 안내 문장이 늘 하나로 정해지게 했습니다.
    @NotNull(message = "어느 주인지(mondayKey) 보내 주세요")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "mondayKey 는 'YYYY-MM-DD' 모양이어야 해요")
        String mondayKey,
    // 문단에 써도 되는 숫자들. AI 는 이 숫자만 가지고 문단을 씁니다.
    @NotEmpty(message = "숫자(numbers)를 하나 이상 보내 주세요")
        @Size(max = 10, message = "숫자(numbers)는 10개까지 보낼 수 있어요")
        @Valid
        List<Number> numbers,
    // 내가 정한 관리 강도 최대 한도 (0 회복 ~ 4 강제적 채찍)
    @Min(value = 0, message = "maxCareLevel 은 0 ~ 4 예요")
        @Max(value = 4, message = "maxCareLevel 은 0 ~ 4 예요")
        int maxCareLevel,
    // 내가 허용한 독설 수위 (1 ~ 5)
    @Min(value = 1, message = "maxHarshness 는 1 ~ 5 예요")
        @Max(value = 5, message = "maxHarshness 는 1 ~ 5 예요")
        int maxHarshness,
    // 안전 브레이크가 켜져 있는지. (위기 표현 · 기분 저하, FE 의 isSafetyBrakeOnNow)
    // true 면 한도가 몇이든 위로하는 말투만 씁니다. 안 보내면 꺼진 것으로 봅니다.
    Boolean safetyBrakeOn) {

  // 숫자 하나. FE 의 CoachEvidence 와 같습니다.
  //   { "label": "완주율", "value": "55%", "basis": "20번" }
  public record Number(
      // 무슨 숫자인지. 예: "완주율", "화요일 완주율"
      @NotBlank(message = "숫자의 이름(label)을 보내 주세요")
          @Size(max = 30, message = "숫자의 이름(label)은 30자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 이름(label)에 쓸 수 없는 글자가 있어요")
          String label,
      // 화면에 보여주는 값. 예: "55%", "평균 23분", "목표의 62%"
      @NotBlank(message = "숫자의 값(value)을 보내 주세요")
          @Size(max = 30, message = "숫자의 값(value)은 30자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 값(value)에 쓸 수 없는 글자가 있어요")
          String value,
      // 몇 개의 기록으로 계산했는지. 예: "20번", "계획 12개"
      @NotBlank(message = "숫자의 근거(basis)를 보내 주세요")
          @Size(max = 30, message = "숫자의 근거(basis)는 30자까지예요")
          @Pattern(regexp = ALLOWED_TEXT, message = "숫자의 근거(basis)에 쓸 수 없는 글자가 있어요")
          String basis) {

    // 쓸 수 있는 글자: 한글, 영문, 숫자, 띄어쓰기, 그리고 % · ( ) 뿐입니다.
    // 빈 글자도 통과시키는 것은 위의 @NotBlank 가 이미 알려 주기 때문입니다. (안내 문장이 하나만 나가게)
    private static final String ALLOWED_TEXT = "[가-힣a-zA-Z0-9 %·()]*";
  }
}
