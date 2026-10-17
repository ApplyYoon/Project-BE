package com.ithopeschool.projectbe.domain.fixedblock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 고정 일정 엔티티의 계산 도우미를 FE(src/lib/fixedBlocks.ts · planConflict.ts)와 같은 예시로 확인합니다.
class FixedBlockTest {

  private static FixedBlock block(List<Integer> days) {
    return new FixedBlock(1L, "학교", days, LocalTime.of(8, 0), LocalTime.of(16, 30), Instant.EPOCH);
  }

  @Test
  @DisplayName("시각을 하루 시작(새벽 4시) 기준 분으로 바꾼다 (FE 의 toDayMinutes 와 같은 예시)")
  void dayMinutes() {
    assertThat(FixedBlock.dayMinutesOf(LocalTime.of(4, 0))).isEqualTo(240);
    assertThat(FixedBlock.dayMinutesOf(LocalTime.of(19, 0))).isEqualTo(1140);
    assertThat(FixedBlock.dayMinutesOf(LocalTime.of(0, 0))).isEqualTo(1440);
    assertThat(FixedBlock.dayMinutesOf(LocalTime.of(3, 59))).isEqualTo(1679);
  }

  @Test
  @DisplayName("끝 시각은 시작보다 뒤여야 하고, 자정을 넘는 23:00~01:00 은 된다")
  void endAfterStart() {
    assertThat(FixedBlock.isEndAfterStart(LocalTime.of(18, 0), LocalTime.of(21, 0))).isTrue();
    assertThat(FixedBlock.isEndAfterStart(LocalTime.of(18, 0), LocalTime.of(17, 0))).isFalse();
    assertThat(FixedBlock.isEndAfterStart(LocalTime.of(18, 0), LocalTime.of(18, 0))).isFalse();
    assertThat(FixedBlock.isEndAfterStart(LocalTime.of(23, 0), LocalTime.of(1, 0))).isTrue();
    // 새벽 4시를 넘어가면 다음 날이라 안 됩니다.
    assertThat(FixedBlock.isEndAfterStart(LocalTime.of(3, 0), LocalTime.of(5, 0))).isFalse();
  }

  @Test
  @DisplayName("반복 요일은 작은 숫자부터 쉼표로 이어 적고, 숫자 목록으로 다시 꺼낸다")
  void repeatDays() {
    FixedBlock block = block(List.of(5, 1, 3));

    assertThat(block.getRepeatDays()).isEqualTo("1,3,5");
    assertThat(block.repeatDayList()).containsExactly(1, 3, 5);

    block.changeRepeatDays(List.of(0));
    assertThat(block.getRepeatDays()).isEqualTo("0");
  }

  @Test
  @DisplayName("그 날짜의 요일에 있는 일정인지 본다 (일요일 = 0)")
  void isOnDate() {
    FixedBlock school = block(List.of(1, 2, 3, 4, 5));

    // 2026-10-12 는 월요일, 10-18 은 일요일
    assertThat(FixedBlock.weekdayOf(LocalDate.of(2026, 10, 12))).isEqualTo(1);
    assertThat(FixedBlock.weekdayOf(LocalDate.of(2026, 10, 18))).isZero();
    assertThat(school.isOnDate(LocalDate.of(2026, 10, 12))).isTrue();
    assertThat(school.isOnDate(LocalDate.of(2026, 10, 18))).isFalse();
  }
}
