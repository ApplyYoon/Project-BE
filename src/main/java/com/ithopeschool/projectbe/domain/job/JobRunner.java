package com.ithopeschool.projectbe.domain.job;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 이름으로 예약 작업을 찾아 실행하는 곳입니다. 작업을 실행할 때 지킬 안전 규칙이 여기에 모여 있습니다.
//   1) 같은 작업이 이미 실행 중이면 또 시작하지 않고 건너뜁니다. (겹쳐 돌면 결과가 꼬일 수 있어서)
//   2) 작업이 실패해도 서버는 멀쩡하게 두고, 실패했다고만 알려 줍니다.
//   3) 작업이 끝나면(성공이든 실패든) "실행 중" 표시를 꼭 지웁니다. (안 지우면 영영 건너뛰게 됩니다)
@Service
public class JobRunner {

  private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

  // 이름 → 작업. ScheduledJob 을 구현한 @Component 를 Spring 이 전부 모아서 넣어 줍니다.
  private final Map<String, ScheduledJob> jobs = new HashMap<>();

  // 지금 실행 중인 작업 이름들. 여러 요청이 동시에 와도 안전한 Set 입니다.
  private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

  private final Clock clock;

  public JobRunner(List<ScheduledJob> scheduledJobs, Clock clock) {
    this.clock = clock;
    for (ScheduledJob job : scheduledJobs) {
      // 이름이 같은 작업이 둘이면 어느 쪽을 실행해야 할지 알 수 없으니, 서버를 켤 때 바로 알려 줍니다.
      if (jobs.put(job.name(), job) != null) {
        throw new IllegalStateException("예약 작업 이름이 겹칩니다: " + job.name());
      }
    }
  }

  // 작업을 한 번 실행합니다. 그런 이름의 작업이 없으면 빈 값(Optional.empty)을 돌려줍니다.
  public Optional<JobRunResult> run(String name) {
    ScheduledJob job = jobs.get(name);
    if (job == null) {
      // 없는 이름은 로그에 남기지 않습니다. (주소에 아무 글자나 넣어 로그를 어지럽히는 걸 막으려고)
      return Optional.empty();
    }

    Instant now = clock.instant();
    JobContext context = new JobContext(DayRule.dateOf(now), now);

    // add 가 false 면 같은 이름이 이미 들어 있다는 뜻 = 이미 실행 중입니다.
    if (!runningJobs.add(name)) {
      log.info("[작업] {} 은(는) 이미 실행 중이라 건너뜁니다.", name);
      return Optional.of(new JobRunResult(name, JobRunStatus.SKIPPED, context.date(), now));
    }

    try {
      job.run(context);
      return Optional.of(new JobRunResult(name, JobRunStatus.DONE, context.date(), now));
    } catch (RuntimeException e) {
      // 예외 메시지에는 DB 주소 같은 비밀이 섞일 수 있어서, 예외의 "종류" 만 로그에 남깁니다.
      log.error("[작업] {} 실행에 실패했습니다: {}", name, e.getClass().getSimpleName());
      return Optional.of(new JobRunResult(name, JobRunStatus.FAILED, context.date(), now));
    } finally {
      // 성공하든 실패하든 "실행 중" 표시는 반드시 지웁니다.
      runningJobs.remove(name);
    }
  }
}
