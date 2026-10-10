package com.ithopeschool.projectbe.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

// 작업 실행기의 안전 규칙을 확인하는 단위 테스트입니다. 서버를 켜지 않고, 가짜 작업과 고정한 시계를 씁니다.
@ExtendWith(OutputCaptureExtension.class)
class JobRunnerTest {

  // 시계를 한국 시간 2026-10-10 17:20:03 (= UTC 08:20:03) 에 고정합니다.
  private static final Instant NOW = Instant.parse("2026-10-10T08:20:03Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  // 이름과 할 일만 주면 만들어지는 가짜 작업입니다.
  private static ScheduledJob job(String name, Consumer<JobContext> body) {
    return new ScheduledJob() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public void run(JobContext context) {
        body.accept(context);
      }
    };
  }

  @Test
  void runsTheJobAndReportsDone() {
    AtomicInteger runs = new AtomicInteger();
    JobRunner runner = new JobRunner(List.of(job("demo", ctx -> runs.incrementAndGet())), CLOCK);

    JobRunResult result = runner.run("demo").orElseThrow();

    assertThat(runs).hasValue(1);
    assertThat(result.job()).isEqualTo("demo");
    assertThat(result.status()).isEqualTo(JobRunStatus.DONE);
    assertThat(result.ranAt()).isEqualTo(NOW);
  }

  @Test
  void unknownJobReturnsEmpty() {
    JobRunner runner = new JobRunner(List.of(job("demo", ctx -> {})), CLOCK);

    assertThat(runner.run("nothing")).isEqualTo(Optional.empty());
  }

  @Test
  void jobReceivesTheDateByTheDayRule() {
    // UTC 2026-09-28 18:30 = 한국 시간 9월 29일 03:30 → 새벽 4시 전이라 전날(9월 28일)입니다.
    Clock lateNight = Clock.fixed(Instant.parse("2026-09-28T18:30:00Z"), ZoneOffset.UTC);
    LocalDate[] seen = new LocalDate[1];
    JobRunner runner = new JobRunner(List.of(job("demo", ctx -> seen[0] = ctx.date())), lateNight);

    JobRunResult result = runner.run("demo").orElseThrow();

    assertThat(seen[0]).isEqualTo(LocalDate.of(2026, 9, 28));
    assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void duplicateJobNamesFailAtStartup() {
    // 이름이 같은 작업이 둘이면 서버를 켜는 순간 알려 줍니다. (어느 쪽을 실행할지 알 수 없어서)
    assertThatThrownBy(
            () -> new JobRunner(List.of(job("same", ctx -> {}), job("same", ctx -> {})), CLOCK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("same");
  }

  @Test
  void sameJobIsSkippedWhileItIsAlreadyRunning() throws Exception {
    // 첫 번째 실행이 끝나지 않은 사이에 같은 작업이 또 불리는 상황을 만듭니다.
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ScheduledJob slow =
        job(
            "slow",
            ctx -> {
              started.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    JobRunner runner = new JobRunner(List.of(slow), CLOCK);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      Future<Optional<JobRunResult>> first = executor.submit(() -> runner.run("slow"));
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      // 첫 번째가 도는 중에 또 부르면 → 겹쳐서 돌지 않고 건너뜁니다.
      assertThat(runner.run("slow").orElseThrow().status()).isEqualTo(JobRunStatus.SKIPPED);

      release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).orElseThrow().status())
          .isEqualTo(JobRunStatus.DONE);

      // 끝난 뒤에는 "실행 중" 표시가 지워져서 다시 실행할 수 있어야 합니다.
      assertThat(runner.run("slow").orElseThrow().status()).isEqualTo(JobRunStatus.DONE);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void failedJobReportsFailedAndCanRunAgain(CapturedOutput output) {
    AtomicInteger calls = new AtomicInteger();
    JobRunner runner =
        new JobRunner(
            List.of(
                job(
                    "flaky",
                    ctx -> {
                      // 첫 번째는 실패, 두 번째부터 성공하는 작업입니다. 예외 메시지에 비밀이 섞인 상황도 흉내 냅니다.
                      if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("jdbc:postgresql://secret-host/db");
                      }
                    })),
            CLOCK);

    assertThat(runner.run("flaky").orElseThrow().status()).isEqualTo(JobRunStatus.FAILED);
    // 실패해도 "실행 중" 표시가 남아 영영 건너뛰면 안 됩니다.
    assertThat(runner.run("flaky").orElseThrow().status()).isEqualTo(JobRunStatus.DONE);
    // 로그에는 예외의 종류만 남고, 메시지(비밀이 섞였을 수 있는 글자)는 없어야 합니다.
    assertThat(output).contains("IllegalStateException");
    assertThat(output).doesNotContain("secret-host");
  }

  @Test
  void runningTwiceInARowRunsTheJobBothTimesAndKeepsItSafe() {
    // 순서대로 두 번 부르면 둘 다 실행됩니다. (건너뛰기는 "동시에" 겹칠 때만입니다)
    AtomicInteger runs = new AtomicInteger();
    JobRunner runner = new JobRunner(List.of(job("demo", ctx -> runs.incrementAndGet())), CLOCK);

    runner.run("demo");
    runner.run("demo");

    assertThat(runs).hasValue(2);
  }
}
