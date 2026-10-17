package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.StudyTotal.SubjectTotal;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import com.ithopeschool.projectbe.domain.summary.DaySummary.DaySession;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// "오늘 순공시간 · 과목별 합계" 계산(이슈 #14 "바뀐 점")을 확인합니다.
//   순공 = 끝 − 시작 − 일시정지한 시간, 노는 기록(PLAY)은 넣지 않는다, 분은 마지막에 한 번만 반올림한다.
// 하루 안에 시작한 기록만 골라 넘기는 일은 Service 가 하고, 여기서는 넘겨받은 기록의 계산만 봅니다.
class StudyTotalTest {

  private static final long MINUTE = 60_000;
  private static final long SECOND = 1_000;

  // 한국 시간 2026년 9월 28일 10:00 (기록들의 기준 시각)
  private static final Instant BASE =
      ZonedDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();

  // 기준 시각에서 startMinutes 분 뒤에 시작해서 durationMillis 동안 한 기록을 만듭니다.
  private static StudySession session(
      Long subjectId, PlanKind kind, int startMinutes, long durationMillis, long pausedMs) {
    Instant start = BASE.plusSeconds(startMinutes * 60L);
    return new StudySession(
        1L,
        subjectId,
        start,
        start.plusMillis(durationMillis),
        0,
        pausedMs,
        null,
        null,
        null,
        kind);
  }

  private static StudySession study(
      Long subjectId, int startMinutes, long durationMillis, long pausedMs) {
    return session(subjectId, PlanKind.STUDY, startMinutes, durationMillis, pausedMs);
  }

  @Test
  @DisplayName("기록이 없으면 0분이고 과목 목록도 비어 있다")
  void empty() {
    StudyTotal total = StudyTotal.of(List.of());

    assertThat(total.studyMinutes()).isZero();
    assertThat(total.subjects()).isEmpty();
  }

  @Test
  @DisplayName("일시정지한 시간을 뺀다: 19:00 ~ 20:10, 40분 멈춤 → 30분 (FE 예시)")
  void pausedTimeIsRemoved() {
    StudyTotal total = StudyTotal.of(List.of(study(3L, 0, 70 * MINUTE, 40 * MINUTE)));

    assertThat(total.studyMinutes()).isEqualTo(30);
    assertThat(total.subjects()).containsExactly(new SubjectTotal(3L, 30));
  }

  @Test
  @DisplayName("과목별로 더하고, 과목 번호가 작은 순서로 돌려준다")
  void sumsBySubject() {
    // 일부러 과목 번호가 섞인 순서로 넣습니다: 영어(4) 30분 → 수학(3) 50분 → 수학(3) 20분
    StudyTotal total =
        StudyTotal.of(
            List.of(
                study(4L, 0, 30 * MINUTE, 0),
                study(3L, 60, 50 * MINUTE, 0),
                study(3L, 120, 20 * MINUTE, 0)));

    assertThat(total.studyMinutes()).isEqualTo(100);
    assertThat(total.subjects())
        .containsExactly(new SubjectTotal(3L, 70), new SubjectTotal(4L, 30));
  }

  @Test
  @DisplayName("노는 기록은 전체와 과목별 어디에도 넣지 않는다")
  void playIsExcluded() {
    StudyTotal total =
        StudyTotal.of(
            List.of(
                study(3L, 0, 60 * MINUTE, 0),
                // 노는 기록은 과목이 없습니다
                session(null, PlanKind.PLAY, 90, 30 * MINUTE, 0)));

    assertThat(total.studyMinutes()).isEqualTo(60);
    assertThat(total.subjects()).containsExactly(new SubjectTotal(3L, 60));
  }

  @Test
  @DisplayName("노는 기록만 있으면 0분이다")
  void onlyPlay() {
    StudyTotal total = StudyTotal.of(List.of(session(null, PlanKind.PLAY, 0, 45 * MINUTE, 0)));

    assertThat(total.studyMinutes()).isZero();
    assertThat(total.subjects()).isEmpty();
  }

  @Test
  @DisplayName("분은 모두 더한 뒤 마지막에 한 번만 반올림한다 (20분 24초 × 3 = 61분)")
  void roundsOnlyOnce() {
    long twentyMinutes24Seconds = 20 * MINUTE + 24 * SECOND;

    StudyTotal total =
        StudyTotal.of(
            List.of(
                study(1L, 0, twentyMinutes24Seconds, 0),
                study(2L, 60, twentyMinutes24Seconds, 0),
                study(3L, 120, twentyMinutes24Seconds, 0)));

    // 전체: 61.2분 → 61. 기록마다 반올림해서 더하면 20 + 20 + 20 = 60 이 되어 틀립니다.
    assertThat(total.studyMinutes()).isEqualTo(61);
    // 과목별: 각각 20.4분 → 20. 그래서 과목별을 더한 값(60)이 전체(61)와 1분 다를 수 있습니다.
    assertThat(total.subjects())
        .containsExactly(
            new SubjectTotal(1L, 20), new SubjectTotal(2L, 20), new SubjectTotal(3L, 20));
  }

  @Test
  @DisplayName("같은 과목의 기록은 밀리초로 더한 뒤 그 과목에서 한 번만 반올림한다")
  void roundsSubjectOnlyOnce() {
    // 수학 10분 20초 + 10분 20초 = 20분 40초 → 21분 (각각 반올림하면 10 + 10 = 20 이라 틀림)
    StudyTotal total =
        StudyTotal.of(
            List.of(
                study(3L, 0, 10 * MINUTE + 20 * SECOND, 0),
                study(3L, 60, 10 * MINUTE + 20 * SECOND, 0)));

    assertThat(total.studyMinutes()).isEqualTo(21);
    assertThat(total.subjects()).containsExactly(new SubjectTotal(3L, 21));
  }

  @Test
  @DisplayName("이상한 일시정지 시간(음수·기록보다 긺)은 빼지 않는다")
  void invalidPausedMsIsIgnored() {
    StudyTotal total =
        StudyTotal.of(
            List.of(
                study(3L, 0, 50 * MINUTE, -5 * MINUTE), study(3L, 60, 50 * MINUTE, 90 * MINUTE)));

    assertThat(total.studyMinutes()).isEqualTo(100);
  }

  @Test
  @DisplayName("과목이 비어 있는 공부 기록은 전체 시간에만 넣고 과목별 목록에는 넣지 않는다")
  void studyWithoutSubjectOnlyCountsInTotal() {
    // DB 검사 규칙 때문에 실제로는 생기지 않지만, 생겨도 서버가 죽지 않아야 합니다.
    StudyTotal total =
        StudyTotal.of(List.of(study(null, 0, 40 * MINUTE, 0), study(3L, 60, 20 * MINUTE, 0)));

    assertThat(total.studyMinutes()).isEqualTo(60);
    assertThat(total.subjects()).containsExactly(new SubjectTotal(3L, 20));
  }

  @Test
  @DisplayName("같은 기록으로 하루 요약표(DaySummary)를 계산해도 같은 순공시간이 나온다")
  void sameAsDaySummary() {
    // 일시정지·노는 기록·반올림이 모두 섞인 하루
    List<StudySession> sessions = new ArrayList<>();
    sessions.add(study(3L, 0, 70 * MINUTE, 40 * MINUTE));
    sessions.add(study(4L, 90, 20 * MINUTE + 24 * SECOND, 0));
    sessions.add(study(3L, 130, 20 * MINUTE + 24 * SECOND, 5 * MINUTE));
    sessions.add(session(null, PlanKind.PLAY, 180, 60 * MINUTE, 0));

    List<DaySession> daySessions = new ArrayList<>();
    for (StudySession s : sessions) {
      daySessions.add(
          new DaySession(
              s.getStartedAt(), s.getEndedAt(), s.getPausedMs(), s.getPauseCount(), s.getKind()));
    }
    DaySummary summary = DaySummary.of(DayRule.dateOf(BASE), List.of(), daySessions);

    assertThat(StudyTotal.of(sessions).studyMinutes()).isEqualTo(summary.studyMinutes());
  }
}
