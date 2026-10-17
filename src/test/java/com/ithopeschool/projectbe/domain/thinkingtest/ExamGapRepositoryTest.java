package com.ithopeschool.projectbe.domain.thinkingtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 시험 전 2주를 꺼낼 때 쓰는 "기간" 쿼리 두 개를 실제 DB(H2)에서 확인합니다.
// 두 날짜가 모두 들어가는지, 다른 사람의 것은 안 나오는지, 순서가 맞는지 봅니다.
@DataJpaTest
class ExamGapRepositoryTest {

  private static final Long ME = 1L;
  private static final Long OTHER = 2L;
  private static final LocalDate FROM = LocalDate.of(2026, 10, 6);
  private static final LocalDate TO = LocalDate.of(2026, 10, 19);

  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private CallRecordRepository callRecordRepository;

  private StudyPlan plan(Long userId, LocalDate date, String content) {
    return studyPlanRepository.saveAndFlush(new StudyPlan(userId, 1L, date, content, 60));
  }

  private CallRecord call(Long userId, LocalDate date, String startedAt) {
    return callRecordRepository.saveAndFlush(
        new CallRecord(userId, date, Instant.parse(startedAt), null));
  }

  @Test
  @DisplayName("계획: from ~ to 의 내 계획만 날짜 순서로 (첫날·마지막 날 포함)")
  void plansBetween() {
    plan(ME, TO, "마지막 날");
    plan(ME, FROM, "첫날");
    plan(ME, FROM.minusDays(1), "기간 전");
    plan(ME, TO.plusDays(1), "기간 뒤");
    plan(OTHER, FROM, "남의 계획");

    assertThat(
            studyPlanRepository.findByUserIdAndPlanDateBetweenOrderByPlanDateAscIdAsc(ME, FROM, TO))
        .extracting(StudyPlan::getContent)
        .containsExactly("첫날", "마지막 날");
  }

  @Test
  @DisplayName("통화: from ~ to 의 내 통화만 시작 순서로 (첫날·마지막 날 포함)")
  void callsBetween() {
    Long last = call(ME, TO, "2026-10-19T10:00:00Z").getId();
    Long first = call(ME, FROM, "2026-10-06T10:00:00Z").getId();
    call(ME, FROM.minusDays(1), "2026-10-05T10:00:00Z");
    call(ME, TO.plusDays(1), "2026-10-20T10:00:00Z");
    call(OTHER, FROM, "2026-10-06T11:00:00Z");

    assertThat(
            callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
                ME, FROM, TO))
        .extracting(CallRecord::getId)
        .containsExactly(first, last);
  }
}
