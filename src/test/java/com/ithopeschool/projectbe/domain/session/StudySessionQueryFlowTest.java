package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse.SubjectTotalResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 진짜 DB(H2)에 기록을 넣고, "자정을 넘겨 공부한 기록이 오늘로 잡히는지" 를 확인합니다. (이슈 #14 완료 조건)
// Service · Repository · DayRule(하루의 기준)이 실제로 맞물려 돌고, 시계와 "지금 요청한 사람" 만 고정합니다.
// 한국 시간 새벽 4시에 하루가 바뀝니다. (docs/API.md 2-4)
//
//   10월 5일 23:00 ~ 23:40   → 10월 5일
//   10월 6일 00:10 ~ 00:40   → 10월 5일  (달력으로는 6일이지만 새벽 4시 전이라 전날)
//   10월 6일 03:50 ~ 04:20   → 10월 5일  (4시를 걸쳐도 시작한 날의 기록 하나)
//   10월 6일 04:00 에 시작    → 10월 6일  (딱 4시는 새 하루)
@DataJpaTest
class StudySessionQueryFlowTest {

  private static final Long ME = 1L;
  private static final Long OTHER = 2L;
  private static final long MINUTE = 60_000;

  @Autowired private StudySessionRepository studySessionRepository;
  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private CardRepository cardRepository;
  @Autowired private TestEntityManager em;

  private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  // 시계가 now 를 가리키는 Service 입니다. ("오늘" 을 정하려고)
  private StudySessionService serviceAt(Instant now) {
    return new StudySessionService(
        currentUserProvider,
        studySessionRepository,
        subjectRepository,
        studyPlanRepository,
        cardRepository,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  // 한국 시간 2026년 10월의 순간입니다.
  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  private static LocalDate october(int day) {
    return LocalDate.of(2026, 10, day);
  }

  // 공부 기록을 DB 에 넣습니다. (저장 규칙 검사는 건너뛰고 DB 에 바로 넣습니다. 겹침 검사는 StudySessionServiceTest 몫)
  private StudySession saveStudy(Long userId, Long subjectId, Instant start, int minutes) {
    return em.persistAndFlush(
        new StudySession(userId, subjectId, start, start.plusSeconds(minutes * 60L), 0));
  }

  private StudySession saveSession(
      Long userId,
      Long subjectId,
      Instant start,
      int minutes,
      long pausedMs,
      Long planId,
      PlanKind kind) {
    return em.persistAndFlush(
        new StudySession(
            userId,
            subjectId,
            start,
            start.plusSeconds(minutes * 60L),
            0,
            pausedMs,
            planId,
            null,
            null,
            kind));
  }

  @Test
  @DisplayName("자정을 넘겨 공부한 기록도 새벽 4시 전이면 전날(오늘)로 센다: 지금이 10월 6일 01:30")
  void recordsAcrossMidnightCountAsToday() {
    // 어제(10월 4일)로 세는 기록들 — 오늘 합계에 들어가면 안 됩니다.
    saveStudy(ME, 1L, kst(4, 23, 30), 60); // 10월 4일 23:30 ~ 10월 5일 00:30
    saveStudy(ME, 1L, kst(5, 3, 30), 60); // 10월 5일 03:30 ~ 04:30 (4시 전에 시작 → 10월 4일)
    // 오늘(10월 5일)로 세는 기록들
    saveStudy(ME, 1L, kst(5, 4, 30), 50); // 수학 50분
    saveStudy(ME, 2L, kst(5, 23, 0), 40); // 영어 40분
    saveStudy(ME, 1L, kst(6, 0, 10), 30); // 수학 30분 — 달력으로는 10월 6일 00:10
    // 오늘이 아닌 것들
    saveSession(ME, null, kst(5, 20, 0), 60, 0, null, PlanKind.PLAY); // 노는 기록 60분 (순공 아님)
    saveStudy(OTHER, 1L, kst(5, 12, 0), 90); // 남의 기록

    StudyTotalResponse result = serviceAt(kst(6, 1, 30)).getTodayTotal();

    assertThat(result.date()).isEqualTo(october(5));
    assertThat(result.studyMinutes()).isEqualTo(120);
    assertThat(result.subjects())
        .containsExactly(new SubjectTotalResponse("1", 80), new SubjectTotalResponse("2", 40));
  }

  @Test
  @DisplayName("딱 04:00 에 시작한 기록은 새 하루의 기록이고, 03:59 에 시작한 기록은 전날이다")
  void fourOClockIsTheBoundary() {
    saveStudy(ME, 1L, kst(6, 3, 59), 20); // 10월 5일로 셈
    saveStudy(ME, 1L, kst(6, 4, 0), 30); // 10월 6일로 셈 (딱 4시)
    saveStudy(ME, 1L, kst(6, 11, 0), 30); // 10월 6일

    StudyTotalResponse result = serviceAt(kst(6, 12, 0)).getTodayTotal();

    assertThat(result.date()).isEqualTo(october(6));
    assertThat(result.studyMinutes()).isEqualTo(60);
  }

  @Test
  @DisplayName("지금이 03:59:59 면 아직 어제 기록을 세고, 04:00:00 이 되면 0분에서 다시 시작한다")
  void todayResetsAtFourOClock() {
    saveStudy(ME, 1L, kst(5, 22, 0), 60); // 10월 5일 22:00 ~ 23:00

    StudyTotalResponse before = serviceAt(kst(6, 3, 59).plusSeconds(59)).getTodayTotal();
    StudyTotalResponse after = serviceAt(kst(6, 4, 0)).getTodayTotal();

    assertThat(before.date()).isEqualTo(october(5));
    assertThat(before.studyMinutes()).isEqualTo(60);
    assertThat(after.date()).isEqualTo(october(6));
    assertThat(after.studyMinutes()).isZero();
    assertThat(after.subjects()).isEmpty();
  }

  @Test
  @DisplayName("일시정지한 시간은 오늘 순공에서 빠진다: 19:00 ~ 20:10, 40분 멈춤 → 30분")
  void pausedTimeIsNotCounted() {
    saveSession(ME, 1L, kst(5, 19, 0), 70, 40 * MINUTE, null, PlanKind.STUDY);

    StudyTotalResponse result = serviceAt(kst(5, 23, 0)).getTodayTotal();

    assertThat(result.studyMinutes()).isEqualTo(30);
    assertThat(result.subjects()).containsExactly(new SubjectTotalResponse("1", 30));
  }

  @Test
  @DisplayName("기록이 하나도 없는 사람의 오늘은 0분이다")
  void nothingToday() {
    StudyTotalResponse result = serviceAt(kst(5, 12, 0)).getTodayTotal();

    assertThat(result.studyMinutes()).isZero();
    assertThat(result.subjects()).isEmpty();
  }

  @Test
  @DisplayName("기간 목록도 같은 하루의 기준이다: 10월 5일만 물으면 자정을 넘긴 10월 6일 00:10 기록이 들어 있다")
  void periodListFollowsDayRule() {
    saveStudy(ME, 1L, kst(4, 23, 30), 60); // 10월 4일로 셈
    saveStudy(ME, 1L, kst(5, 4, 30), 50); // 10월 5일
    saveStudy(ME, 1L, kst(6, 0, 10), 30); // 10월 5일로 셈 (달력으로는 6일)
    saveStudy(ME, 1L, kst(6, 3, 50), 30); // 10월 5일로 셈 (4시를 걸침)
    saveStudy(ME, 1L, kst(6, 4, 0), 30); // 10월 6일
    saveStudy(OTHER, 1L, kst(5, 12, 0), 90); // 남의 기록

    List<StudySessionResponse> fifth = serviceAt(kst(7, 12, 0)).getSessions(october(5), october(5));
    List<StudySessionResponse> sixth = serviceAt(kst(7, 12, 0)).getSessions(october(6), october(6));

    assertThat(fifth)
        .extracting(StudySessionResponse::startedAt)
        .containsExactly(
            kst(5, 4, 30).toEpochMilli(),
            kst(6, 0, 10).toEpochMilli(),
            kst(6, 3, 50).toEpochMilli());
    assertThat(sixth)
        .extracting(StudySessionResponse::startedAt)
        .containsExactly(kst(6, 4, 0).toEpochMilli());
  }

  @Test
  @DisplayName("기간 목록은 시작 시각 순이고, 노는 기록도 kind 로 구별해서 담고, 일시정지·계획·목표·이유도 담는다")
  void periodListContentAndOrder() {
    // 일부러 늦은 기록을 먼저 저장합니다.
    saveSession(ME, null, kst(5, 21, 0), 30, 0, null, PlanKind.PLAY);
    em.persistAndFlush(
        new StudySession(
            ME,
            1L,
            kst(5, 9, 0),
            kst(5, 9, 40),
            3,
            5 * MINUTE,
            77L,
            50,
            MissReason.PHONE,
            PlanKind.STUDY));

    List<StudySessionResponse> result =
        serviceAt(kst(6, 12, 0)).getSessions(october(5), october(5));

    assertThat(result).hasSize(2);
    StudySessionResponse study = result.get(0);
    assertThat(study.kind()).isEqualTo("study");
    assertThat(study.subjectId()).isEqualTo("1");
    assertThat(study.pauseCount()).isEqualTo(3);
    assertThat(study.pausedMs()).isEqualTo(5 * MINUTE);
    assertThat(study.planId()).isEqualTo("77");
    assertThat(study.targetMinutes()).isEqualTo(50);
    assertThat(study.stopReason()).isEqualTo("phone");
    StudySessionResponse play = result.get(1);
    assertThat(play.kind()).isEqualTo("play");
    assertThat(play.subjectId()).isEmpty();
  }

  @Test
  @DisplayName("기간 목록은 내 기록만 돌려준다")
  void periodListIsMineOnly() {
    saveStudy(OTHER, 1L, kst(5, 12, 0), 90);

    assertThat(serviceAt(kst(6, 12, 0)).getSessions(october(5), october(5))).isEmpty();
  }

  @Test
  @DisplayName("계획별 실제 시간: 그 계획으로 켠 기록들의 순공 합이고, 다른 날 한 기록·계획 없는 기록·남의 기록은 세지 않는다")
  void planActualsFromDatabase() {
    StudyPlan math = studyPlanRepository.save(new StudyPlan(ME, 1L, october(5), "수학", 50));
    StudyPlan play =
        studyPlanRepository.save(
            new StudyPlan(ME, null, october(5), "게임", 60, null, PlanKind.PLAY));
    Long mathId = math.getId();
    Long playId = play.getId();

    saveSession(ME, 1L, kst(5, 9, 0), 20, 0, mathId, PlanKind.STUDY); // 20분
    saveSession(
        ME, 1L, kst(5, 11, 0), 40, 10 * MINUTE, mathId, PlanKind.STUDY); // 40분 중 10분 멈춤 → 30분
    // 계획을 하루 늦게 한 기록도 그 계획의 기록입니다 (날짜는 가리지 않음)
    saveSession(ME, 1L, kst(7, 9, 0), 5, 0, mathId, PlanKind.STUDY); // 5분
    saveSession(ME, null, kst(5, 21, 0), 95, 0, playId, PlanKind.PLAY); // 노는 계획으로 95분
    saveSession(ME, 1L, kst(5, 14, 0), 60, 0, null, PlanKind.STUDY); // 계획 없이 켠 기록 (세지 않음)
    saveSession(OTHER, 1L, kst(5, 15, 0), 60, 0, mathId, PlanKind.STUDY); // 남의 기록 (세지 않음)
    em.clear();

    List<PlanActualResponse> result = serviceAt(kst(8, 12, 0)).getPlanActuals(october(5));

    assertThat(result)
        .containsExactly(
            // 수학: 20 + 30 + 5 = 55분 / 목표 50분 → 채움
            new PlanActualResponse(String.valueOf(mathId), 55, 100, true, 0),
            // 게임: 95분 / 목표 60분 → 채움 (노는 계획도 실제 시간이 나옴)
            new PlanActualResponse(String.valueOf(playId), 95, 100, true, 0));
  }
}
