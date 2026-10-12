package com.ithopeschool.projectbe.domain.safety;

import com.ithopeschool.projectbe.domain.safety.LowMoodStreak.MoodRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 어떤 설정보다 먼저 적용되는 안전 규칙 4가지를 한 번에 판정합니다. (이슈 #56, 기획서 v2 5장 「안전 브레이크」)
// 사용자 대부분이 미성년자라서, 아래 결과는 관리 강도·알림 설정보다 항상 우선합니다.
//
//   규칙                      검사기                    켜지는 것
//   1) 위기 표현               CrisisText (이슈 #56)     stopWhip · recoveryMode · showHelp
//   2) 시험 직전 3일~직후 2일   ExamPeriod (이슈 #58)     recoveryMode
//   3) 기분 저하 6일 연속       LowMoodStreak (이슈 #59)  recoveryMode · showHelp
//   4) 연락 금지 시간           QuietHours (이슈 #57)     quiet
//
//   글에 "죽고 싶다" 가 있음           →  stopWhip · recoveryMode · showHelp 모두 true
//   시험 이틀 전 (글·기분은 괜찮음)     →  recoveryMode 만 true  (도움 안내는 띄우지 않습니다)
//   기분 1~2 가 오늘까지 6일 연속      →  recoveryMode · showHelp 가 true, stopWhip 은 false
//   밤 11시 30분 (금지 23:00~07:00)   →  quiet 가 true  (위의 셋과는 따로 정해집니다)
//
// 관리 강도 계산(CareLevel.of)의 isSafetyBrakeOn 에는 stopWhip || recoveryMode 를 넘깁니다.
//
//   SafetyBrake brake = SafetyBrake.check(now, texts, moods, examDates);
//   boolean isSafetyBrakeOn = brake.stopWhip() || brake.recoveryMode();
//
// FE 의 getSafetyBrakeResult (src/lib/safetyBrake.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판단이 어긋나지 않습니다.
//
// stopWhip: 모든 채찍을 즉시 멈춰야 하면 true (위기 표현을 감지했을 때만)
// recoveryMode: 채찍 대신 회복 모드로 가야 하면 true (위기 표현·시험 기간·기분 저하 중 하나라도)
// showHelp: 도움 연결 안내(HelpLine)를 보여줘야 하면 true (위기 표현·기분 저하)
// quiet: 지금은 메시지·전화를 하면 안 되면 true (연락 금지 시간)
public record SafetyBrake(boolean stopWhip, boolean recoveryMode, boolean showHelp, boolean quiet) {

  // 기본 연락 금지 시간(23:00~07:00)으로 판정합니다.
  public static SafetyBrake check(
      Instant now, List<String> recentTexts, List<MoodRecord> recentMoods, List<LocalDate> exams) {
    return check(
        now, recentTexts, recentMoods, exams, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END);
  }

  // 안전 브레이크 결과를 계산합니다.
  //
  // now: 지금 시각. "오늘" 은 하루의 기준(새벽 4시, DayRule)으로 정하고, 연락 금지 시간은 시계 시각 그대로 봅니다.
  // recentTexts: 최근에 쓴 글 원문 (일기·체크인 등 여러 글을 한 번에 넘깁니다)
  // recentMoods: 최근 기분 기록 (순서는 상관없습니다)
  // exams: 등록된 시험 날짜들
  // quietStart, quietEnd: 사용자가 정한 연락 금지 시간. start 부터 end 직전까지입니다.
  public static SafetyBrake check(
      Instant now,
      List<String> recentTexts,
      List<MoodRecord> recentMoods,
      List<LocalDate> exams,
      LocalTime quietStart,
      LocalTime quietEnd) {
    boolean isCrisisDetected = CrisisText.hasCrisisText(recentTexts);
    boolean isExamNear = ExamPeriod.isNearExam(now, exams);
    boolean isMoodLow = LowMoodStreak.hasLowMoodStreak(now, recentMoods);

    return new SafetyBrake(
        isCrisisDetected,
        isCrisisDetected || isExamNear || isMoodLow,
        isCrisisDetected || isMoodLow,
        QuietHours.isQuiet(now, quietStart, quietEnd));
  }
}
