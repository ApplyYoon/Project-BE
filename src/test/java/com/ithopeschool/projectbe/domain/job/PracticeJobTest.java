package com.ithopeschool.projectbe.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

// 연습 작업을 확인합니다. 핵심은 "같은 날 두 번 돌아도 결과가 망가지지 않는다(덮어쓰기)" 입니다.
@ExtendWith(OutputCaptureExtension.class)
class PracticeJobTest {

  private final JobResultStore store = new JobResultStore();
  private final PracticeJob job = new PracticeJob(store);

  private static JobContext at(LocalDate date) {
    return new JobContext(date, Instant.parse("2026-10-10T08:20:03Z"));
  }

  @Test
  void leavesOneLogLine(CapturedOutput output) {
    job.run(at(LocalDate.of(2026, 10, 10)));

    // 10분마다 로그 한 줄이 남는지 눈으로 확인하려고 만든 작업입니다.
    assertThat(output).contains("[연습 작업] 실행됐어요");
    assertThat(output).contains("2026-10-10");
  }

  @Test
  void usesThePracticeNameForTheAddress() {
    assertThat(job.name()).isEqualTo("practice");
  }

  @Test
  void runningTwiceOnTheSameDayOverwritesInsteadOfAdding() {
    LocalDate day = LocalDate.of(2026, 10, 10);

    job.run(at(day));
    String afterFirst = store.find("practice", day).orElseThrow();
    int sizeAfterFirst = store.size();

    job.run(at(day));
    String afterSecond = store.find("practice", day).orElseThrow();

    // 두 번 돌려도 칸은 그대로 1개이고, 내용도 같습니다. (망가지지 않음)
    assertThat(store.size()).isEqualTo(sizeAfterFirst).isEqualTo(1);
    assertThat(afterSecond).isEqualTo(afterFirst);
  }

  @Test
  void runningManyTimesOnTheSameDayStillKeepsOneResult() {
    LocalDate day = LocalDate.of(2026, 10, 10);

    for (int i = 0; i < 20; i++) {
      job.run(at(day));
    }

    assertThat(store.size()).isEqualTo(1);
  }

  @Test
  void aNewDayGetsItsOwnResultAndKeepsTheOldOne() {
    job.run(at(LocalDate.of(2026, 10, 10)));
    job.run(at(LocalDate.of(2026, 10, 11)));

    assertThat(store.size()).isEqualTo(2);
    assertThat(store.find("practice", LocalDate.of(2026, 10, 10))).isPresent();
    assertThat(store.find("practice", LocalDate.of(2026, 10, 11))).isPresent();
  }
}
