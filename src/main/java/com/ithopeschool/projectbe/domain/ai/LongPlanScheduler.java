package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// "긴 계획(30일)" 의 엔진입니다. 계획의 뼈대(목표 · 기간 · 하루 시간 · 할 일 목록)를 날짜별 카드로 나눕니다. (2026-10-18)
// 원칙: 할 일(무엇을)은 AI 가, 날짜 · 시각 · 길이(언제 · 얼마나)는 이 코드가.
//   그래서 "주말은 빼 줘", "하루 30분으로", "8시부터" 같은 요청은 AI 가 숫자만 바꾸고, 여기서 다시 나누면 됩니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. AI 를 부르지 않습니다.
//
// 나누는 순서
//   1. 공부 날: 시작 날 ~ 시험 전날 중 쉬는 요일을 뺀 날
//   2. 총복습 날: 공부 날이 7일 이상이면 마지막 2일, 3 ~ 6일이면 마지막 1일. 2일 이하면 없음
//   3. 용량 맞추기: 할 일 시간 합이 (복습 날을 뺀 공부 날 수 × 하루 시간) 을 넘으면 비례로 줄입니다. (각 20분 이상)
//   4. 고르게 펴기: 할 일이 적으면 하루 시간을 줄여서 기간 전체에 고르게 둡니다. (안 들어가면 하루 시간 그대로)
//   5. 채우기: 할 일 순서대로 하루 남은 시간에 넣습니다. 한 장은 120분까지, 15분 미만 조각은 만들지 않고 다음 날로.
//      여러 장으로 나뉜 할 일은 제목 끝에 " (1/3)" 처럼 붙입니다.
//   6. 시각: 하루 첫 카드는 시작 시각, 다음 카드는 앞 카드 끝 + 10분. 자정을 넘는 카드는 버립니다.
// 다 넣지 못했으면(줄였거나 · 버렸거나) overflow 가 true 입니다. 카드는 60장까지입니다.
public final class LongPlanScheduler {

  // 전체 카드 수
  public static final int MAX_CARDS = 60;
  // 카드 한 장의 최대 길이 (분)
  public static final int MAX_CARD_MINUTES = 120;
  // 이보다 짧은 조각은 만들지 않습니다 (분)
  public static final int MIN_PIECE_MINUTES = 15;
  // 용량에 맞춰 줄여도 할 일 하나에 남기는 시간 (분)
  public static final int MIN_TOPIC_MINUTES = 20;
  // 고르게 펼 때 하루 시간의 최소값 (분). 너무 잘게 나뉘지 않게
  public static final int MIN_SPREAD_MINUTES = 30;
  // 카드 사이 쉬는 시간 (분)
  public static final int BREAK_MINUTES = 10;
  // 하루의 끝 (분). 자정을 넘는 카드는 버립니다.
  private static final int DAY_END_MINUTES = 24 * 60;

  // 총복습 카드
  static final String REVIEW_TITLE = "총복습";
  static final List<String> REVIEW_CHECKLIST = List.of("틀린 문제 다시 풀기", "개념 다시 보기");

  // 나눈 결과. cards 는 날짜 · 시각 순서입니다.
  public record Result(List<DayPlanRevise.Card> cards, boolean overflow) {}

  // 할 일 하나의 조각: 몇 번째 할 일을, 몇 번째 공부 날에, 몇 분
  private record Piece(int topicIndex, int dayIndex, int minutes) {}

  // 채우기 결과. leftover 가 true 면 다 넣지 못한 것입니다.
  private record Fill(List<Piece> pieces, boolean leftover) {}

  private LongPlanScheduler() {}

  // 공부 날: startDate ~ endDate 전날 중 쉬는 요일(MON ~ SUN)을 뺀 날
  //   예) 10-19(월) ~ 11-02(월), 쉬는 요일 SAT · SUN → 10-19 ~ 10-30 의 평일 10일
  public static List<LocalDate> studyDaysOf(
      LocalDate startDate, LocalDate endDate, List<String> restDays) {
    List<LocalDate> days = new ArrayList<>();
    for (LocalDate day = startDate; day.isBefore(endDate); day = day.plusDays(1)) {
      if (!restDays.contains(dayCodeOf(day))) {
        days.add(day);
      }
    }
    return days;
  }

  // 요일 세 글자. 예) 2026-10-19 → "MON"
  public static String dayCodeOf(LocalDate date) {
    return date.getDayOfWeek().name().substring(0, 3);
  }

  // 총복습 날 수: 공부 날 7일 이상이면 2, 3 ~ 6일이면 1, 2일 이하면 0
  public static int reviewDayCount(int studyDayCount) {
    if (studyDayCount >= 7) {
      return 2;
    }
    return studyDayCount >= 3 ? 1 : 0;
  }

  // 할 일에 쓸 수 있는 시간 (분) = 복습 날을 뺀 공부 날 수 × 하루 시간. AI 에게도 알려 줍니다.
  public static int capacityOf(int studyDayCount, int dailyMinutes) {
    return (studyDayCount - reviewDayCount(studyDayCount)) * dailyMinutes;
  }

  // 계획의 뼈대를 날짜별 카드로 나눕니다.
  public static Result schedule(LongPlanOutline plan) {
    List<LocalDate> days = studyDaysOf(plan.startDate(), plan.endDate(), plan.restDays());
    if (days.isEmpty()) {
      return new Result(List.of(), true);
    }
    int reviewCount = reviewDayCount(days.size());
    List<LocalDate> topicDays = days.subList(0, days.size() - reviewCount);
    List<LocalDate> reviewDays = days.subList(days.size() - reviewCount, days.size());
    int daily = plan.dailyMinutes();
    LocalTime startTime = LocalTime.parse(plan.startTime());

    // 3) 용량 맞추기
    List<Integer> minutes = new ArrayList<>();
    for (LongPlanOutline.Topic topic : plan.topics()) {
      minutes.add(topic.minutes());
    }
    int capacity = topicDays.size() * daily;
    boolean overflow = false;
    if (sum(minutes) > capacity) {
      minutes = shrunk(minutes, capacity);
      overflow = true;
    }

    // 4) 고르게 펴기 → 5) 채우기. 펴서 안 들어가면 하루 시간 그대로 다시 채웁니다.
    int maxPieces = MAX_CARDS - reviewCount;
    int spread = Math.min(daily, spreadMinutes(sum(minutes), topicDays.size()));
    Fill fill = fill(minutes, topicDays.size(), spread, maxPieces);
    if (fill.leftover() && spread < daily) {
      fill = fill(minutes, topicDays.size(), daily, maxPieces);
    }
    overflow = overflow || fill.leftover();

    // 6) 시각 붙이기
    List<DayPlanRevise.Card> cards = new ArrayList<>();
    int[] pieceCounts = new int[minutes.size()];
    for (Piece piece : fill.pieces()) {
      pieceCounts[piece.topicIndex()]++;
    }
    int[] pieceNumbers = new int[minutes.size()];
    int currentDay = -1;
    int cursor = 0;
    for (Piece piece : fill.pieces()) {
      if (piece.dayIndex() != currentDay) {
        currentDay = piece.dayIndex();
        cursor = minutesOf(startTime);
      }
      LongPlanOutline.Topic topic = plan.topics().get(piece.topicIndex());
      pieceNumbers[piece.topicIndex()]++;
      if (cursor + piece.minutes() > DAY_END_MINUTES) {
        overflow = true;
        continue;
      }
      String title =
          numbered(
              topic.title(), pieceNumbers[piece.topicIndex()], pieceCounts[piece.topicIndex()]);
      List<String> checklist =
          topic.checklist().isEmpty() ? List.of(topic.title()) : topic.checklist();
      cards.add(
          new DayPlanRevise.Card(
              topicDays.get(piece.dayIndex()),
              timeText(cursor),
              piece.minutes(),
              title,
              checklist));
      cursor += piece.minutes() + BREAK_MINUTES;
    }

    // 2) 총복습 카드
    int reviewMinutes = Math.min(daily, MAX_CARD_MINUTES);
    for (int i = 0; i < reviewDays.size(); i++) {
      if (minutesOf(startTime) + reviewMinutes > DAY_END_MINUTES) {
        overflow = true;
        continue;
      }
      cards.add(
          new DayPlanRevise.Card(
              reviewDays.get(i),
              timeText(minutesOf(startTime)),
              reviewMinutes,
              numbered(REVIEW_TITLE, i + 1, reviewDays.size()),
              REVIEW_CHECKLIST));
    }
    return new Result(cards, overflow);
  }

  // 용량에 맞춰 비례로 줄입니다. 5분 단위로 내리고, 할 일 하나에 20분은 남깁니다.
  //   예) [120, 60] 을 90분에 → [60, 30]
  // 20분씩 남기느라 그래도 넘치면, 채우기에서 뒤의 할 일이 빠집니다.
  static List<Integer> shrunk(List<Integer> minutes, int capacity) {
    int total = sum(minutes);
    List<Integer> result = new ArrayList<>();
    for (int each : minutes) {
      int scaled = (int) ((long) each * capacity / total) / 5 * 5;
      result.add(Math.max(MIN_TOPIC_MINUTES, scaled));
    }
    return result;
  }

  // 고르게 펼 때의 하루 시간: 할 일 합 ÷ 날 수를 5분 단위로 올림. 30분보다 작으면 30분
  //   예) 600분을 28일에 → 25 → 30 / 900분을 10일에 → 90
  static int spreadMinutes(int totalMinutes, int dayCount) {
    int average = (totalMinutes + dayCount - 1) / dayCount;
    int roundedUp = (average + 4) / 5 * 5;
    return Math.max(MIN_SPREAD_MINUTES, roundedUp);
  }

  // 할 일 순서대로 하루 budget 분씩 채웁니다.
  //   - 그날 남은 시간이 15분보다 짧으면 다음 날로 넘어갑니다.
  //   - 한 장은 120분까지
  //   - 남는 꼬리가 15분보다 짧으면 이번 조각에 붙입니다. (그날 시간을 15분 안쪽으로 넘을 수 있음)
  //     붙이면 120분을 넘을 때만 이번 조각을 줄여 꼬리를 15분으로 남깁니다.
  //     예) 70분을 60분 남은 날에 → 70 한 장 / 130분을 새 날에 → 115 + 15
  //   - 날이 모자라거나 조각이 maxPieces 개가 되면 멈추고 leftover = true
  private static Fill fill(List<Integer> minutes, int dayCount, int budget, int maxPieces) {
    List<Piece> pieces = new ArrayList<>();
    int day = 0;
    int remaining = budget;
    for (int topicIndex = 0; topicIndex < minutes.size(); topicIndex++) {
      int left = minutes.get(topicIndex);
      while (left > 0) {
        if (day >= dayCount || pieces.size() >= maxPieces) {
          return new Fill(pieces, true);
        }
        if (remaining < MIN_PIECE_MINUTES) {
          day++;
          remaining = budget;
          continue;
        }
        int piece = Math.min(left, Math.min(remaining, MAX_CARD_MINUTES));
        int tail = left - piece;
        if (tail > 0 && tail < MIN_PIECE_MINUTES) {
          piece =
              piece + tail <= MAX_CARD_MINUTES ? piece + tail : piece - (MIN_PIECE_MINUTES - tail);
        }
        pieces.add(new Piece(topicIndex, day, piece));
        left -= piece;
        remaining -= piece;
      }
    }
    return new Fill(pieces, false);
  }

  // 여러 장으로 나뉜 할 일의 제목. 한 장이면 그대로, 아니면 " (2/3)" 을 붙이고 30자를 넘으면 앞 제목을 줄입니다.
  static String numbered(String title, int number, int count) {
    if (count <= 1) {
      return title;
    }
    String suffix = " (" + number + "/" + count + ")";
    int maxTitle = CardParse.MAX_TITLE_LENGTH - suffix.length();
    String base = title.length() > maxTitle ? title.substring(0, maxTitle).trim() : title;
    return base + suffix;
  }

  private static int sum(List<Integer> minutes) {
    return minutes.stream().mapToInt(Integer::intValue).sum();
  }

  private static int minutesOf(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }

  // 'HH:MM'
  private static String timeText(int minutes) {
    return String.format("%02d:%02d", minutes / 60, minutes % 60);
  }
}
