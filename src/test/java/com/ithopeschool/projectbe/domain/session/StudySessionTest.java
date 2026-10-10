package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// study_sessions 테이블을 실제 DB(H2)에 만들고, 넣고 꺼내 보며 설계한 규칙이 지켜지는지 확인합니다.
@DataJpaTest
class StudySessionTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // FE 는 Date.now() 같은 밀리초 숫자를 보냅니다. 서버는 Instant.ofEpochMilli 로 바꿔 저장합니다.
  private static final Instant START = Instant.ofEpochMilli(1_790_000_000_000L);
  private static final Instant END = START.plusSeconds(50 * 60);

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("저장하면 번호가 생기고, DB 에서 다시 꺼내도 값이 같다")
  void saveAndFind() {
    StudySession saved = em.persistFlushFind(new StudySession(1L, 10L, START, END, 2));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getSubjectId()).isEqualTo(10L);
    assertThat(saved.getStartedAt()).isEqualTo(START);
    assertThat(saved.getEndedAt()).isEqualTo(END);
    assertThat(saved.getPauseCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("FE 가 보낸 밀리초 숫자가 저장했다 꺼내도 1밀리초도 어긋나지 않는다")
  void millisecondsSurvive() {
    long startedMillis = 1_790_000_000_123L;
    long endedMillis = 1_790_003_600_987L;

    StudySession saved =
        em.persistFlushFind(
            new StudySession(
                1L,
                10L,
                Instant.ofEpochMilli(startedMillis),
                Instant.ofEpochMilli(endedMillis),
                0));

    assertThat(saved.getStartedAt().toEpochMilli()).isEqualTo(startedMillis);
    assertThat(saved.getEndedAt().toEpochMilli()).isEqualTo(endedMillis);
  }

  @Test
  @DisplayName("일시정지를 한 번도 안 눌렀으면 0 이 저장된다")
  void zeroPauseCount() {
    StudySession saved = em.persistFlushFind(new StudySession(1L, 10L, START, END, 0));

    assertThat(saved.getPauseCount()).isZero();
  }

  @Test
  @DisplayName("자정을 넘겨 공부한 기록도 시작·끝 시각이 그대로 저장된다")
  void crossesMidnight() {
    // 한국 시간 10월 5일 23:50 에 시작해서 10월 6일 00:20 에 끝난 30분짜리 기록
    Instant start = ZonedDateTime.of(2026, 10, 5, 23, 50, 0, 0, SEOUL).toInstant();
    Instant end = ZonedDateTime.of(2026, 10, 6, 0, 20, 0, 0, SEOUL).toInstant();

    StudySession saved = em.persistFlushFind(new StudySession(1L, 10L, start, end, 1));

    assertThat(saved.getStartedAt().atZone(SEOUL).toLocalDate())
        .isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(saved.getEndedAt().atZone(SEOUL).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(saved.getEndedAt()).isEqualTo(saved.getStartedAt().plusSeconds(30 * 60));
  }

  @Test
  @DisplayName("DB 에는 시각이 서버 시간대와 상관없이 같은 순간으로 저장된다")
  void instantIsStoredAsSameMoment() {
    StudySession saved = em.persistAndFlush(new StudySession(1L, 10L, START, END, 0));

    // 자바를 거치지 않고, DB 에 들어 있는 시각을 "1970-01-01 00:00 UTC 부터 지난 초" 로 직접 읽는다
    Object storedStart =
        em.getEntityManager()
            .createNativeQuery(
                "select extract(epoch from started_at) from study_sessions where id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
    assertThat(new BigDecimal(storedStart.toString()))
        .isEqualByComparingTo(BigDecimal.valueOf(START.getEpochSecond()));
  }

  // 값 하나씩 비워 보며 "꼭 있어야 하는 값" 이 정말 필수인지 확인합니다.
  static Stream<Arguments> missingValues() {
    return Stream.of(
        Arguments.of("USER_ID", new StudySession(null, 10L, START, END, 0)),
        Arguments.of("SUBJECT_ID", new StudySession(1L, null, START, END, 0)),
        Arguments.of("STARTED_AT", new StudySession(1L, 10L, null, END, 0)),
        Arguments.of("ENDED_AT", new StudySession(1L, 10L, START, null, 0)));
  }

  @ParameterizedTest(name = "{0} 이 비어 있으면 저장되지 않는다")
  @MethodSource("missingValues")
  void requiredValues(String column, StudySession session) {
    assertThatThrownBy(() -> em.persistAndFlush(session))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining(column);
  }

  @Test
  @DisplayName("userId 와 기간으로 찾으면 그 사람의 기록만, 시작 시각 순서로 나온다")
  void findsOnlyMySessionsInPeriod() {
    Instant dayStart = ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, SEOUL).toInstant();
    Instant dayEnd = dayStart.plusSeconds(24 * 60 * 60);

    // 내 기록: 기간 시작 정각(포함) / 한낮 / 기간 끝 정각(제외) / 기간 직전(제외)
    em.persistAndFlush(
        new StudySession(
            1L, 10L, dayStart.plusSeconds(12 * 3600), dayStart.plusSeconds(13 * 3600), 0));
    em.persistAndFlush(new StudySession(1L, 10L, dayStart, dayStart.plusSeconds(600), 0));
    em.persistAndFlush(new StudySession(1L, 10L, dayEnd, dayEnd.plusSeconds(600), 0));
    em.persistAndFlush(
        new StudySession(1L, 10L, dayStart.minusSeconds(600), dayStart.minusSeconds(60), 0));
    // 남의 기록: 같은 시간대
    em.persistAndFlush(
        new StudySession(2L, 20L, dayStart.plusSeconds(3600), dayStart.plusSeconds(7200), 0));
    em.clear();

    List<StudySession> mine = findByUserIdAndPeriod(1L, dayStart, dayEnd);

    assertThat(mine)
        .extracting(StudySession::getStartedAt)
        .containsExactly(dayStart, dayStart.plusSeconds(12 * 3600));
    assertThat(mine).allMatch(session -> session.getUserId().equals(1L));
  }

  // 나중에 Repository 가 만들 쿼리와 같은 모양입니다: where user_id = ? and started_at >= ? and started_at < ?
  private List<StudySession> findByUserIdAndPeriod(Long userId, Instant from, Instant to) {
    return em.getEntityManager()
        .createQuery(
            "select s from StudySession s where s.userId = :userId and s.startedAt >= :from and s.startedAt < :to order by s.startedAt",
            StudySession.class)
        .setParameter("userId", userId)
        .setParameter("from", from)
        .setParameter("to", to)
        .getResultList();
  }
}
