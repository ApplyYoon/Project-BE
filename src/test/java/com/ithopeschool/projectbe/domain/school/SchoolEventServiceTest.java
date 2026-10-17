package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.school.dto.SchoolEventsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

// Service 단위 테스트: NEIS · DB 를 가짜로 바꿔서 "언제 NEIS 를 부르는지" 와 저장 · 응답 모양을 봅니다.
class SchoolEventServiceTest {

  private static final Long USER_ID = 7L;
  private static final String SCHOOL = "7530560";
  // 2026-10-17 12:00 (한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final SchoolEventRepository eventRepository = mock(SchoolEventRepository.class);
  private final SchoolEventFetchRepository fetchRepository = mock(SchoolEventFetchRepository.class);
  private final NeisClient neisClient = mock(NeisClient.class);
  private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private final SchoolEventService service =
      new SchoolEventService(
          currentUserProvider,
          accountRepository,
          eventRepository,
          fetchRepository,
          neisClient,
          transactionTemplate,
          CLOCK);

  private void hasSchool(String schoolCode) {
    Account account = mock(Account.class);
    given(account.getSchoolCode()).willReturn(schoolCode);
    given(account.getSchoolName()).willReturn("한국디지털미디어고등학교");
    given(accountRepository.findByUserId(USER_ID)).willReturn(Optional.of(account));
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    // 트랜잭션 틀은 안의 일을 그대로 실행만 합니다.
    doAnswer(
            invocation -> {
              ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Test
  @DisplayName("학교를 안 골랐으면 school: null, 빈 목록이고 NEIS 를 부르지 않는다")
  void noSchool() {
    hasSchool(null);

    assertThat(service.getMyEvents(null, null)).isEqualTo(SchoolEventsResponse.noSchool());
    verifyNoInteractions(neisClient);
  }

  @Test
  @DisplayName("받은 지 24시간이 안 됐으면 NEIS 를 부르지 않고, 저장된 일정에 시험 표시를 붙여 돌려준다")
  void freshUsesSaved() {
    hasSchool(SCHOOL);
    Instant fetchedAt = NOW.minus(Duration.ofHours(3));
    given(fetchRepository.findById(SCHOOL))
        .willReturn(Optional.of(new SchoolEventFetch(SCHOOL, "J10", fetchedAt)));
    given(
            eventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
                SCHOOL, TODAY, TODAY.plusDays(120)))
        .willReturn(
            List.of(
                new SchoolEvent(SCHOOL, LocalDate.of(2026, 10, 20), "전국연합학력평가", null, ""),
                new SchoolEvent(SCHOOL, LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", "기말", "1,2"),
                new SchoolEvent(SCHOOL, LocalDate.of(2026, 12, 31), "동계방학식", null, "")));

    SchoolEventsResponse response = service.getMyEvents(null, null);

    assertThat(response.school())
        .isEqualTo(new SchoolEventsResponse.School(SCHOOL, "한국디지털미디어고등학교"));
    assertThat(response.fetchedAt()).isEqualTo(fetchedAt.toEpochMilli());
    assertThat(response.events())
        .containsExactly(
            new SchoolEventsResponse.Event(
                LocalDate.of(2026, 10, 20), "전국연합학력평가", null, List.of(), true),
            new SchoolEventsResponse.Event(
                LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", "기말", List.of(1, 2), true),
            new SchoolEventsResponse.Event(
                LocalDate.of(2026, 12, 31), "동계방학식", null, List.of(), false));
    verifyNoInteractions(neisClient);
  }

  @Test
  @DisplayName("처음이면 교육청 코드를 찾고, 키가 있으면 이번 학년도 전체를 받아 통째로 바꿔 넣는다 (토요휴업일 · 같은 줄은 뺌)")
  void firstFetchReplaces() {
    hasSchool(SCHOOL);
    given(fetchRepository.findById(SCHOOL)).willReturn(Optional.empty());
    given(neisClient.findOfficeCode(SCHOOL)).willReturn(Optional.of("J10"));
    given(neisClient.hasApiKey()).willReturn(true);
    given(
            neisClient.fetchSchedule(
                "J10", SCHOOL, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28)))
        .willReturn(
            Optional.of(
                List.of(
                    new NeisClient.NeisEvent(LocalDate.of(2026, 10, 24), "토요휴업일", null, List.of()),
                    new NeisClient.NeisEvent(
                        LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", null, List.of(1, 2)),
                    new NeisClient.NeisEvent(
                        LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", null, List.of(1, 2)))));

    service.getMyEvents(null, null);

    verify(eventRepository)
        .deleteRange(SCHOOL, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SchoolEvent>> saved = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).hasSize(1);
    assertThat(saved.getValue().getFirst().getName()).isEqualTo("2학기 2차 정기시험");
    assertThat(saved.getValue().getFirst().getGrades()).isEqualTo("1,2");
    ArgumentCaptor<SchoolEventFetch> fetch = ArgumentCaptor.forClass(SchoolEventFetch.class);
    verify(fetchRepository).save(fetch.capture());
    assertThat(fetch.getValue().getOfficeCode()).isEqualTo("J10");
    assertThat(fetch.getValue().getFetchedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("키가 없으면 오늘부터 120일만 받는다")
  void withoutKeyFetchesShortRange() {
    hasSchool(SCHOOL);
    given(fetchRepository.findById(SCHOOL))
        .willReturn(
            Optional.of(new SchoolEventFetch(SCHOOL, "J10", NOW.minus(Duration.ofDays(2)))));
    given(neisClient.hasApiKey()).willReturn(false);
    given(neisClient.fetchSchedule("J10", SCHOOL, TODAY, TODAY.plusDays(120)))
        .willReturn(Optional.of(List.of()));

    service.getMyEvents(null, null);

    verify(eventRepository).deleteRange(SCHOOL, TODAY, TODAY.plusDays(120));
    verify(neisClient, never()).findOfficeCode(anyString());
  }

  @Test
  @DisplayName("NEIS 가 멈추면 받아 둔 일정을 지우지 않고 그대로 돌려준다")
  void neisFailureKeepsSaved() {
    hasSchool(SCHOOL);
    Instant old = NOW.minus(Duration.ofDays(3));
    given(fetchRepository.findById(SCHOOL))
        .willReturn(Optional.of(new SchoolEventFetch(SCHOOL, "J10", old)));
    given(neisClient.hasApiKey()).willReturn(true);
    given(neisClient.fetchSchedule(any(), any(), any(), any())).willReturn(Optional.empty());

    SchoolEventsResponse response = service.getMyEvents(null, null);

    assertThat(response.fetchedAt()).isEqualTo(old.toEpochMilli());
    verify(eventRepository, never()).deleteRange(any(), any(), any());
  }

  @Test
  @DisplayName("[새로고침] 은 받은 지 10분이 지났을 때만 NEIS 를 부른다")
  void refreshAfterTenMinutes() {
    hasSchool(SCHOOL);
    given(fetchRepository.findById(SCHOOL))
        .willReturn(
            Optional.of(new SchoolEventFetch(SCHOOL, "J10", NOW.minus(Duration.ofMinutes(5)))));
    service.refreshMyEvents();
    verifyNoInteractions(neisClient);

    given(fetchRepository.findById(SCHOOL))
        .willReturn(
            Optional.of(new SchoolEventFetch(SCHOOL, "J10", NOW.minus(Duration.ofMinutes(11)))));
    given(neisClient.fetchSchedule(any(), any(), any(), any())).willReturn(Optional.of(List.of()));
    service.refreshMyEvents();
    verify(neisClient).fetchSchedule(any(), any(), any(), any());
  }

  @Test
  @DisplayName("학년도 첫날 · 시험 판정 · 학년 글자")
  void helpers() {
    assertThat(SchoolEventService.schoolYearStart(LocalDate.of(2026, 10, 17)))
        .isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(SchoolEventService.schoolYearStart(LocalDate.of(2027, 2, 10)))
        .isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(SchoolEventService.isExam("1학기 1차 정기시험")).isTrue();
    assertThat(SchoolEventService.isExam("3학년 대학수학능력 6월 모의평가")).isTrue();
    assertThat(SchoolEventService.isExam("3학년 직업기초능력평가")).isFalse();
    assertThat(SchoolEventService.isExam("하계방학식")).isFalse();
    assertThat(SchoolEventService.joinGrades(List.of(1, 2))).isEqualTo("1,2");
    assertThat(SchoolEventService.gradesOf("1,2")).containsExactly(1, 2);
    assertThat(SchoolEventService.gradesOf("")).isEmpty();
  }
}
