package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// study_plans 테이블을 실제 DB(H2)에 만들고, 넣고 꺼내 보며 설계한 규칙이 지켜지는지 확인합니다.
@DataJpaTest
class StudyPlanTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 28);

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("저장하면 번호가 생기고, DB 에서 다시 꺼내도 값이 같다")
  void saveAndFind() {
    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, DAY, "수학 문제집 3단원 풀기", 90));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getSubjectId()).isEqualTo(10L);
    assertThat(saved.getPlanDate()).isEqualTo(DAY);
    assertThat(saved.getContent()).isEqualTo("수학 문제집 3단원 풀기");
    assertThat(saved.getTargetMinutes()).isEqualTo(90);
  }

  @Test
  @DisplayName("새 계획은 아직 안 한 상태(done = false)로 시작하고, 그대로 저장된다")
  void startsNotDone() {
    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, DAY, "영어 단어 50개", 30));

    assertThat(saved.isDone()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-01-01", "2026-12-31", "2028-02-29", "2026-10-04"})
  @DisplayName("연초·연말·윤일 같은 경계 날짜도 하루 어긋나지 않고 저장된다")
  void boundaryDates(String date) {
    LocalDate planDate = LocalDate.parse(date);

    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, planDate, "경계 날짜", 10));

    assertThat(saved.getPlanDate()).isEqualTo(planDate);
  }

  @Test
  @DisplayName("DB 에는 날짜가 2026-09-28 글자 그대로 저장된다 (서버 시간대와 상관없이)")
  void dateIsStoredAsIs() {
    StudyPlan saved = em.persistAndFlush(new StudyPlan(1L, 10L, DAY, "날짜 확인", 10));

    // 자바를 거치지 않고, DB 에 들어 있는 값을 SQL 로 직접 읽는다
    Object stored =
        em.getEntityManager()
            .createNativeQuery("select cast(plan_date as varchar) from study_plans where id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
    assertThat(stored).isEqualTo("2026-09-28");
  }

  @Test
  @DisplayName("같은 날 같은 과목에 계획을 여러 개 만들 수 있다")
  void manyPlansForSameSubjectSameDay() {
    em.persistAndFlush(new StudyPlan(1L, 10L, DAY, "수학 3단원", 60));
    em.persistAndFlush(new StudyPlan(1L, 10L, DAY, "수학 오답 노트", 30));

    assertThat(findByUserIdAndDate(1L, DAY))
        .extracting(StudyPlan::getContent)
        .containsExactly("수학 3단원", "수학 오답 노트");
  }

  @Test
  @DisplayName("할 일 내용은 200자까지 저장된다")
  void contentMaxLength() {
    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, DAY, "가".repeat(200), 10));

    assertThat(saved.getContent()).hasSize(200);
  }

  @Test
  @DisplayName("할 일 내용이 201자면 DB 가 거절한다")
  void contentTooLong() {
    StudyPlan tooLong = new StudyPlan(1L, 10L, DAY, "가".repeat(201), 10);

    assertThatThrownBy(() -> em.persistAndFlush(tooLong))
        .isInstanceOf(DataException.class)
        .hasMessageContaining("CONTENT");
  }

  @Test
  @DisplayName("한글·이모지가 섞인 내용도 깨지지 않고 저장된다")
  void unicodeContent() {
    String content = "수학 📚 오답노트 — 3단원 (p.12~15)";

    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, DAY, content, 10));

    assertThat(saved.getContent()).isEqualTo(content);
  }

  @Test
  @DisplayName("userId 가 없으면 저장되지 않는다")
  void userIdRequired() {
    StudyPlan plan = new StudyPlan(null, 10L, DAY, "내용", 10);

    assertThatThrownBy(() -> em.persistAndFlush(plan))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("USER_ID");
  }

  @Test
  @DisplayName("공부 계획(STUDY)은 subjectId 가 없으면 저장되지 않는다")
  void subjectIdRequiredForStudy() {
    StudyPlan plan = new StudyPlan(1L, null, DAY, "내용", 10);

    // 검사 규칙(ck_study_plans_study_has_subject)이 막습니다.
    assertThatThrownBy(() -> em.persistAndFlush(plan))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("CK_STUDY_PLANS_STUDY_HAS_SUBJECT");
  }

  @Test
  @DisplayName("노는 계획(PLAY)은 subjectId 없이 저장된다")
  void playWithoutSubject() {
    StudyPlan saved =
        em.persistFlushFind(new StudyPlan(1L, null, DAY, "친구랑 농구", 60, null, PlanKind.PLAY));

    assertThat(saved.getSubjectId()).isNull();
    assertThat(saved.getKind()).isEqualTo(PlanKind.PLAY);
  }

  @Test
  @DisplayName("짧은 생성자로 만든 계획은 공부 계획 · 시간 미정 · 루틴 아님으로 저장된다")
  void shortConstructorDefaults() {
    StudyPlan saved = em.persistFlushFind(new StudyPlan(1L, 10L, DAY, "수학", 30));

    assertThat(saved.getKind()).isEqualTo(PlanKind.STUDY);
    assertThat(saved.getStartTime()).isNull();
    assertThat(saved.getRoutineId()).isNull();
  }

  @Test
  @DisplayName("시작 시각은 몇 시 몇 분 그대로 저장된다")
  void startTimeIsStored() {
    StudyPlan saved =
        em.persistFlushFind(
            new StudyPlan(1L, 10L, DAY, "수학", 30, LocalTime.of(19, 0), PlanKind.STUDY));

    assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(19, 0));
  }

  @Test
  @DisplayName("종류(kind)가 없으면 저장되지 않는다")
  void kindRequired() {
    StudyPlan plan = new StudyPlan(1L, 10L, DAY, "내용", 10, null, null);

    assertThatThrownBy(() -> em.persistAndFlush(plan))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("KIND");
  }

  @Test
  @DisplayName("날짜가 없으면 저장되지 않는다")
  void planDateRequired() {
    StudyPlan plan = new StudyPlan(1L, 10L, null, "내용", 10);

    assertThatThrownBy(() -> em.persistAndFlush(plan))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("PLAN_DATE");
  }

  @Test
  @DisplayName("할 일 내용이 없으면(null) 저장되지 않는다")
  void contentRequired() {
    StudyPlan plan = new StudyPlan(1L, 10L, DAY, null, 10);

    assertThatThrownBy(() -> em.persistAndFlush(plan))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("CONTENT");
  }

  @Test
  @DisplayName("userId 와 날짜로 찾으면 그 사람의 그날 계획만 나온다")
  void findsOnlyMyPlansOfTheDay() {
    LocalDate nextDay = DAY.plusDays(1);
    em.persistAndFlush(new StudyPlan(1L, 10L, DAY, "내 오늘 계획", 60));
    em.persistAndFlush(new StudyPlan(1L, 10L, nextDay, "내 내일 계획", 60));
    em.persistAndFlush(new StudyPlan(2L, 20L, DAY, "남의 오늘 계획", 60));
    em.clear();

    assertThat(findByUserIdAndDate(1L, DAY))
        .extracting(StudyPlan::getContent)
        .containsExactly("내 오늘 계획");
    assertThat(findByUserIdAndDate(1L, nextDay))
        .extracting(StudyPlan::getContent)
        .containsExactly("내 내일 계획");
    assertThat(findByUserIdAndDate(2L, DAY))
        .extracting(StudyPlan::getContent)
        .containsExactly("남의 오늘 계획");
    // 계획이 없는 날은 빈 목록
    assertThat(findByUserIdAndDate(1L, DAY.minusDays(1))).isEmpty();
  }

  // 나중에 Repository 가 만들 쿼리와 같은 모양입니다: where user_id = ? and plan_date = ?
  private List<StudyPlan> findByUserIdAndDate(Long userId, LocalDate date) {
    return em.getEntityManager()
        .createQuery(
            "select p from StudyPlan p where p.userId = :userId and p.planDate = :date order by p.id",
            StudyPlan.class)
        .setParameter("userId", userId)
        .setParameter("date", date)
        .getResultList();
  }
}
