package com.ithopeschool.projectbe.domain.fixedblock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 고정 일정 테이블. 한 줄 = 학교 · 학원처럼 요일마다 정해진 시간 하나. (최종 기획서 「4.3 카드」, 이슈 #223, FE 의 FixedBlock)
// 카드가 아니라서 타이머가 없고, 의지력 · 순공 계산에서 빠집니다. 공부할 수 있는 시간을 계산할 때 이 시간을 뺍니다.
//   예: 학교 (월~금 08:00~16:30), 수학 학원 (화 · 목 18:00~21:00)
//
// 하루는 새벽 4시에 시작합니다. (FE 와 같음) 그래서 23:00~01:00 처럼 자정을 넘는 일정도 됩니다. (dayMinutesOf)
// 반복 요일은 카드(cards.repeat_days)와 같은 모양으로 한 칸에 적었습니다.
@Entity
@Table(
    name = "fixed_blocks",
    indexes = @Index(name = "idx_fixed_blocks_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedBlock {

  // 하루가 시작하는 시각을 자정부터 센 분 (새벽 4시 = 240분)
  public static final int DAY_START_MINUTES = 4 * 60;

  // 고정 일정을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 일정의 주인(users.id). 다른 사람의 일정이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 무슨 일정인지 (예: "학교", "수학 학원"). 20자까지 (FE 와 같음)
  @Column(nullable = false, length = 20)
  private String title;

  // 반복 요일을 쉼표로 이은 글자. 0 = 일요일 … 6 = 토요일. 예: 월~금 → "1,2,3,4,5". 하나 이상 있습니다.
  @Column(nullable = false, length = 20)
  private String repeatDays;

  // 시작 시각 (예: 08:00)
  @Column(nullable = false)
  private LocalTime startTime;

  // 끝 시각 (예: 16:30). 하루 시작(새벽 4시) 기준으로 시작보다 뒤입니다.
  @Column(nullable = false)
  private LocalTime endTime;

  // 일정을 만든 시각 (서버에 저장한 시각)
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // 값은 Service 가 검사를 마친 것만 넘깁니다.
  public FixedBlock(
      Long userId,
      String title,
      List<Integer> repeatDays,
      LocalTime startTime,
      LocalTime endTime,
      Instant createdAt) {
    this.userId = userId;
    this.title = title;
    this.repeatDays = toRepeatDaysText(repeatDays);
    this.startTime = startTime;
    this.endTime = endTime;
    this.createdAt = createdAt;
  }

  // ---- 읽기 도우미 ----

  // 반복 요일을 숫자 목록으로 돌려줍니다.   "1,3,5" → [1, 3, 5]
  public List<Integer> repeatDayList() {
    List<Integer> days = new ArrayList<>();
    for (String day : repeatDays.split(",")) {
      days.add(Integer.parseInt(day));
    }
    return days;
  }

  // 그 날짜(요일)에 있는 일정인지. FE 의 getFixedBlocksOnDate 와 같은 규칙입니다.
  //   (2026-10-12 는 월요일) 학교 [1,2,3,4,5] → 10-12 true,  10-18(일) false
  public boolean isOnDate(LocalDate date) {
    return repeatDayList().contains(weekdayOf(date));
  }

  // 날짜의 요일을 FE 와 같은 숫자로 바꿉니다. (0 = 일요일 … 6 = 토요일)
  // 자바의 요일 번호는 월요일 1 … 일요일 7 이라서, 7 로 나눈 나머지를 쓰면 일요일만 0 이 되고 나머지는 그대로입니다.
  public static int weekdayOf(LocalDate date) {
    return date.getDayOfWeek().getValue() % 7;
  }

  // 시각을 "하루 시작(새벽 4시)을 기준으로 한 분" 으로 바꿉니다. FE 의 toDayMinutes 와 같습니다.
  //   04:00 → 240      19:00 → 1140      00:00 → 1440 (다음 날 새벽이라 하루의 끝쪽)      03:59 → 1679
  public static int dayMinutesOf(LocalTime time) {
    int minutes = time.getHour() * 60 + time.getMinute();
    return minutes >= DAY_START_MINUTES ? minutes : minutes + 24 * 60;
  }

  // 끝 시각이 시작보다 뒤인지 (하루 시작 새벽 4시 기준). FE 의 validateFixedBlock 과 같습니다.
  //   18:00 ~ 21:00 → true      18:00 ~ 17:00 → false
  //   23:00 ~ 01:00 → true      03:00 ~ 05:00 → false (새벽 4시를 넘으면 다음 날)
  public static boolean isEndAfterStart(LocalTime startTime, LocalTime endTime) {
    return dayMinutesOf(endTime) > dayMinutesOf(startTime);
  }

  // 반복 요일 목록을 DB 에 적을 글자로 바꿉니다. 작은 숫자부터 줄 세워 잇습니다.   [5, 1, 3] → "1,3,5"
  // 요일이 겹치지 않는지, 0 ~ 6 인지, 하나 이상인지는 Service 가 먼저 검사합니다.
  static String toRepeatDaysText(List<Integer> days) {
    List<Integer> sorted = new ArrayList<>(days);
    sorted.sort(null);
    StringBuilder text = new StringBuilder();
    for (Integer day : sorted) {
      if (!text.isEmpty()) {
        text.append(',');
      }
      text.append(day);
    }
    return text.toString();
  }

  // ---- 바꾸기 (Service 가 검사를 마친 값만 넘깁니다) ----

  // 이름을 바꿉니다.
  public void changeTitle(String title) {
    this.title = title;
  }

  // 반복 요일을 바꿉니다.
  public void changeRepeatDays(List<Integer> days) {
    this.repeatDays = toRepeatDaysText(days);
  }

  // 시작 · 끝 시각을 함께 바꿉니다. (둘 중 하나만 바꿔도 "끝이 시작보다 뒤" 를 함께 봐야 해서 한 번에 받습니다)
  public void changeTime(LocalTime startTime, LocalTime endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }
}
