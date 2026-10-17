package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 의지력 지수 계산기입니다. (최종 기획서 「5.3 의지력 지수」, 이슈 #231)
//
// 0 ~ 100 의 **내부 값**입니다. ⚠️ 어떤 API 응답에도 넣지 않습니다. (WillpowerNotInResponseTest 가 지킵니다)
// "의지력 42점" 같은 점수는 낙인(6번 오류)이 되기 때문입니다. 관리 단계(#62)의 기준 단계를 정하는 데만 씁니다.
//
//   의지력(데이터) = 50 × 평균 실행률 + 30 × 정시 시작 비율 + 20 × (1 − 미루기·실패 비율)   ← 최근 14일 카드
//   의지력(검사)   = (7 − 시작 저항·주의 분산·실패 민감도의 평균) ÷ 6 × 100              ← 온보딩 심리검사(1~7점)
//   의지력         = w × 데이터 + (1 − w) × 검사,  w = min(유효 카드일 수 ÷ 14, 1)
//
// 값만 계산하는 순수 함수라서 DB 를 모릅니다. FE 의 src/lib/willpower.ts 와 같은 규칙 · 같은 숫자입니다.
// 숫자는 모두 출발값입니다. 바꿀 때는 FE(#384)의 상수와 같이 고칩니다.
public final class Willpower {

  // 의지력(데이터)를 보는 기간 (오늘 포함 최근 14일)
  public static final int DATA_PERIOD_DAYS = 14;
  // 데이터로 완전히 넘어가는 데 걸리는 유효 카드일 수
  public static final int WILLPOWER_DATA_DAYS = 14;
  // 검사도 데이터도 없을 때의 의지력 (가운데 → 기준 단계 2 '넛지')
  public static final double DEFAULT_WILLPOWER = 50;
  // 기준 단계 경계: 이 값 이상이면 1단계(당근)
  public static final double BASE_STAGE_CARROT_FROM = 70;
  // 기준 단계 경계: 이 값 이상이면 2단계(넛지), 그 밑은 3단계(심리적 채찍)
  public static final double BASE_STAGE_NUDGE_FROM = 40;
  // 처음 이 일수 동안은 기준 단계 상한이 2 입니다 — 기록이 없을 때는 채찍을 쓰지 않습니다
  public static final int FIRST_DAYS_CAP_DAYS = 7;
  public static final int FIRST_DAYS_CAP_STAGE = 2;

  // 검사 점수 중 의지력에 쓰는 세 척도 (각각 1 ~ 7점, 높을수록 꾸물거림 · 흔들림 · 자책이 큼)
  // ⚠️ 서버에는 아직 심리검사 점수가 저장되지 않습니다(#219). 부르는 쪽이 이 세 값을 넘깁니다.
  public record TestScores(double startResistance, double distraction, double failSensitivity) {}

  // 관리 선호(검사의 C1 · C2 척도) 수준 — FE psychTest.ts 의 getScaleLevel 결과 (high · mid · low)
  public enum CarePreference {
    HIGH,
    MID,
    LOW
  }

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private Willpower() {}

  // 최근 14일 카드 기록으로 의지력(데이터)을 구합니다. 카드가 하나도 없으면 null 입니다.
  // 휴식(REST)한 날은 넣지 않고 들어와도 뺍니다. 고정 일정(학원)은 카드가 아니라 처음부터 없습니다.
  // (카드 유형은 자습 · 인강 두 가지뿐이라 "자습 · 인강 카드만" 은 카드 전부입니다)
  //
  //   - 평균 실행률: 카드마다 runRate 의 평균 (미실행 0, 증빙 없는 자기 보고 0.5)
  //   - 정시 시작 비율: 시작한 카드 중 예정 시각 10분 안에 시작한 비율. 시작한 카드가 없으면 0
  //   - 미루기·실패 비율: 전체 카드 중 미루기 · 실패 · 미처리 비율
  //
  //   카드 4장: 실행(정시, 1) · 실행(정시, 1) · 지연(늦음, 1) · 실패(0)
  //   평균 실행률 0.75, 정시 2/3, 실패 1/4
  //   → 50 × 0.75 + 30 × 0.667 + 20 × 0.75 = 72.5
  public static Double getDataWillpower(List<JudgedCardDay> days) {
    List<JudgedCardDay> counted = withoutRest(days);
    if (counted.isEmpty()) {
      return null;
    }

    double runRateSum = 0;
    int startedCount = 0;
    int onTimeCount = 0;
    int missCount = 0;
    for (JudgedCardDay day : counted) {
      runRateSum += day.runRate();
      // startedOnTime 이 null 이면 시작하지 않은 카드라 비율 계산에서 뺍니다.
      if (day.startedOnTime() != null) {
        startedCount++;
        if (day.startedOnTime()) {
          onTimeCount++;
        }
      }
      if (day.result().isMiss()) {
        missCount++;
      }
    }
    double averageRunRate = runRateSum / counted.size();
    double onTimeRatio = startedCount == 0 ? 0 : (double) onTimeCount / startedCount;
    double missRatio = (double) missCount / counted.size();

    return 50 * averageRunRate + 30 * onTimeRatio + 20 * (1 - missRatio);
  }

  // 온보딩 심리검사로 의지력(검사)을 구합니다. 세 척도가 높을수록(꾸물거림 · 흔들림 · 자책) 낮아집니다.
  //
  //   세 척도 평균 1 → 100    평균 4 → 50    평균 7 → 0
  public static double getTestWillpower(TestScores scores) {
    double average =
        (scores.startResistance() + scores.distraction() + scores.failSensitivity()) / 3;
    return (7 - average) / 6 * 100;
  }

  // 유효 카드일 수 = 카드가 1장 이상 있었던 날 수 (휴식일 빼고)
  public static int countValidCardDays(List<JudgedCardDay> days) {
    Set<LocalDate> dates = new HashSet<>();
    for (JudgedCardDay day : withoutRest(days)) {
      dates.add(day.date());
    }
    return dates.size();
  }

  // 검사와 데이터를 섞어 의지력을 구합니다. 첫날은 검사 100%, 유효 카드일 14일째부터 데이터 100% 입니다.
  //   - 검사를 안 했으면(scores 가 null) 데이터만, 데이터가 없으면 검사만 씁니다.
  //   - 둘 다 없으면 50 (가운데 → 기준 단계 2 '넛지') 입니다.
  //
  //   검사 50, 데이터 80, 유효 카드일 7일  → 0.5 × 80 + 0.5 × 50 = 65
  //   검사 50, 데이터 80, 유효 카드일 20일 → 80
  //   검사 없음, 데이터 없음              → 50
  public static double getWillpower(List<JudgedCardDay> days, TestScores scores) {
    Double data = getDataWillpower(days);
    Double test = scores == null ? null : getTestWillpower(scores);
    if (data == null) {
      return test == null ? DEFAULT_WILLPOWER : test;
    }
    if (test == null) {
      return data;
    }
    double weight = Math.min((double) countValidCardDays(days) / WILLPOWER_DATA_DAYS, 1);
    return weight * data + (1 - weight) * test;
  }

  // 기준 단계를 정합니다 (1 ~ 3).
  //   - 의지력 70 이상 → 1, 40 ~ 69 → 2, 40 미만 → 3
  //   - 관리 선호가 높으면 +1, 낮으면 −1 (1 ~ 3 안에서). 모르면(null) 그대로
  //   - 시작한 지 7일 안(dayIndex 0 ~ 6)이면 상한 2
  //
  //   getBaseStage(75, MID, 20) → 1     getBaseStage(55, HIGH, 20) → 3
  //   getBaseStage(30, LOW, 20) → 2     getBaseStage(30, MID, 3)   → 2 (첫 7일)
  public static int getBaseStage(double willpower, CarePreference carePreference, int dayIndex) {
    int stage;
    if (willpower >= BASE_STAGE_CARROT_FROM) {
      stage = 1;
    } else if (willpower >= BASE_STAGE_NUDGE_FROM) {
      stage = 2;
    } else {
      stage = 3;
    }
    if (carePreference == CarePreference.HIGH) {
      stage += 1;
    }
    if (carePreference == CarePreference.LOW) {
      stage -= 1;
    }
    stage = Math.min(3, Math.max(1, stage));
    if (dayIndex < FIRST_DAYS_CAP_DAYS) {
      stage = Math.min(stage, FIRST_DAYS_CAP_STAGE);
    }
    return stage;
  }

  // 휴식(REST) 날을 뺀 목록
  private static List<JudgedCardDay> withoutRest(List<JudgedCardDay> days) {
    List<JudgedCardDay> counted = new ArrayList<>();
    for (JudgedCardDay day : days) {
      if (day.result() != CardResult.REST) {
        counted.add(day);
      }
    }
    return counted;
  }
}
