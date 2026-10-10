package com.ithopeschool.projectbe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.user.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 테이블 4개를 한꺼번에 쓰는 "진짜 같은 상황" 테스트입니다.
// 두 사용자가 일부러 같은 과목 이름·같은 날짜를 써서, 데이터가 섞이지 않는지 확인합니다.
@DataJpaTest
class TableScenarioTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

  @Autowired private TestEntityManager em;
  @Autowired private EntityManager entityManager;

  private Long aliceId;
  private Long bobId;
  private Long aliceMath;
  private Long aliceEnglish;
  private Long bobMath;

  @BeforeEach
  void prepareTwoStudents() {
    aliceId = em.persist(new User("앨리스")).getId();
    bobId = em.persist(new User("밥")).getId();

    // 두 사람 모두 "수학" 이라는 같은 이름의 과목을 가진다 (사용자가 다르면 허용)
    aliceMath = em.persist(new Subject(aliceId, "수학", SubjectColor.RED)).getId();
    aliceEnglish = em.persist(new Subject(aliceId, "영어", SubjectColor.BLUE)).getId();
    bobMath = em.persist(new Subject(bobId, "수학", SubjectColor.GREEN)).getId();

    // 같은 날짜에 각자 계획을 세운다
    em.persist(new StudyPlan(aliceId, aliceMath, TODAY, "앨리스 수학 3단원", 60));
    em.persist(new StudyPlan(aliceId, aliceEnglish, TODAY, "앨리스 영어 단어", 30));
    em.persist(new StudyPlan(aliceId, aliceMath, TODAY.plusDays(1), "앨리스 내일 수학", 60));
    em.persist(new StudyPlan(bobId, bobMath, TODAY, "밥 수학 문제집", 120));

    // 앨리스는 자정을 넘겨 공부했다 (23:50 ~ 00:20), 밥은 낮에 공부했다
    em.persist(session(aliceId, aliceMath, at(TODAY, 23, 50), at(TODAY.plusDays(1), 0, 20), 1));
    em.persist(session(aliceId, aliceEnglish, at(TODAY, 15, 0), at(TODAY, 15, 40), 0));
    em.persist(session(bobId, bobMath, at(TODAY, 15, 0), at(TODAY, 17, 0), 3));

    em.flush();
    em.clear();
  }

  @Test
  @DisplayName("같은 이름·같은 날짜여도 userId 로 찾으면 내 것만 나온다")
  void eachUserSeesOnlyOwnRows() {
    assertThat(subjectNames(aliceId)).containsExactly("수학", "영어");
    assertThat(subjectNames(bobId)).containsExactly("수학");

    assertThat(planContents(aliceId, TODAY)).containsExactly("앨리스 수학 3단원", "앨리스 영어 단어");
    assertThat(planContents(bobId, TODAY)).containsExactly("밥 수학 문제집");

    assertThat(count("StudySession", aliceId)).isEqualTo(2);
    assertThat(count("StudySession", bobId)).isEqualTo(1);
  }

  @Test
  @DisplayName("과목별 공부 횟수를 과목 이름으로 묶어 셀 수 있다 (표 사이가 이어진다)")
  void sessionsCanBeGroupedBySubjectName() {
    List<Object[]> rows =
        entityManager
            .createQuery(
                "select s.name, count(ss) from StudySession ss"
                    + " join Subject s on s.id = ss.subjectId"
                    + " where ss.userId = :userId group by s.name order by s.name",
                Object[].class)
            .setParameter("userId", aliceId)
            .getResultList();

    assertThat(rows).extracting(row -> row[0] + ":" + row[1]).containsExactly("수학:1", "영어:1");
  }

  @Test
  @DisplayName("자정을 넘긴 기록은 한국 시간 기준으로 시작한 날(10/5)에 잡히고, 끝난 날은 다음 날이다")
  void midnightSessionBelongsToStartDay() {
    // 한국 시간 하루(10/5 00:00 ~ 10/6 00:00) 안에서 시작한 앨리스의 기록
    Instant dayStart = TODAY.atStartOfDay(SEOUL).toInstant();
    Instant dayEnd = TODAY.plusDays(1).atStartOfDay(SEOUL).toInstant();

    List<StudySession> sessions =
        entityManager
            .createQuery(
                "select s from StudySession s where s.userId = :userId"
                    + " and s.startedAt >= :from and s.startedAt < :to order by s.startedAt",
                StudySession.class)
            .setParameter("userId", aliceId)
            .setParameter("from", dayStart)
            .setParameter("to", dayEnd)
            .getResultList();

    assertThat(sessions).hasSize(2);
    StudySession midnight = sessions.get(1);
    assertThat(midnight.getEndedAt().atZone(SEOUL).toLocalDate()).isEqualTo(TODAY.plusDays(1));
  }

  // ---- 정합도 검사: "남의 과목" 이 붙은 줄을 찾는 쿼리 ----

  @Test
  @DisplayName("정상 데이터에는 남의 과목이 붙은 계획·기록이 하나도 없다")
  void noPlanOrSessionPointsToSomeoneElsesSubject() {
    assertThat(plansWithForeignSubject()).isEmpty();
    assertThat(sessionsWithForeignSubject()).isEmpty();
  }

  @Test
  @DisplayName("DB 는 외래키가 없어서 남의 과목도 받아 주지만, 정합도 쿼리로 그 줄을 찾아낼 수 있다")
  void foreignSubjectIsDetectable() {
    // 일부러 앨리스의 계획·기록에 밥의 과목을 붙인다 (API 에서 막아야 하는 실수)
    em.persistAndFlush(new StudyPlan(aliceId, bobMath, TODAY, "잘못 붙인 계획", 10));
    em.persistAndFlush(session(aliceId, bobMath, at(TODAY, 9, 0), at(TODAY, 9, 30), 0));
    em.clear();

    assertThat(plansWithForeignSubject())
        .extracting(StudyPlan::getContent)
        .containsExactly("잘못 붙인 계획");
    assertThat(sessionsWithForeignSubject()).hasSize(1);
  }

  @Test
  @DisplayName("존재하지 않는 과목 번호가 붙은 줄도 정합도 쿼리로 찾아낼 수 있다")
  void missingSubjectIsDetectable() {
    em.persistAndFlush(new StudyPlan(aliceId, 987_654L, TODAY, "없는 과목 계획", 10));
    em.clear();

    assertThat(plansWithForeignSubject())
        .extracting(StudyPlan::getContent)
        .containsExactly("없는 과목 계획");
  }

  // ---- 회원 탈퇴(#69) 에 필요한 것: userId 만으로 한 사람의 데이터를 모두 지울 수 있는가 ----

  @Test
  @DisplayName("userId 로 한 사람의 데이터만 모든 테이블에서 지워지고, 다른 사람의 데이터는 그대로다")
  void deleteOneUsersDataLeavesOthersUntouched() {
    // 자식(기록·계획·과목)을 먼저 지우고 마지막에 사용자를 지운다
    entityManager
        .createQuery("delete from StudySession s where s.userId = :id")
        .setParameter("id", aliceId)
        .executeUpdate();
    entityManager
        .createQuery("delete from StudyPlan p where p.userId = :id")
        .setParameter("id", aliceId)
        .executeUpdate();
    entityManager
        .createQuery("delete from Subject s where s.userId = :id")
        .setParameter("id", aliceId)
        .executeUpdate();
    entityManager
        .createQuery("delete from User u where u.id = :id")
        .setParameter("id", aliceId)
        .executeUpdate();
    entityManager.clear();

    assertThat(count("StudySession", aliceId)).isZero();
    assertThat(count("StudyPlan", aliceId)).isZero();
    assertThat(count("Subject", aliceId)).isZero();
    assertThat(entityManager.find(User.class, aliceId)).isNull();

    // 밥의 데이터는 그대로
    assertThat(entityManager.find(User.class, bobId)).isNotNull();
    assertThat(count("Subject", bobId)).isEqualTo(1);
    assertThat(count("StudyPlan", bobId)).isEqualTo(1);
    assertThat(count("StudySession", bobId)).isEqualTo(1);
  }

  // ---- 도우미 ----

  private static StudySession session(
      Long userId, Long subjectId, Instant start, Instant end, int pauseCount) {
    return new StudySession(userId, subjectId, start, end, pauseCount);
  }

  // 한국 시간으로 날짜와 시·분을 적으면 Instant 로 바꿔 준다
  private static Instant at(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(
            date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, minute, 0, 0, SEOUL)
        .toInstant();
  }

  private List<String> subjectNames(Long userId) {
    return entityManager
        .createQuery(
            "select s.name from Subject s where s.userId = :userId order by s.id", String.class)
        .setParameter("userId", userId)
        .getResultList();
  }

  private List<String> planContents(Long userId, LocalDate date) {
    return entityManager
        .createQuery(
            "select p.content from StudyPlan p where p.userId = :userId and p.planDate = :date order by p.id",
            String.class)
        .setParameter("userId", userId)
        .setParameter("date", date)
        .getResultList();
  }

  private long count(String entityName, Long userId) {
    return entityManager
        .createQuery(
            "select count(e) from " + entityName + " e where e.userId = :userId", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
  }

  // 계획의 과목이 "없거나, 계획 주인의 과목이 아닌" 줄
  private List<StudyPlan> plansWithForeignSubject() {
    return entityManager
        .createQuery(
            "select p from StudyPlan p where not exists"
                + " (select 1 from Subject s where s.id = p.subjectId and s.userId = p.userId)",
            StudyPlan.class)
        .getResultList();
  }

  // 기록의 과목이 "없거나, 기록 주인의 과목이 아닌" 줄
  private List<StudySession> sessionsWithForeignSubject() {
    return entityManager
        .createQuery(
            "select ss from StudySession ss where not exists"
                + " (select 1 from Subject s where s.id = ss.subjectId and s.userId = ss.userId)",
            StudySession.class)
        .getResultList();
  }
}
