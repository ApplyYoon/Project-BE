package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 설문 답을 실제 DB(H2)에 넣고 꺼내 봅니다. (딸린 테이블 두 개까지)
@DataJpaTest
class OnboardingAnswerRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  @Autowired private OnboardingAnswerRepository onboardingAnswerRepository;
  @Autowired private EntityManager entityManager;

  // 꼭 있어야 하는 네 칸만 채운 설문
  private static OnboardingAnswer.Values required() {
    OnboardingAnswer.Values values = new OnboardingAnswer.Values();
    values.grade = Grade.HIGH_2;
    values.targetExam = "수능";
    values.targetExamDate = LocalDate.of(2027, 11, 18);
    values.dailyAvailableMinutes = 180;
    return values;
  }

  private OnboardingAnswer save(Long userId, Instant answeredAt, OnboardingAnswer.Values values) {
    OnboardingAnswer saved =
        onboardingAnswerRepository.saveAndFlush(new OnboardingAnswer(userId, answeredAt, values));
    // 1차 캐시를 비워서, 다음 조회가 메모리가 아니라 DB 에서 다시 읽게 합니다.
    entityManager.clear();
    return saved;
  }

  @Test
  @DisplayName("모든 칸과 딸린 테이블(무너진 때 · 과목별 수준)이 그대로 저장되고 꺼내진다")
  void savesEveryColumn() {
    OnboardingAnswer.Values values = required();
    values.targetUniversity = "한국대";
    values.targetScore = "1등급";
    values.wakeUpTime = LocalTime.of(7, 0);
    values.sleepTime = LocalTime.of(23, 30);
    values.schoolEndTime = LocalTime.of(16, 30);
    values.academyEndTime = LocalTime.of(22, 0);
    values.studyPlace = StudyPlace.LIBRARY;
    values.chronotype = Chronotype.NIGHT;
    values.maxFocusMinutes = 50;
    values.biggestDistraction = Distraction.PHONE;
    values.motivationReason = "부모님께 효도";
    values.goalReward = "여행 가기";
    values.pastFailureCauses = EnumSet.of(FailureCause.WEEKEND, FailureCause.PHONE);
    values.subjectLevels =
        List.of(new SubjectLevel(3L, 2, null, 4), new SubjectLevel(5L, null, 85, 3));
    Long id = save(1L, NOW, values).getId();

    OnboardingAnswer found = onboardingAnswerRepository.findById(id).orElseThrow();

    assertThat(found.getGrade()).isEqualTo(Grade.HIGH_2);
    assertThat(found.getTargetExamDate()).isEqualTo(LocalDate.of(2027, 11, 18));
    assertThat(found.getSleepTime()).isEqualTo(LocalTime.of(23, 30));
    assertThat(found.getStudyPlace()).isEqualTo(StudyPlace.LIBRARY);
    assertThat(found.getBiggestDistraction()).isEqualTo(Distraction.PHONE);
    assertThat(found.getMotivationReason()).isEqualTo("부모님께 효도");
    assertThat(found.getPastFailureCauses())
        .containsExactlyInAnyOrder(FailureCause.WEEKEND, FailureCause.PHONE);
    assertThat(found.getSubjectLevels())
        .extracting(
            l ->
                l.getSubjectId()
                    + ":"
                    + l.getGrade()
                    + ":"
                    + l.getScore()
                    + ":"
                    + l.getConfidence())
        .containsExactlyInAnyOrder("3:2:null:4", "5:null:85:3");
  }

  @Test
  @DisplayName("꼭 있어야 하는 네 칸만 있어도 저장되고, 나머지는 비어 있다")
  void savesOnlyRequired() {
    Long id = save(1L, NOW, required()).getId();

    OnboardingAnswer found = onboardingAnswerRepository.findById(id).orElseThrow();

    assertThat(found.getChronotype()).isNull();
    assertThat(found.getWakeUpTime()).isNull();
    assertThat(found.getPastFailureCauses()).isEmpty();
    assertThat(found.getSubjectLevels()).isEmpty();
  }

  @Test
  @DisplayName("고치면 새 줄이 쌓이고, 지금 것은 답한 시각이 가장 늦은 내 줄이다 (남의 줄은 섞이지 않음)")
  void latestIsMyNewest() {
    OnboardingAnswer first = save(1L, NOW, required());
    OnboardingAnswer second = save(1L, NOW.plusSeconds(60), required());
    save(2L, NOW.plusSeconds(120), required());

    assertThat(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(1L))
        .get()
        .extracting(OnboardingAnswer::getId)
        .isEqualTo(second.getId());
    assertThat(onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(1L))
        .extracting(OnboardingAnswer::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(3L))
        .isEmpty();
  }

  @Test
  @DisplayName("같은 시각에 두 줄이 생기면 번호가 큰 쪽이 나중 것이다")
  void sameTimeUsesId() {
    save(1L, NOW, required());
    OnboardingAnswer second = save(1L, NOW, required());

    assertThat(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(1L))
        .get()
        .extracting(OnboardingAnswer::getId)
        .isEqualTo(second.getId());
  }

  @Test
  @DisplayName("일부만 고칠 때 쓰는 copyValues 는 모든 칸과 목록을 옮겨 적고, 옮긴 목록을 바꿔도 원래 줄은 그대로다")
  void copyValuesCopiesEverything() {
    OnboardingAnswer.Values values = required();
    values.chronotype = Chronotype.MORNING;
    values.pastFailureCauses = EnumSet.of(FailureCause.WEEKEND);
    values.subjectLevels = List.of(new SubjectLevel(3L, 2, null, 4));
    Long id = save(1L, NOW, values).getId();
    OnboardingAnswer found = onboardingAnswerRepository.findById(id).orElseThrow();

    OnboardingAnswer.Values copy = found.copyValues();
    copy.pastFailureCauses.add(FailureCause.PHONE);
    copy.subjectLevels.clear();

    assertThat(copy.chronotype).isEqualTo(Chronotype.MORNING);
    assertThat(copy.dailyAvailableMinutes).isEqualTo(180);
    assertThat(found.getPastFailureCauses()).containsExactly(FailureCause.WEEKEND);
    assertThat(found.getSubjectLevels()).hasSize(1);
  }
}
