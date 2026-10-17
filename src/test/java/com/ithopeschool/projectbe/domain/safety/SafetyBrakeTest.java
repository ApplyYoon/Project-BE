package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.CareAction;
import com.ithopeschool.projectbe.domain.care.CareLevel;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak.MoodRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 안전 규칙 4가지를 한 번에 판정하는 SafetyBrake(이슈 #56)를 확인합니다.
// 규칙 하나하나의 자세한 경계값은 CrisisTextTest · ExamPeriodTest · LowMoodStreakTest · QuietHoursTest 가 봅니다.
// 여기서는 "어떤 규칙이 걸리면 결과의 어느 값이 켜지는지" 를 봅니다.
class SafetyBrakeTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 테스트에서 쓰는 "오늘": 2026년 10월 10일
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

  // 오늘 낮 12시. 연락 금지 시간이 아닌 평범한 시각입니다.
  private static final Instant NOON = seoul(TODAY, 12, 0);

  // 아무 규칙에도 걸리지 않는 평범한 입력들
  private static final List<String> ORDINARY_TEXTS = List.of("오늘 학원에서 힘들었다");
  private static final List<MoodRecord> NO_MOODS = List.of();
  private static final List<LocalDate> NO_EXAMS = List.of();

  // 한국 시간의 어느 날 "시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant seoul(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(date, LocalTime.of(hour, minute), SEOUL).toInstant();
  }

  // 오늘까지 6일 연속으로 기분이 낮은(2점) 기록을 만듭니다.
  private static List<MoodRecord> sixLowMoodDays() {
    List<MoodRecord> records = new ArrayList<>();
    for (int daysAgo = 0; daysAgo < 6; daysAgo++) {
      records.add(new MoodRecord(TODAY.minusDays(daysAgo), 2));
    }
    return records;
  }

  @Test
  void nothingIsOnForAnOrdinaryDay() {
    // 글도 기분도 괜찮고, 시험도 없고, 낮이면 아무것도 켜지지 않습니다.
    SafetyBrake brake = SafetyBrake.check(NOON, ORDINARY_TEXTS, NO_MOODS, NO_EXAMS);

    assertThat(brake).isEqualTo(new SafetyBrake(false, false, false, false, false));
  }

  @Test
  void crisisTextStopsWhipAndShowsHelp() {
    // 규칙 1) 위기 표현 → 채찍 즉시 중지 + 회복 모드 + 도움 안내
    SafetyBrake brake = SafetyBrake.check(NOON, List.of("죽고 싶다"), NO_MOODS, NO_EXAMS);

    assertThat(brake.stopWhip()).isTrue();
    assertThat(brake.recoveryMode()).isTrue();
    assertThat(brake.showHelp()).isTrue();
    assertThat(brake.quiet()).isFalse();
  }

  @Test
  void examPeriodOnlyTurnsOnExamPeriod() {
    // 규칙 2) 시험 이틀 전 → examPeriod 만. 회복 모드(0)가 아니라 관리 강도 상한 2 입니다. (이슈 #232)
    // 위기가 아니라서 도움 안내도 띄우지 않습니다.
    List<LocalDate> exams = List.of(TODAY.plusDays(2));

    SafetyBrake brake = SafetyBrake.check(NOON, ORDINARY_TEXTS, NO_MOODS, exams);

    assertThat(brake).isEqualTo(new SafetyBrake(false, false, true, false, false));
  }

  @Test
  void examFarAwayDoesNotTurnOnExamPeriod() {
    // 시험이 4일 뒤면 아직 시험 기간이 아닙니다.
    List<LocalDate> exams = List.of(TODAY.plusDays(4));

    SafetyBrake brake = SafetyBrake.check(NOON, ORDINARY_TEXTS, NO_MOODS, exams);

    assertThat(brake.examPeriod()).isFalse();
    assertThat(brake.recoveryMode()).isFalse();
  }

  @Test
  void lowMoodStreakTurnsOnRecoveryModeAndHelp() {
    // 규칙 3) 기분 저하 6일 연속 → 회복 모드 + 도움 안내. 위기 표현은 아니라서 stopWhip 은 false 입니다.
    SafetyBrake brake = SafetyBrake.check(NOON, ORDINARY_TEXTS, sixLowMoodDays(), NO_EXAMS);

    assertThat(brake.stopWhip()).isFalse();
    assertThat(brake.recoveryMode()).isTrue();
    assertThat(brake.showHelp()).isTrue();
    assertThat(brake.quiet()).isFalse();
  }

  @Test
  void fiveLowMoodDaysAreNotEnough() {
    // 5일만 낮으면 아직 켜지지 않습니다. ("5일 넘게" = 6일 연속)
    List<MoodRecord> fiveDays = sixLowMoodDays().subList(0, 5);

    SafetyBrake brake = SafetyBrake.check(NOON, ORDINARY_TEXTS, fiveDays, NO_EXAMS);

    assertThat(brake.recoveryMode()).isFalse();
    assertThat(brake.showHelp()).isFalse();
  }

  @Test
  void quietHoursOnlyTurnOnQuiet() {
    // 규칙 4) 밤 11시 30분(기본 금지 23:00~07:00) → quiet 만 켜집니다. 나머지 셋과는 따로 정해집니다.
    Instant lateNight = seoul(TODAY, 23, 30);

    SafetyBrake brake = SafetyBrake.check(lateNight, ORDINARY_TEXTS, NO_MOODS, NO_EXAMS);

    assertThat(brake).isEqualTo(new SafetyBrake(false, false, false, false, true));
  }

  @Test
  void customQuietHoursAreUsedWhenGiven() {
    // 금지 시간을 22:30~07:30 으로 정한 사람은 22:40 이 금지 시간이고, 기본값(23:00~)으로는 아닙니다.
    Instant tenForty = seoul(TODAY, 22, 40);

    SafetyBrake custom =
        SafetyBrake.check(
            tenForty,
            ORDINARY_TEXTS,
            NO_MOODS,
            NO_EXAMS,
            LocalTime.of(22, 30),
            LocalTime.of(7, 30));
    SafetyBrake byDefault = SafetyBrake.check(tenForty, ORDINARY_TEXTS, NO_MOODS, NO_EXAMS);

    assertThat(custom.quiet()).isTrue();
    assertThat(byDefault.quiet()).isFalse();
  }

  @Test
  void everythingCanBeOnAtOnce() {
    // 밤늦게 위기 표현이 감지되면 네 값이 모두 켜집니다.
    Instant lateNight = seoul(TODAY, 23, 30);

    SafetyBrake brake = SafetyBrake.check(lateNight, List.of("사라지고 싶어"), NO_MOODS, NO_EXAMS);

    assertThat(brake).isEqualTo(new SafetyBrake(true, true, false, true, true));
  }

  @Test
  void crisisInAnyOneOfManyTextsIsEnough() {
    // 일기·체크인 약속처럼 여러 글을 한 번에 넘겨도, 하나에만 있으면 감지합니다.
    List<String> texts = List.of("수학 2단원 끝내기", "요즘은 살기 싫다", "영어 단어 30개");

    SafetyBrake brake = SafetyBrake.check(NOON, texts, NO_MOODS, NO_EXAMS);

    assertThat(brake.stopWhip()).isTrue();
  }

  @Test
  void todayFollowsTheFourAmDayRule() {
    // "오늘" 은 새벽 4시에 바뀝니다. (DayRule) 시험이 10월 14일일 때:
    List<LocalDate> exams = List.of(LocalDate.of(2026, 10, 14));
    LocalDate nextDay = LocalDate.of(2026, 10, 11);

    // 10월 11일 03:59 는 아직 10월 10일 → 시험 4일 전 → 시험 기간 아님
    SafetyBrake beforeFour = SafetyBrake.check(seoul(nextDay, 3, 59), List.of(), NO_MOODS, exams);
    // 10월 11일 04:00 부터는 10월 11일 → 시험 3일 전 → 시험 기간
    SafetyBrake atFour = SafetyBrake.check(seoul(nextDay, 4, 0), List.of(), NO_MOODS, exams);

    assertThat(beforeFour.examPeriod()).isFalse();
    assertThat(atFour.examPeriod()).isTrue();
    // 연락 금지 시간은 하루의 기준과 상관없이 시계 시각 그대로 봅니다. 새벽 3시 59분·4시는 둘 다 금지 시간입니다.
    assertThat(beforeFour.quiet()).isTrue();
    assertThat(atFour.quiet()).isTrue();
  }

  @Test
  void brakeMakesCareLevelZero() {
    // 이슈 #56 완료 조건: 감지되면 관리 강도(#62)가 0(회복)이 됩니다.
    // 어제 3단계에서 채찍이 나와 4단계가 될 날이어도, 브레이크가 켜지면 0 입니다.
    SafetyBrake brake = SafetyBrake.check(NOON, List.of("죽고 싶다"), NO_MOODS, NO_EXAMS);
    boolean isSafetyBrakeOn = brake.stopWhip() || brake.recoveryMode();

    assertThat(CareLevel.of(3, CareAction.WHIP, 4, isSafetyBrakeOn)).isEqualTo(0);

    // 평범한 날이면 같은 입력이 4단계가 됩니다. (브레이크가 원인이라는 것을 확인)
    SafetyBrake ordinary = SafetyBrake.check(NOON, ORDINARY_TEXTS, NO_MOODS, NO_EXAMS);
    boolean isOrdinaryBrakeOn = ordinary.stopWhip() || ordinary.recoveryMode();

    assertThat(CareLevel.of(3, CareAction.WHIP, 4, isOrdinaryBrakeOn)).isEqualTo(4);
  }
}
