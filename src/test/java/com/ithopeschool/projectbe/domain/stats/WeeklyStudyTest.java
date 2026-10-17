package com.ithopeschool.projectbe.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ithopeschool.projectbe.domain.stats.WeeklyStudy.Day;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 최근 7일 막대그래프 값의 계산 규칙(이슈 #36)을 확인합니다. 오늘은 2026-10-11(일) 입니다.
class WeeklyStudyTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 한국 시간 10월 "day일 시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, SEOUL).toInstant();
  }

  // 10월 day일 hour시에 시작해서 minutes 분 공부한 기록을 만듭니다. (일시정지 없음)
  private static DaySession study(int day, int hour, int minutes) {
    Instant startedAt = at(day, hour, 0);
    return new DaySession(startedAt, startedAt.plusSeconds(minutes * 60L), 0, 0);
  }

  @Test
  void sevenDaysEndWithToday() {
    // 월 60분 · 목 120분 · 일(오늘) 30분
    List<DaySession> sessions = List.of(study(5, 19, 60), study(8, 19, 120), study(11, 10, 30));

    WeeklyStudy weekly = WeeklyStudy.of(sessions, TODAY);

    // 가장 많이 한 목요일(120분)이 100% 이고, 나머지는 그 비율입니다. 요일은 일 0 ~ 토 6 입니다.
    assertThat(weekly.days())
        .containsExactly(
            new Day(october(5), 1, 60, 50.0, false),
            new Day(october(6), 2, 0, 0.0, false),
            new Day(october(7), 3, 0, 0.0, false),
            new Day(october(8), 4, 120, 100.0, false),
            new Day(october(9), 5, 0, 0.0, false),
            new Day(october(10), 6, 0, 0.0, false),
            new Day(october(11), 0, 30, 25.0, true));
    assertThat(weekly.totalMinutes()).isEqualTo(210);
  }

  @Test
  void noStudyMeansAllBarsAreZero() {
    // 모든 날이 0분이면 0 으로 나눌 수 없으니 막대는 전부 0 입니다. 그래도 7일은 그대로 나옵니다.
    WeeklyStudy weekly = WeeklyStudy.of(List.of(), TODAY);

    assertThat(weekly.days()).hasSize(7);
    assertThat(weekly.days()).allMatch(day -> day.minutes() == 0 && day.percent() == 0.0);
    assertThat(weekly.days().get(6)).isEqualTo(new Day(TODAY, 0, 0, 0.0, true));
    assertThat(weekly.totalMinutes()).isZero();
  }

  @Test
  void recordsOutsideTheSevenDaysAreIgnored() {
    // 7일 전(10/04)과 내일(10/12)의 기록은 세지 않습니다. 첫날(10/05)과 오늘(10/11)은 셉니다.
    List<DaySession> sessions =
        List.of(study(4, 19, 500), study(5, 19, 10), study(11, 19, 20), study(12, 19, 500));

    WeeklyStudy weekly = WeeklyStudy.of(sessions, TODAY);

    assertThat(weekly.totalMinutes()).isEqualTo(30);
    assertThat(weekly.days().get(0).minutes()).isEqualTo(10);
    assertThat(weekly.days().get(6).minutes()).isEqualTo(20);
  }

  @Test
  void percentIsNotRounded() {
    // 30분 · 20분 · 10분 → 100% · 66.66…% · 33.33…%
    List<DaySession> sessions = List.of(study(9, 19, 30), study(10, 19, 20), study(11, 19, 10));

    List<Day> days = WeeklyStudy.of(sessions, TODAY).days();

    assertThat(days.get(4).percent()).isEqualTo(100.0);
    assertThat(days.get(5).percent()).isCloseTo(200.0 / 3, within(1e-9));
    assertThat(days.get(6).percent()).isCloseTo(100.0 / 3, within(1e-9));
  }

  @Test
  void dayMinutesAreSummedBeforeRounding() {
    // 30초짜리 기록 4개는 2분입니다. (하나씩 반올림하면 4분이 됩니다)
    Instant start = at(11, 19, 0);
    List<DaySession> sessions =
        List.of(
            new DaySession(start, start.plusSeconds(30), 0, 0),
            new DaySession(start.plusSeconds(60), start.plusSeconds(90), 0, 0),
            new DaySession(start.plusSeconds(120), start.plusSeconds(150), 0, 0),
            new DaySession(start.plusSeconds(180), start.plusSeconds(210), 0, 0));

    assertThat(WeeklyStudy.of(sessions, TODAY).days().get(6).minutes()).isEqualTo(2);
  }

  @Test
  void todayFollowsTheFourAmDayRule() {
    // 10월 12일 새벽 2시는 아직 10월 11일입니다. 그래서 맨 뒤 막대는 10월 11일이고,
    // 10월 12일 새벽 1시에 시작한 공부(40분)도 10월 11일 막대에 들어갑니다.
    List<DaySession> sessions = List.of(study(12, 1, 40));

    WeeklyStudy lateNight = WeeklyStudy.of(sessions, at(12, 2, 0));

    assertThat(lateNight.days().get(6)).isEqualTo(new Day(october(11), 0, 40, 100.0, true));
    assertThat(lateNight.days().get(0).date()).isEqualTo(october(5));

    // 새벽 4시가 되면 하루가 바뀌어서 맨 뒤 막대는 10월 12일(월)입니다.
    WeeklyStudy morning = WeeklyStudy.of(sessions, at(12, 4, 0));

    assertThat(morning.days().get(6)).isEqualTo(new Day(october(12), 1, 0, 0.0, true));
    assertThat(morning.days().get(5).minutes()).isEqualTo(40);
  }

  @Test
  void sevenDaysCrossMonthBoundary() {
    // 오늘이 10월 3일이면 9월 27일부터 시작합니다.
    List<Day> days = WeeklyStudy.of(List.of(), october(3)).days();

    assertThat(days.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 27));
    assertThat(days.get(0).weekday()).isEqualTo(0);
    assertThat(days.get(6).date()).isEqualTo(october(3));
    assertThat(days.get(6).weekday()).isEqualTo(6);
  }
}
