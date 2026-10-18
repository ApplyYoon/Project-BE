package com.ithopeschool.projectbe.domain.card;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.card.ScheduleCheck.SleepWindow;
import com.ithopeschool.projectbe.domain.card.ScheduleCheck.Slot;
import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// FE 의 src/lib/scheduleCheck.ts 주석과 같은 예시로 확인합니다. (이슈 #223)
class ScheduleCheckTest {

  private static LocalTime t(String hhmm) {
    return LocalTime.parse(hhmm);
  }

  @Test
  @DisplayName("수면 보호 시간: 23:00~07:00 은 그대로, 4시간이면 6시간으로 늘리고, 모르면 00:00~06:00")
  void sleepWindow() {
    assertThat(ScheduleCheck.sleepWindow(t("23:00"), t("07:00")))
        .isEqualTo(new SleepWindow(1380, 480, "23:00~07:00"));
    assertThat(ScheduleCheck.sleepWindow(t("01:00"), t("05:00")))
        .isEqualTo(new SleepWindow(60, 360, "01:00~07:00"));
    assertThat(ScheduleCheck.sleepWindow(null, null))
        .isEqualTo(new SleepWindow(0, 360, "00:00~06:00"));
  }

  @Test
  @DisplayName("취침 ~ 기상 길이는 자정을 넘어도 센다: 23:00 → 07:00 은 480분, 01:00 → 06:00 은 300분")
  void sleepMinutes() {
    assertThat(ScheduleCheck.sleepMinutes(t("23:00"), t("07:00"))).isEqualTo(480);
    assertThat(ScheduleCheck.sleepMinutes(t("01:00"), t("06:00"))).isEqualTo(300);
  }

  @Test
  @DisplayName("수면 보호 23:00~07:00 과 겹치는지: 딱 붙은 것은 괜찮고, 걸치거나 한밤중이면 겹친다")
  void overlappingSleep() {
    SleepWindow window = ScheduleCheck.sleepWindow(t("23:00"), t("07:00"));

    assertThat(ScheduleCheck.isOverlappingSleep(t("22:00"), 60, window)).isFalse();
    assertThat(ScheduleCheck.isOverlappingSleep(t("22:30"), 60, window)).isTrue();
    assertThat(ScheduleCheck.isOverlappingSleep(t("06:30"), 60, window)).isTrue();
    assertThat(ScheduleCheck.isOverlappingSleep(t("07:00"), 60, window)).isFalse();
    assertThat(ScheduleCheck.isOverlappingSleep(t("02:00"), 30, window)).isTrue();
    assertThat(ScheduleCheck.isOverlappingSleep(null, 30, window)).isFalse();
  }

  @Test
  @DisplayName("10분 버퍼: 학원 18:00~21:00 뒤 5분 · 딱 10분 · 딱 붙음 · 겹침 · 시간 미정")
  void bufferShortage() {
    Slot academy = new Slot("학원", 1080, 1260, true);
    List<Slot> slots = List.of(academy);

    assertThat(ScheduleCheck.findBufferShortage(t("21:05"), 60, slots))
        .isEqualTo(new ScheduleCheck.Shortage(academy, 5));
    assertThat(ScheduleCheck.findBufferShortage(t("16:50"), 60, slots)).isNull();
    assertThat(ScheduleCheck.findBufferShortage(t("17:30"), 30, slots))
        .isEqualTo(new ScheduleCheck.Shortage(academy, 0));
    assertThat(ScheduleCheck.findBufferShortage(t("20:00"), 60, slots))
        .isEqualTo(new ScheduleCheck.Shortage(academy, -60));
    assertThat(ScheduleCheck.findBufferShortage(null, 60, slots)).isNull();
  }

  @Test
  @DisplayName("버퍼 경고 문장: 1회성은 그날, 반복은 일주일 안의 그 요일마다 보고 요일을 앞에 붙인다")
  void bufferWarning() {
    // 화요일(2)마다 학원 18:00~21:00. 2026-10-20 은 화요일입니다.
    FixedBlock academy =
        new FixedBlock(1L, "학원", List.of(2), t("18:00"), t("21:00"), Instant.EPOCH);
    LocalDate tuesday = LocalDate.of(2026, 10, 20);

    assertThat(
            ScheduleCheck.bufferWarning(
                tuesday, List.of(), t("21:05"), 60, List.of(), List.of(academy), null))
        .isEqualTo("학원 18:00~21:00 과 사이가 5분뿐이에요. 10분 여유를 두면 좋아요.");
    // 반복(화) 카드: 시작일(10-18 일요일)부터 일주일 안의 화요일에 학원과 겹침
    assertThat(
            ScheduleCheck.bufferWarning(
                LocalDate.of(2026, 10, 18),
                List.of(2),
                t("20:00"),
                60,
                List.of(),
                List.of(academy),
                null))
        .isEqualTo("(화) 학원 18:00~21:00 과 시간이 겹쳐요. 10분 여유를 두면 좋아요.");
    // 학원이 없는 수요일이면 경고 없음
    assertThat(
            ScheduleCheck.bufferWarning(
                tuesday.plusDays(1), List.of(), t("20:00"), 60, List.of(), List.of(academy), null))
        .isNull();
  }
}
