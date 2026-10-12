package com.ithopeschool.projectbe.domain.routine;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.routine.RoutineFill.RoutineDays;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// "그 날짜에 어느 루틴을 채울지" 규칙(이슈 #85)을 확인합니다.
// feExample 로 시작하는 3개는 FE 함수(getRoutinePlansToAdd) 주석의 예시를 그대로 옮긴 것입니다.
class RoutineFillTest {

  // 요일 숫자 (0 = 일요일 … 6 = 토요일)
  private static final int SUN = 0;
  private static final int MON = 1;
  private static final int TUE = 2;
  private static final int WED = 3;
  private static final int THU = 4;
  private static final int FRI = 5;
  private static final int SAT = 6;

  // FE 예시의 루틴 두 개: A(월·수·금 수학), B(화·목 영어)
  private static final RoutineDays ROUTINE_A = new RoutineDays(1L, List.of(MON, WED, FRI));
  private static final RoutineDays ROUTINE_B = new RoutineDays(2L, List.of(TUE, THU));
  private static final List<RoutineDays> ROUTINES = List.of(ROUTINE_A, ROUTINE_B);

  // FE 예시의 날짜들: 2026-10-12 는 월요일, 오늘은 2026-10-10(토)
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

  // ─── FE 예시 ───

  @Test
  void feExample1FillsTheRoutineOfThatWeekday() {
    // FE 예) 월요일(10/12), 아직 아무것도 안 넣음 → A 만 채웁니다. (B 는 화·목)
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), MONDAY, TODAY)).containsExactly(1L);
  }

  @Test
  void feExample2AlreadyFilledRoutineIsNotFilledAgain() {
    // FE 예) 월요일(10/12), A 는 이미 넣었음 → []
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(1L), MONDAY, TODAY)).isEmpty();
  }

  @Test
  void feExample3PastDateIsNotFilled() {
    // FE 예) 10월 5일(월요일이지만 지난 날짜) → []
    LocalDate lastMonday = LocalDate.of(2026, 10, 5);

    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), lastMonday, TODAY)).isEmpty();
  }

  // ─── 규칙 1: 요일 ───

  @Test
  void weekdayNumbersStartFromSunday() {
    // 요일 숫자는 0 = 일요일 … 6 = 토요일입니다. (이슈 #85 의 weekdays 와 같은 숫자)
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 11))).isEqualTo(SUN);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 12))).isEqualTo(MON);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 13))).isEqualTo(TUE);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 14))).isEqualTo(WED);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 15))).isEqualTo(THU);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 16))).isEqualTo(FRI);
    assertThat(RoutineFill.weekdayOf(LocalDate.of(2026, 10, 17))).isEqualTo(SAT);
  }

  @Test
  void eachDayFillsOnlyItsOwnRoutines() {
    // 화요일(10/13)에는 B 만, 수요일(10/14)에는 A 만, 일요일(10/18)에는 아무것도 채우지 않습니다.
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), LocalDate.of(2026, 10, 13), TODAY))
        .containsExactly(2L);
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), LocalDate.of(2026, 10, 14), TODAY))
        .containsExactly(1L);
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), LocalDate.of(2026, 10, 18), TODAY))
        .isEmpty();
  }

  @Test
  void sundayRoutineIsFilledOnSunday() {
    // 일요일은 7 이 아니라 0 입니다. 주말 루틴(토·일)은 일요일(10/11)에도 채워집니다.
    RoutineDays weekend = new RoutineDays(3L, List.of(SAT, SUN));

    assertThat(
            RoutineFill.routineIdsToFill(
                List.of(weekend), Set.of(), LocalDate.of(2026, 10, 11), TODAY))
        .containsExactly(3L);
  }

  @Test
  void severalRoutinesOfTheSameDayKeepTheirOrder() {
    // 같은 요일의 루틴이 여러 개면 받은 순서대로 모두 채웁니다.
    RoutineDays morning = new RoutineDays(7L, List.of(MON));
    RoutineDays evening = new RoutineDays(5L, List.of(MON, TUE));
    RoutineDays everyDay = new RoutineDays(9L, List.of(SUN, MON, TUE, WED, THU, FRI, SAT));

    assertThat(
            RoutineFill.routineIdsToFill(
                List.of(morning, evening, everyDay), Set.of(), MONDAY, TODAY))
        .containsExactly(7L, 5L, 9L);
  }

  @Test
  void routineWithoutWeekdaysIsNeverFilled() {
    // 요일이 하나도 없는 루틴이나, 0 ~ 6 밖의 숫자만 든 루틴은 어느 날에도 채워지지 않습니다.
    RoutineDays noDays = new RoutineDays(4L, List.of());
    RoutineDays wrongDays = new RoutineDays(5L, List.of(7, -1));

    for (int day = 10; day <= 17; day++) {
      assertThat(
              RoutineFill.routineIdsToFill(
                  List.of(noDays, wrongDays), Set.of(), LocalDate.of(2026, 10, day), TODAY))
          .isEmpty();
    }
  }

  // ─── 규칙 2: 한 날짜에 한 번만 ───

  @Test
  void onlyRoutinesNotYetFilledAreReturned() {
    // 같은 요일의 루틴 셋 중 하나만 이미 넣었으면, 나머지 둘만 채웁니다.
    RoutineDays first = new RoutineDays(1L, List.of(MON));
    RoutineDays second = new RoutineDays(2L, List.of(MON));
    RoutineDays third = new RoutineDays(3L, List.of(MON));

    assertThat(
            RoutineFill.routineIdsToFill(List.of(first, second, third), Set.of(2L), MONDAY, TODAY))
        .containsExactly(1L, 3L);
  }

  @Test
  void askingTwiceDoesNotFillTwice() {
    // 이슈 #85 완료 조건: 같은 날짜를 여러 번 조회해도 계획이 두 번 생기지 않습니다.
    // 첫 번째 결과를 "넣었다" 고 적어 두고 다시 물으면 빈 목록입니다.
    List<Long> firstTime = RoutineFill.routineIdsToFill(ROUTINES, Set.of(), MONDAY, TODAY);
    List<Long> secondTime = RoutineFill.routineIdsToFill(ROUTINES, firstTime, MONDAY, TODAY);

    assertThat(firstTime).containsExactly(1L);
    assertThat(secondTime).isEmpty();
  }

  @Test
  void filledOnAnotherDayDoesNotMatter() {
    // "넣었다" 는 표시는 날짜마다 따로입니다. 부르는 쪽이 "그 날짜의" 표시만 넘기므로,
    // 지난주 월요일에 넣었던 루틴도 이번 주 월요일의 표시가 비어 있으면 다시 채웁니다.
    assertThat(RoutineFill.routineIdsToFill(ROUTINES, Set.of(), MONDAY, TODAY)).containsExactly(1L);
  }

  // ─── 규칙 3: 지난 날짜 ───

  @Test
  void todayAndFutureDatesAreFilled() {
    // 경계값: 오늘은 채우고, 어제는 채우지 않습니다. 한참 뒤의 날짜도 채웁니다.
    RoutineDays everyDay = new RoutineDays(9L, List.of(SUN, MON, TUE, WED, THU, FRI, SAT));
    List<RoutineDays> routines = List.of(everyDay);

    assertThat(RoutineFill.routineIdsToFill(routines, Set.of(), TODAY, TODAY)).containsExactly(9L);
    assertThat(RoutineFill.routineIdsToFill(routines, Set.of(), TODAY.minusDays(1), TODAY))
        .isEmpty();
    assertThat(RoutineFill.routineIdsToFill(routines, Set.of(), TODAY.plusDays(60), TODAY))
        .containsExactly(9L);
  }

  // ─── 빈 입력 ───

  @Test
  void noRoutinesMeansNothingToFill() {
    // 루틴이 하나도 없으면 채울 것도 없습니다.
    assertThat(RoutineFill.routineIdsToFill(List.of(), Set.of(), MONDAY, TODAY)).isEmpty();
  }
}
