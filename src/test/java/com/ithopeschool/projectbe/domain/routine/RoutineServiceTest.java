package com.ithopeschool.projectbe.domain.routine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.plan.*;
import com.ithopeschool.projectbe.domain.routine.dto.*;
import com.ithopeschool.projectbe.domain.subject.*;
import com.ithopeschool.projectbe.domain.user.*;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({RoutineService.class, StudyPlanService.class})
class RoutineServiceTest {
  @Autowired RoutineService service;
  @Autowired StudyPlanService planService;
  @Autowired UserRepository users;
  @Autowired SubjectRepository subjects;
  @Autowired StudyPlanRepository plans;
  @Autowired RoutineApplicationRepository applications;
  @MockitoBean CurrentUserProvider current;
  @MockitoBean Clock clock;
  Long userId;
  String subjectId;
  LocalDate today = LocalDate.of(2026, 10, 17);

  @BeforeEach
  void setup() {
    userId = users.save(new User("루틴 테스트")).getId();
    subjectId = subjects.save(new Subject(userId, "수학", SubjectColor.BLUE)).getId().toString();
    when(current.getCurrentUserId()).thenReturn(userId);
    when(current.canSaveForCurrentUser()).thenReturn(true);
    when(clock.instant()).thenReturn(Instant.parse("2026-10-17T10:00:00Z"));
  }

  RoutineRequest request(List<Integer> days) {
    return new RoutineRequest(days, "study", subjectId, "복습", 30, "19:00");
  }

  @Test
  void fillsOnlyOnceAndDeletionDoesNotRegenerate() {
    String id = service.create(request(List.of(6))).id();
    assertThat(planService.getPlans(today)).hasSize(1);
    assertThat(planService.getPlans(today)).hasSize(1);
    StudyPlan plan = plans.findByUserIdAndPlanDateOrderById(userId, today).getFirst();
    assertThat(plan.getRoutineId()).isEqualTo(Long.valueOf(id));
    planService.delete(plan.getId());
    assertThat(planService.getPlans(today)).isEmpty();
    assertThat(applications.findByUserIdAndPlanDate(userId, today)).hasSize(1);
  }

  @Test
  void pastDatesAndOtherWeekdaysAreNotFilled() {
    service.create(request(List.of(6)));
    assertThat(planService.getPlans(today.minusDays(7))).isEmpty();
    assertThat(planService.getPlans(today.plusDays(1))).isEmpty();
    assertThat(planService.getPlans(today.plusDays(7))).hasSize(1);
  }

  @Test
  void modificationsAndDeletionLeaveMaterializedPlansUnchanged() {
    Long id = Long.valueOf(service.create(request(List.of(6))).id());
    planService.getPlans(today);
    service.update(id, new RoutineRequest(null, null, null, "새 내용", 50, null));
    StudyPlan plan = plans.findByUserIdAndPlanDateOrderById(userId, today).getFirst();
    assertThat(plan.getContent()).isEqualTo("복습");
    assertThat(plan.getTargetMinutes()).isEqualTo(30);
    service.delete(id);
    assertThat(planService.getPlans(today)).hasSize(1);
  }

  @Test
  void scopesListUpdateDeleteAndSubjectsToOwner() {
    Long id = Long.valueOf(service.create(request(List.of(6))).id());
    Long other = users.save(new User("다른 사용자")).getId();
    when(current.getCurrentUserId()).thenReturn(other);
    assertThat(service.list()).isEmpty();
    assertThatThrownBy(() -> service.update(id, request(List.of(6))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.create(request(List.of(6)))).isInstanceOf(ApiException.class);
    assertThat(planService.getPlans(today)).isEmpty();
  }

  @Test
  void fourAmDefinesTodayAndPlayCanOmitSubject() {
    when(current.canSaveForCurrentUser()).thenReturn(true);
    when(clock.instant()).thenReturn(Instant.parse("2026-10-17T18:59:59Z"));
    service.create(new RoutineRequest(List.of(6), "play", null, "쉬기", 20, null));
    assertThat(planService.getPlans(today)).hasSize(1);
    assertThat(plans.findByUserIdAndPlanDateOrderById(userId, today).getFirst().getSubjectId())
        .isNull();
  }

  @Test
  void expiredConsentKeepsExistingPlansReadableWithoutCreatingNewOnes() {
    service.create(request(List.of(6)));
    assertThat(planService.getPlans(today)).hasSize(1);
    service.create(request(List.of(6)));
    when(current.canSaveForCurrentUser()).thenReturn(false);
    assertThat(planService.getPlans(today)).hasSize(1);
    assertThat(applications.findByUserIdAndPlanDate(userId, today)).hasSize(1);
  }
}
