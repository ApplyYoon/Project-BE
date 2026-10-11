package com.ithopeschool.projectbe.domain.call.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// AI 통화를 시작할 때 FE 가 보내는 모양입니다. (FE src/lib/realtimeCall.ts, src/lib/callCard.ts)
//
// 통화는 "쌓인 기록" 을 보고 질문과 말투가 달라져야 합니다. 그런데 기록이 아직 브라우저에만 있어서,
// FE 가 계산한 숫자를 "학생 카드" 한 장으로 모아 같이 보냅니다. 서버는 이 카드로 AI 의 지침을 만듭니다. (CallScript)
//
// ⚠️ 카드에는 숫자와 정해진 분류값만 담습니다. 계획 내용·일기·이름 같은 글 원문은 받지 않습니다.
//    그래서 모든 값의 범위와 모양을 좁게 정해 두었습니다. (이 값들이 AI 의 지침에 들어가기 때문입니다)
public record CallSessionRequest(
    // 오늘의 관리 강도. 0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍(말투는 3 과 같게 씁니다)
    @NotNull(message = "오늘의 관리 강도(careLevel)를 보내 주세요")
        @Min(value = 0, message = "careLevel 은 0 ~ 4 예요")
        @Max(value = 4, message = "careLevel 은 0 ~ 4 예요")
        Integer careLevel,
    // 내가 허용한 독설 수위 (1 ~ 5)
    @NotNull(message = "독설 허용 수위(maxHarshness)를 보내 주세요")
        @Min(value = 1, message = "maxHarshness 는 1 ~ 5 예요")
        @Max(value = 5, message = "maxHarshness 는 1 ~ 5 예요")
        Integer maxHarshness,
    // 학생 카드. 기록이 하나도 없는 사람은 null 로 보내도 됩니다. (그러면 처음 만난 사람처럼 통화합니다)
    @Valid Card card) {

  // 학생 카드: 통화에서 쓸 "사실" 들입니다. 모르는 값은 null 로 보냅니다. (0 과 "모름" 은 다릅니다)
  public record Card(
      // 어제의 순공시간(분)
      @Min(value = 0, message = "yesterdayStudyMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "yesterdayStudyMinutes 는 0 ~ 1440 이에요")
          Integer yesterdayStudyMinutes,
      // 어제 계획의 목표 시간 합계(분). 계획이 없었으면 0
      @Min(value = 0, message = "yesterdayPlanMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "yesterdayPlanMinutes 는 0 ~ 1440 이에요")
          Integer yesterdayPlanMinutes,
      // 어제 계획을 못 채운 이유로 고른 것. FE 의 MissReason 과 같은 글자입니다. 안 골랐으면 null
      @Pattern(
              regexp = "tired|event|phone|tooMuch|unwilling|sick|etc",
              message = "yesterdayMissReason 은 정해진 값 중 하나여야 해요")
          String yesterdayMissReason,
      // 오늘 계획의 개수와 목표 시간 합계(분)
      @Min(value = 0, message = "todayPlanCount 는 0 ~ 50 이에요")
          @Max(value = 50, message = "todayPlanCount 는 0 ~ 50 이에요")
          Integer todayPlanCount,
      @Min(value = 0, message = "todayPlanMinutes 는 0 ~ 1440 이에요")
          @Max(value = 1440, message = "todayPlanMinutes 는 0 ~ 1440 이에요")
          Integer todayPlanMinutes,
      // 오늘 첫 계획의 시작 시각 'HH:MM'. 시각을 정하지 않았으면 null
      @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "todayFirstStart 는 'HH:MM' 모양이어야 해요")
          String todayFirstStart,
      // 이번 주: 계획 대비 실제(%), 완주율(%), 계획한 시각보다 평균 몇 분 늦게 시작했는지
      @Min(value = 0, message = "weekPlanRate 는 0 ~ 100 이에요")
          @Max(value = 100, message = "weekPlanRate 는 0 ~ 100 이에요")
          Integer weekPlanRate,
      @Min(value = 0, message = "weekCompletionRate 는 0 ~ 100 이에요")
          @Max(value = 100, message = "weekCompletionRate 는 0 ~ 100 이에요")
          Integer weekCompletionRate,
      @Min(value = 0, message = "weekStartDelayMinutes 는 0 ~ 600 이에요")
          @Max(value = 600, message = "weekStartDelayMinutes 는 0 ~ 600 이에요")
          Integer weekStartDelayMinutes,
      // 최근 기분(1 많이 처짐 ~ 5 아주 좋음). 오래된 날부터 최근 날 순서로, 최대 7개
      @Size(max = 7, message = "recentMoods 는 7개까지 보낼 수 있어요")
          List<
                  @NotNull(message = "recentMoods 에 빈 값이 있어요")
                  @Min(value = 1, message = "기분은 1 ~ 5 예요")
                  @Max(value = 5, message = "기분은 1 ~ 5 예요") Integer>
              recentMoods,
      // 연속으로 공부한 날 수
      @Min(value = 0, message = "streakDays 는 0 ~ 3650 이에요")
          @Max(value = 3650, message = "streakDays 는 0 ~ 3650 이에요")
          Integer streakDays,
      // 어제 공부하다가 다른 탭·앱으로 나갔다 온 횟수 (FE 의 "딴짓 감지")
      @Min(value = 0, message = "yesterdayAwayCount 는 0 ~ 200 이에요")
          @Max(value = 200, message = "yesterdayAwayCount 는 0 ~ 200 이에요")
          Integer yesterdayAwayCount,
      // 어제 가장 자주 나간 시간대 (0 ~ 23시). 나간 적이 없으면 null
      @Min(value = 0, message = "yesterdayAwayPeakHour 는 0 ~ 23 이에요")
          @Max(value = 23, message = "yesterdayAwayPeakHour 는 0 ~ 23 이에요")
          Integer yesterdayAwayPeakHour,
      // 어제 집중 모드를 스스로 푼 횟수 (설정·안전 브레이크 때문에 저절로 풀린 것은 세지 않습니다)
      @Min(value = 0, message = "yesterdayUnlockCount 는 0 ~ 50 이에요")
          @Max(value = 50, message = "yesterdayUnlockCount 는 0 ~ 50 이에요")
          Integer yesterdayUnlockCount) {}
}
