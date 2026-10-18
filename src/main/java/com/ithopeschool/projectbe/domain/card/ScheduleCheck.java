package com.ithopeschool.projectbe.domain.card;

import com.ithopeschool.projectbe.domain.fixedblock.FixedBlock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 카드를 저장하기 전에 "수면 보호 시간" 과 "10분 버퍼" 를 검사하는 규칙입니다. (이슈 #223, 기획서 「4.3 카드」)
// FE 의 src/lib/scheduleCheck.ts 와 같은 규칙 · 같은 예시입니다. 한쪽을 고치면 다른 쪽도 같이 고쳐 주세요.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
//   ■ 수면 보호: 겹치면 저장할 수 없습니다. (막음, CardService 가 400)
//   ■ 10분 버퍼: 앞뒤 카드 · 고정 일정과 10분 넘게 떨어져 있지 않으면 경고만 합니다. (막지 않음, 응답의 bufferWarning)
public final class ScheduleCheck {

  // 수면 보호 시간은 최소 6시간입니다. 취침 ~ 기상이 이보다 짧으면 6시간으로 늘려서 지킵니다.
  public static final int SLEEP_PROTECT_MIN_MINUTES = 6 * 60;
  // 카드 사이에 권하는 여유 시간(분)
  public static final int BUFFER_MINUTES = 10;
  // 취침 · 기상 시각을 아직 안 알려 줬을 때 쓰는 값. 00:00 · 06:00
  static final LocalTime DEFAULT_SLEEP = LocalTime.MIDNIGHT;
  static final LocalTime DEFAULT_WAKE = LocalTime.of(6, 0);

  private static final int DAY_MINUTES = 24 * 60;
  // 하루의 시작(새벽 4시)을 분으로. 이보다 이른 시각은 "그날의 밤 끝쪽" 에 놓습니다. (DayRule)
  private static final int DAY_START_MINUTES = 4 * 60;

  private ScheduleCheck() {}

  // 수면 보호 시간. start: 잠드는 시각(자정부터 몇 분째) / minutes: 지키는 길이(최소 360) / label: "00:30~06:30"
  public record SleepWindow(int start, int minutes, String label) {}

  // 그날 이미 시간을 차지한 것 하나 (카드 또는 고정 일정). start · end 는 하루 시작 새벽 4시 기준 분
  public record Slot(String title, int start, int end, boolean fixed) {}

  // 여유가 모자란 상대와 그 사이 시간(분). 0 이면 딱 붙음, 음수면 겹침
  public record Shortage(Slot slot, int gapMinutes) {}

  // 취침 · 기상 시각으로 수면 보호 시간을 만듭니다. 6시간보다 짧으면 기상 시각을 뒤로 밀어 6시간을 채웁니다.
  // 시각이 없으면 기본값(00:00 · 06:00)을 씁니다.
  //
  //   sleepWindow(23:00, 07:00) → (1380, 480, "23:00~07:00")
  //   sleepWindow(01:00, 05:00) → (60,   360, "01:00~07:00")  (4시간 → 6시간)
  //   sleepWindow(null,  null)  → (0,    360, "00:00~06:00")
  public static SleepWindow sleepWindow(LocalTime sleepTime, LocalTime wakeUpTime) {
    int start = minutesOf(sleepTime == null ? DEFAULT_SLEEP : sleepTime);
    int wake = minutesOf(wakeUpTime == null ? DEFAULT_WAKE : wakeUpTime);
    // 자정을 넘어가도 되도록 하루(1440분)를 더하고 나머지를 구합니다. 23:00 → 07:00 = 480분
    int length = (wake - start + DAY_MINUTES) % DAY_MINUTES;
    int minutes = Math.max(length, SLEEP_PROTECT_MIN_MINUTES);
    String label = format(start) + "~" + format((start + minutes) % DAY_MINUTES);
    return new SleepWindow(start, minutes, label);
  }

  // 취침 ~ 기상이 몇 분인지 (자정을 넘어도 됨). 설문 저장 때 6시간 미만을 막는 데 씁니다.
  //   sleepMinutes(23:00, 07:00) → 480 / sleepMinutes(01:00, 06:00) → 300
  public static int sleepMinutes(LocalTime sleepTime, LocalTime wakeUpTime) {
    return (minutesOf(wakeUpTime) - minutesOf(sleepTime) + DAY_MINUTES) % DAY_MINUTES;
  }

  // 카드 시간(시작 + 길이)이 수면 보호 시간과 겹치는지 봅니다. 시작 시각이 없으면 false 입니다.
  // 끝 = 잠드는 시각, 시작 = 일어나는 시각처럼 딱 붙은 것은 겹치지 않습니다.
  //
  //   수면 보호 23:00~07:00 일 때
  //   22:00 · 60분 → false (22:00~23:00, 딱 붙음)   22:30 · 60분 → true (23:00~23:30 이 겹침)
  //   06:30 · 60분 → true  (06:30~07:00 이 겹침)    07:00 · 60분 → false
  //   02:00 · 30분 → true  (한밤중)
  public static boolean isOverlappingSleep(
      LocalTime startTime, int durationMinutes, SleepWindow window) {
    if (startTime == null || durationMinutes <= 0) {
      return false;
    }
    int start = minutesOf(startTime);
    int end = start + durationMinutes;
    // 수면 시간은 매일 반복되므로 어제 · 오늘 · 내일 밤 세 번을 모두 비교합니다.
    for (int dayShift : new int[] {-DAY_MINUTES, 0, DAY_MINUTES}) {
      int sleepStart = window.start() + dayShift;
      int sleepEnd = sleepStart + window.minutes();
      if (start < sleepEnd && sleepStart < end) {
        return true;
      }
    }
    return false;
  }

  // 그 날짜의 다른 카드 · 고정 일정을 "차지한 시간" 목록으로 바꿉니다.
  // 시작 시각이 없는 카드와 고치는 중인 카드 자신(ignoreCardId)은 뺍니다.
  public static List<Slot> slotsOn(
      List<Card> cards, List<FixedBlock> blocks, LocalDate date, Long ignoreCardId) {
    List<Slot> slots = new ArrayList<>();
    for (Card card : cards) {
      if (card.getId() != null && card.getId().equals(ignoreCardId)) {
        continue;
      }
      if (!card.isOnDate(date) || card.getStartTime() == null) {
        continue;
      }
      int start = toDayMinutes(card.getStartTime());
      slots.add(new Slot(card.getTitle(), start, start + card.getDurationMinutes(), false));
    }
    for (FixedBlock block : blocks) {
      if (!block.isOnDate(date)) {
        continue;
      }
      slots.add(
          new Slot(
              block.getTitle(),
              toDayMinutes(block.getStartTime()),
              toDayMinutes(block.getEndTime()),
              true));
    }
    return slots;
  }

  // 카드 시간의 앞뒤로 10분 여유가 없는 일정 중 가장 가까운 것을 찾습니다. 다 넉넉하면 null 입니다.
  // 떨어진 시간 = (상대 시작 − 카드 끝) 과 (카드 시작 − 상대 끝) 중 큰 값. 두 일정이 겹치면 음수가 됩니다.
  //
  //   다른 일정: 학원 18:00~21:00
  //   21:05 · 60분 → (학원, 5)    16:50 · 60분 → null (17:50 끝, 딱 10분 여유)
  //   17:30 · 30분 → (학원, 0)    20:00 · 60분 → (학원, -60)    시작 시각 없음 → null
  public static Shortage findBufferShortage(
      LocalTime startTime, int durationMinutes, List<Slot> slots) {
    if (startTime == null || durationMinutes <= 0) {
      return null;
    }
    int start = toDayMinutes(startTime);
    int end = start + durationMinutes;
    Shortage closest = null;
    for (Slot slot : slots) {
      int gap = Math.max(slot.start() - end, start - slot.end());
      if (gap >= BUFFER_MINUTES) {
        continue;
      }
      if (closest == null || gap < closest.gapMinutes()) {
        closest = new Shortage(slot, gap);
      }
    }
    return closest;
  }

  // 카드 응답에 붙일 버퍼 경고 문장. 여유가 넉넉하면 null 입니다. (경고만, 저장은 막지 않음)
  // 반복 카드는 시작 날짜부터 일주일 안에서 그 요일들을 하나씩 봅니다.
  //
  //   1회성, 학원(18:00~21:00) 뒤 21:05 시작 → "학원 18:00~21:00 과 사이가 5분뿐이에요. 10분 여유를 두면 좋아요."
  //   반복(화), 화요일 학원과 겹침            → "(화) 학원 18:00~21:00 과 시간이 겹쳐요. 10분 여유를 두면 좋아요."
  public static String bufferWarning(
      LocalDate date,
      List<Integer> repeatDays,
      LocalTime startTime,
      int durationMinutes,
      List<Card> otherCards,
      List<FixedBlock> blocks,
      Long ignoreCardId) {
    boolean repeat = !repeatDays.isEmpty();
    List<LocalDate> dates = new ArrayList<>();
    if (repeat) {
      for (int offset = 0; offset < 7; offset++) {
        LocalDate day = date.plusDays(offset);
        if (repeatDays.contains(weekdayOf(day))) {
          dates.add(day);
        }
      }
    } else {
      dates.add(date);
    }
    for (LocalDate day : dates) {
      Shortage shortage =
          findBufferShortage(
              startTime, durationMinutes, slotsOn(otherCards, blocks, day, ignoreCardId));
      if (shortage == null) {
        continue;
      }
      String prefix =
          repeat
              ? "(" + day.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.KOREAN) + ") "
              : "";
      Slot slot = shortage.slot();
      String range = format(slot.start() % DAY_MINUTES) + "~" + format(slot.end() % DAY_MINUTES);
      String situation;
      if (shortage.gapMinutes() < 0) {
        situation = slot.title() + " " + range + " 과 시간이 겹쳐요.";
      } else if (shortage.gapMinutes() == 0) {
        situation = slot.title() + " " + range + " 과 딱 붙어 있어요.";
      } else {
        situation = slot.title() + " " + range + " 과 사이가 " + shortage.gapMinutes() + "분뿐이에요.";
      }
      return prefix + situation + " " + BUFFER_MINUTES + "분 여유를 두면 좋아요.";
    }
    return null;
  }

  // 하루 시작(새벽 4시) 기준 분. 04:00 → 240 / 19:00 → 1140 / 00:00 → 1440 (다음 날 새벽이라 하루의 끝쪽)
  static int toDayMinutes(LocalTime time) {
    int minutes = minutesOf(time);
    return minutes >= DAY_START_MINUTES ? minutes : minutes + DAY_MINUTES;
  }

  // 요일 숫자. 0 = 일요일, 1 = 월요일 … 6 = 토요일 (카드 · 고정 일정의 repeatDays 와 같은 숫자)
  private static int weekdayOf(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SUNDAY ? 0 : day.getValue();
  }

  private static int minutesOf(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }

  private static String format(int minutes) {
    return String.format("%02d:%02d", minutes / 60, minutes % 60);
  }
}
