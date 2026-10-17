package com.ithopeschool.projectbe.domain.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.reflection.dto.ReflectionCurrentResponse;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 "언제 처방하고, 언제 숨기는지" 만 확인합니다.
class ReflectionServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ReflectionPrescriptionRepository prescriptionRepository =
      mock(ReflectionPrescriptionRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final StudySessionRepository studySessionRepository = mock(StudySessionRepository.class);
  private final CallRecordRepository callRecordRepository = mock(CallRecordRepository.class);
  private final ReflectionService service =
      new ReflectionService(
          currentUserProvider,
          prescriptionRepository,
          checkInRepository,
          studySessionRepository,
          callRecordRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(prescriptionRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());
    given(prescriptionRepository.save(any()))
        .willAnswer(
            invocation -> {
              ReflectionPrescription prescription = invocation.getArgument(0);
              ReflectionTestUtils.setField(prescription, "id", 3L);
              return prescription;
            });
  }

  // daysAgo 일 전의 체크인 하나 (기분 · 어제 못 채운 이유 · 약속 앞부분만)
  private static CheckIn checkIn(int daysAgo, Integer mood, MissReason missReason, String promise) {
    return new CheckIn(
        USER_ID,
        TODAY.minusDays(daysAgo),
        CheckInSource.MANUAL,
        null,
        mood,
        missReason,
        null,
        null,
        promise,
        promise == null ? null : "물 한 잔 마신다",
        null);
  }

  // 오늘 오후 3시(한국 시간)에 시작해 목표 전에 그만둔 기록 하나
  private static StudySession stoppedToday(MissReason stopReason, PlanKind kind) {
    Instant startedAt = Instant.parse("2026-10-17T06:00:00Z");
    return new StudySession(
        USER_ID,
        kind == PlanKind.STUDY ? 1L : null,
        startedAt,
        startedAt.plusSeconds(600),
        0,
        0,
        null,
        60,
        stopReason,
        kind);
  }

  private void givenCheckIns(CheckIn... checkIns) {
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                USER_ID, TODAY.minusDays(6), TODAY))
        .willReturn(List.of(checkIns));
  }

  private void givenSessions(StudySession... sessions) {
    given(studySessionRepository.findStartedBetween(eq(USER_ID), any(), any()))
        .willReturn(List.of(sessions));
  }

  private void givenLatest(LocalDate startDate, LocalDate endDate) {
    ReflectionPrescription latest =
        new ReflectionPrescription(
            USER_ID,
            startDate,
            endDate,
            MissReason.PHONE,
            4,
            ReflectionQuestions.of(MissReason.PHONE));
    ReflectionTestUtils.setField(latest, "id", 2L);
    given(prescriptionRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(latest));
  }

  @Test
  @DisplayName("최근 체크인 3일에 '하기 싫음' 이 있으면 오늘부터 5일 처방을 저장하고 돌려준다")
  void prescribesFromCheckIns() {
    // 오늘 · 어제 · 그제 체크인의 이유 = 어제 · 그제 · 사흘 전의 이유
    givenCheckIns(
        checkIn(2, 3, MissReason.UNWILLING, null),
        checkIn(1, 3, MissReason.UNWILLING, null),
        checkIn(0, 3, MissReason.UNWILLING, null));
    givenSessions();

    ReflectionCurrentResponse response = service.getCurrent();

    assertThat(response.date()).isEqualTo(TODAY);
    ReflectionCurrentResponse.Prescription prescription = response.prescription();
    assertThat(prescription.id()).isEqualTo("3");
    assertThat(prescription.startDate()).isEqualTo(TODAY);
    assertThat(prescription.endDate()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(prescription.days()).isEqualTo(5);
    assertThat(prescription.missReason()).isEqualTo("unwilling");
    assertThat(prescription.reasonDays()).isEqualTo(3);
    assertThat(prescription.reason())
        .isEqualTo("최근 일주일 동안 '하기 싫음' 때문에 못 채운 날이 3일 있었어. 5일 동안 짧게 돌아보자.");
    assertThat(prescription.questions()).isEqualTo(ReflectionQuestions.of(MissReason.UNWILLING));
    verify(prescriptionRepository).save(any());
  }

  @Test
  @DisplayName("타이머를 그만둔 이유도 같이 세고, 노는 기록의 이유는 세지 않는다")
  void countsStudyStopReasonsOnly() {
    // 체크인으로 이틀(어제 · 그제) + 오늘 공부 기록의 그만둔 이유 = 3일
    givenCheckIns(checkIn(1, 3, MissReason.PHONE, null), checkIn(0, 3, MissReason.PHONE, null));
    givenSessions(stoppedToday(MissReason.PHONE, PlanKind.STUDY));
    assertThat(service.getCurrent().prescription().missReason()).isEqualTo("phone");

    givenSessions(stoppedToday(MissReason.PHONE, PlanKind.PLAY));
    assertThat(service.getCurrent().prescription()).isNull();
  }

  @Test
  @DisplayName("진행 중인 처방이 있으면 새로 만들지 않고 그것을 돌려준다")
  void returnsOngoingPrescription() {
    givenLatest(TODAY.minusDays(2), TODAY.plusDays(2));
    givenCheckIns();
    givenSessions();

    ReflectionCurrentResponse.Prescription prescription = service.getCurrent().prescription();

    assertThat(prescription.id()).isEqualTo("2");
    assertThat(prescription.startDate()).isEqualTo(TODAY.minusDays(2));
    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  @DisplayName("지난 처방이 끝나고 7일이 안 지났으면 이유가 많아도 처방하지 않는다")
  void restsAfterLastPrescription() {
    givenLatest(TODAY.minusDays(7), TODAY.minusDays(3));
    givenCheckIns(
        checkIn(2, 3, MissReason.PHONE, null),
        checkIn(1, 3, MissReason.PHONE, null),
        checkIn(0, 3, MissReason.PHONE, null));
    givenSessions();

    assertThat(service.getCurrent().prescription()).isNull();
    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  @DisplayName("최근 7일 통화에 위기 표현이 있었으면 진행 중인 처방도 보여 주지 않는다")
  void hidesWhenRecentCallHadCrisis() {
    givenLatest(TODAY.minusDays(1), TODAY.plusDays(3));
    givenCheckIns();
    CallRecord call = new CallRecord(USER_ID, TODAY.minusDays(6), NOW, null);
    call.finishWithCrisis(NOW);
    given(
            callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
                USER_ID, TODAY.minusDays(6)))
        .willReturn(List.of(call));

    assertThat(service.getCurrent().prescription()).isNull();
    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  @DisplayName("체크인의 약속 글에 위기 표현이 있으면 처방하지 않는다")
  void hidesWhenCheckInTextHasCrisis() {
    givenCheckIns(
        checkIn(2, 3, MissReason.PHONE, null),
        checkIn(1, 3, MissReason.PHONE, null),
        checkIn(0, 3, MissReason.PHONE, "죽고 싶으면"));
    givenSessions();

    assertThat(service.getCurrent().prescription()).isNull();
    verify(prescriptionRepository, never()).save(any());
  }

  @Test
  @DisplayName("기분 1~2 가 오늘까지 6일 연속이면 처방하지 않고, 5일이면 처방한다")
  void hidesWhenMoodLowForSixDays() {
    List<CheckIn> lowSixDays = new ArrayList<>();
    for (int daysAgo = 5; daysAgo >= 0; daysAgo--) {
      lowSixDays.add(checkIn(daysAgo, 2, MissReason.TIRED, null));
    }
    givenCheckIns(lowSixDays.toArray(new CheckIn[0]));
    givenSessions();
    assertThat(service.getCurrent().prescription()).isNull();

    // 5일 전(6일 중 첫날) 하루만 기분 3 → 기분 저하가 6일 연속이 아님
    lowSixDays.set(0, checkIn(5, 3, MissReason.TIRED, null));
    givenCheckIns(lowSixDays.toArray(new CheckIn[0]));
    assertThat(service.getCurrent().prescription().missReason()).isEqualTo("tired");
  }

  @Test
  @DisplayName("기록이 없으면 처방이 없다 (prescription 이 null)")
  void noPrescriptionWithoutRecords() {
    givenCheckIns();
    givenSessions();

    ReflectionCurrentResponse response = service.getCurrent();

    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.prescription()).isNull();
  }
}
