package com.ithopeschool.projectbe.domain.care;

import java.util.ArrayList;
import java.util.List;

// 관리 단계 매일 판정입니다. (최종 기획서 「5.4 관리 단계」, 이슈 #62 · #232)
// 하루 1번(통화 때, 통화가 없으면 다음 날 첫 진입 때) 오늘의 단계(0 ~ 4)를 정합니다.
//
// 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//   1. 안전 조건(위기 표현, 기분 저하)                           → 0 고정
//   2. 회복 조건(휴식·미실행 연속, 부정 감정 연속, 절대 카드 놓침, 대화에서 힘들다)  → 0
//   3. 재발 신호 + 주원인이 내 쪽(폰·SNS, 하기 싫음, 미루다 보니)   → 지금 단계 +1 (최대 4, 제재 한도 안)
//   4. 재발 신호 + 주원인이 어쩔 수 없음(아픔, 행사·약속)          → 그대로 + 계획 축소 제안
//   5. 재발 신호 + 주원인이 계획 과다·피곤                        → 그대로 + 계획 축소 제안
//   6. 재발 신호 없음                                           → 기준 단계(Willpower.getBaseStage) 쪽으로 한 칸
// 그리고 상한: 제재 최대 한도, 시험 직전 3일 · 직후 2일은 2, 처음 7일은 2.
// 하루에 한 칸까지만 올라가고, 성공하면 그 자리에서 한 칸 내려갑니다. (stepDownOnSuccess)
//
// 예시 (어제 2단계, 기준 단계 2, 한도 4, 20일째, 시험 아님)
//   위기 표현                                  →  0, SAFETY
//   휴식·미실행 3일 연속 (민감도 3)             →  0, RECOVERY
//   재발 신호 + 주원인 폰                       →  3, UP
//   재발 신호 + 주원인 폰, 한도 2                →  2, UP          (한도는 넘지 않음)
//   재발 신호 + 주원인 폰, 시험 기간            →  2, UP          (시험 기간 상한 2)
//   재발 신호 + 주원인 아픔                     →  2, HOLD_UNAVOIDABLE, 계획 축소 제안
//   재발 신호 + 주원인 피곤                     →  2, HOLD_OVERLOAD, 계획 축소 제안
//   재발 신호 없음, 기준 단계 1                 →  1, TO_BASE
//   재발 신호 없음, 어제 0(회복), 기준 단계 2    →  1, TO_BASE      (한 칸씩)
//   어제 4단계, 재발 신호 + 폰                  →  4               (최대 4)
//
// 왜 이 단계인지 홈과 통화에서 항상 한 줄로 말합니다. (reason)
// FE 의 decideCareStage (src/lib/careStage.ts) 와 같은 규칙 · 같은 숫자 · 같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 단계가 어긋나지 않습니다.
public final class CareStage {

  // 시험 기간(직전 3일 · 직후 2일) 상한. 시험 기간에는 0 이 아니라 넛지(2)까지만 씁니다. (이슈 #232)
  public static final int EXAM_STAGE_CAP = 2;
  // 처음 이 일수 동안의 상한 (기록이 없을 때는 채찍을 쓰지 않음)
  public static final int FIRST_DAYS = 7;
  public static final int FIRST_DAYS_STAGE_CAP = 2;
  // 성공으로 칠 체크리스트 달성률 (성공하면 stepDownOnSuccess)
  public static final double SUCCESS_CHECKLIST_RATE = 0.7;

  // 단계의 범위
  private static final int MIN_STAGE = 0;
  private static final int MAX_STAGE = 4;

  // 어느 규칙으로 정했는지. FE 의 rule('safety' · 'recovery' · 'up' · 'holdUnavoidable' …)과 같습니다.
  public enum Rule {
    SAFETY("safety"),
    RECOVERY("recovery"),
    UP("up"),
    HOLD_UNAVOIDABLE("holdUnavoidable"),
    HOLD_OVERLOAD("holdOverload"),
    HOLD_UNKNOWN("holdUnknown"),
    TO_BASE("toBase");

    // FE 와 주고받는 글자
    private final String value;

    Rule(String value) {
      this.value = value;
    }

    public String toValue() {
      return value;
    }
  }

  // 안전 조건: 위기 표현을 감지했는지 · 기분 저하가 이어졌는지 (SafetyBrake 의 stopWhip · 기분 저하)
  public record Safety(boolean crisis, boolean lowMood) {}

  // 회복 조건에 쓰는 기록
  // restOrMissStreakDays: 오늘까지 휴식·미실행만 이어진 날 수
  // negativeMoodStreakDays: 오늘까지 부정 감정 태그(막막함·불안함·피곤함·짜증남)만 이어진 날 수
  // absoluteMissed: 지난 판정 이후 절대 카드를 놓쳤는지
  // hardTalk: 대화(통화·채팅)에서 힘들다는 표현이 있었는지
  // sensitivityDays: 회복 모드 민감도 (설정, 2 · 3 · 4일)
  public record Recovery(
      int restOrMissStreakDays,
      int negativeMoodStreakDays,
      boolean absoluteMissed,
      boolean hardTalk,
      int sensitivityDays) {}

  // 판정에 넣는 값
  // previousStage: 어제까지의 단계. 처음 판정이면 null → 기준 단계에서 시작
  // baseStage: 오늘의 기준 단계 (Willpower.getBaseStage)
  // dayIndex: 시작한 지 며칠째인지 (0 = 첫날)
  // relapse: 재발 신호 (RelapseSignal.judge). 이 중 hasSignal · reasons · mainCause 만 씁니다
  // maxLevel: 사용자가 정한 제재 최대 한도 (0 ~ 4)
  // examPeriod: 시험 직전 3일 ~ 직후 2일인지 (ExamPeriod)
  public record Input(
      Integer previousStage,
      int baseStage,
      int dayIndex,
      Safety safety,
      Recovery recovery,
      RelapseSignal relapse,
      int maxLevel,
      boolean examPeriod) {}

  // 판정 결과. stage: 오늘의 단계 (0 ~ 4) / rule: 어느 규칙으로 정했는지
  // suggestShrink: 계획을 줄여 보자고 제안할지 / reason: 왜 이 단계인지 한 줄 (홈 · 통화)
  public record Result(int stage, Rule rule, boolean suggestShrink, String reason) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CareStage() {}

  // 오늘의 관리 단계를 정합니다.
  public static Result decide(Input input) {
    int previous = input.previousStage() == null ? input.baseStage() : input.previousStage();

    // 1. 안전 조건
    if (input.safety().crisis() || input.safety().lowMood()) {
      return new Result(MIN_STAGE, Rule.SAFETY, false, "요즘 많이 힘들어 보여서 오늘은 쉬어 가는 회복 모드야.");
    }

    // 2. 회복 조건 (「4.5 회복 모드」)
    Recovery recovery = input.recovery();
    boolean isRecovery =
        recovery.restOrMissStreakDays() >= recovery.sensitivityDays()
            || recovery.negativeMoodStreakDays() >= recovery.sensitivityDays()
            || recovery.absoluteMissed()
            || recovery.hardTalk();
    if (isRecovery) {
      return new Result(
          MIN_STAGE, Rule.RECOVERY, true, "요 며칠 버거웠던 것 같아서 회복 모드야. 오늘은 계획을 줄여서 작게 시작해 보자.");
    }

    // 3 ~ 6
    int stage;
    Rule rule;
    String reason;
    RelapseSignal relapse = input.relapse();
    if (relapse.hasSignal()) {
      // 주원인을 모르면(null) 기타와 같이 UNKNOWN 입니다. (MissReason.causeKind)
      CauseKind kind =
          relapse.mainCause() == null ? CauseKind.UNKNOWN : relapse.mainCause().causeKind();
      String why = reasonsText(relapse.reasons());
      if (kind == CauseKind.INTERNAL) {
        stage = clamp(previous + 1);
        rule = Rule.UP;
        reason =
            why
                + " 주로 "
                + causeText(relapse.mainCause())
                + " 때문이라 "
                + CareLevelInfo.of(capStage(stage, input)).name()
                + " 모드야.";
      } else {
        stage = previous;
        if (kind == CauseKind.UNAVOIDABLE) {
          rule = Rule.HOLD_UNAVOIDABLE;
          reason = why + " 아픔·약속처럼 어쩔 수 없는 일이 많아서 단계는 그대로야. 계획을 줄여 볼까?";
        } else if (kind == CauseKind.OVERLOAD) {
          rule = Rule.HOLD_OVERLOAD;
          reason = why + " 계획이 많았거나 피곤했던 날이 많아서 단계는 그대로야. 계획을 줄여 볼까?";
        } else {
          rule = Rule.HOLD_UNKNOWN;
          reason = why + " 아직 이유를 몰라서 단계는 그대로야. 무슨 일이 있었는지 통화에서 물어볼게.";
        }
      }
    } else {
      // 한 칸씩 기준 단계 쪽으로
      if (previous < input.baseStage()) {
        stage = clamp(previous + 1);
      } else if (previous > input.baseStage()) {
        stage = clamp(previous - 1);
      } else {
        stage = previous;
      }
      rule = Rule.TO_BASE;
      reason = CareLevelInfo.of(capStage(stage, input)).defaultReason();
    }

    boolean suggestShrink = rule == Rule.HOLD_UNAVOIDABLE || rule == Rule.HOLD_OVERLOAD;
    return new Result(capStage(stage, input), rule, suggestShrink, reason);
  }

  // 성공(실행 + 체크리스트 70% 이상)하면 그 자리에서 한 칸 내립니다. 1단계 밑으로는 내려가지 않고, 0(회복)은 그대로입니다.
  //
  //   3 → 2    1 → 1    0 → 0
  public static int stepDownOnSuccess(int stage) {
    return stage >= 2 ? stage - 1 : stage;
  }

  // 한도 · 시험 기간 · 처음 7일 상한을 적용합니다.
  private static int capStage(int stage, Input input) {
    int capped = Math.min(stage, input.maxLevel());
    if (input.examPeriod()) {
      capped = Math.min(capped, EXAM_STAGE_CAP);
    }
    if (input.dayIndex() < FIRST_DAYS) {
      capped = Math.min(capped, FIRST_DAYS_STAGE_CAP);
    }
    return clamp(capped);
  }

  private static int clamp(int value) {
    return Math.min(MAX_STAGE, Math.max(MIN_STAGE, value));
  }

  // 재발 신호의 이유들을 한 줄로 잇습니다. (FE 의 RELAPSE_REASON_TEXT 를 ", " 로 이은 것)
  private static String reasonsText(List<RelapseReason> reasons) {
    List<String> texts = new ArrayList<>();
    for (RelapseReason reason : reasons) {
      texts.add(
          switch (reason) {
            case ROUTINE_TWICE -> "같은 루틴을 2번 연속 놓쳤고";
            case RATE_DROP -> "최근 7일 실행률이 평소보다 많이 떨어졌고";
            case ABSOLUTE_MISS -> "꼭 해야 하는 카드를 놓쳤고";
          });
    }
    return String.join(", ", texts);
  }

  // 내 쪽 원인의 이름입니다. (FE 의 CAUSE_TEXT) 목록에 없으면 "내 쪽 이유" 입니다.
  private static String causeText(MissReason cause) {
    if (cause == MissReason.PHONE) {
      return "폰·SNS";
    }
    if (cause == MissReason.UNWILLING) {
      return "하기 싫은 마음";
    }
    if (cause == MissReason.PROCRASTINATED) {
      return "미루다 보니";
    }
    return "내 쪽 이유";
  }
}
