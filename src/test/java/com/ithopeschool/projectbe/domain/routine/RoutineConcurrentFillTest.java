package com.ithopeschool.projectbe.domain.routine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.plan.*;
import com.ithopeschool.projectbe.domain.user.*;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(RoutineService.class)
class RoutineConcurrentFillTest {
  @Autowired RoutineService service;
  @Autowired UserRepository users;
  @Autowired RoutineRepository routines;
  @Autowired StudyPlanRepository plans;
  @Autowired RoutineApplicationRepository applications;
  @Autowired PlatformTransactionManager transactions;
  @MockitoBean CurrentUserProvider current;
  @MockitoBean Clock clock;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRequestsCreateOnlyOnePlanAndMarker() throws Exception {
    LocalDate date = LocalDate.of(2026, 10, 17);
    when(current.canSaveForCurrentUser()).thenReturn(true);
    when(clock.instant()).thenReturn(Instant.parse("2026-10-17T10:00:00Z"));
    TransactionTemplate transaction = new TransactionTemplate(transactions);
    Long userId =
        transaction.execute(
            status -> {
              Long id = users.save(new User("동시 조회")).getId();
              Routine routine = new Routine(id);
              routine.change(List.of(6), PlanKind.PLAY, null, "쉬기", 20, null);
              routines.save(routine);
              return id;
            });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  service.fill(userId, date);
                  return null;
                }));
      start.countDown();
      for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
      assertThat(plans.findByUserIdAndPlanDateOrderById(userId, date)).hasSize(1);
      assertThat(applications.findByUserIdAndPlanDate(userId, date)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
