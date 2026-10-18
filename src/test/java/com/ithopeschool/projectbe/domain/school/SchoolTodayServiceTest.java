package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.school.dto.SchoolTodayResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: NEIS · DB 를 가짜로 바꿔서 급식을 언제 새로 받는지와 응답 모양을 봅니다.
class SchoolTodayServiceTest {

  private static final Long USER_ID = 7L;
  private static final String SCHOOL = "7530560";
  // 2026-10-19 (월) 12:00 한국 시간
  private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final SchoolEventRepository eventRepository = mock(SchoolEventRepository.class);
  private final SchoolEventFetchRepository fetchRepository = mock(SchoolEventFetchRepository.class);
  private final NeisClient neisClient = mock(NeisClient.class);
  // 시간이 흐르는 것을 흉내 내려고 시계도 가짜로 둡니다.
  private final Clock clock = mock(Clock.class);
  private final SchoolTodayService service =
      new SchoolTodayService(
          currentUserProvider,
          accountRepository,
          eventRepository,
          fetchRepository,
          neisClient,
          clock);

  private void hasSchool(String schoolCode) {
    Account account = mock(Account.class);
    given(account.getSchoolCode()).willReturn(schoolCode);
    given(account.getSchoolName()).willReturn("한국디지털미디어고등학교");
    given(accountRepository.findByUserId(USER_ID)).willReturn(Optional.of(account));
  }

  // 학사일정을 받을 때 교육청 코드를 저장해 둔 학교
  private void hasSavedOfficeCode() {
    given(fetchRepository.findById(SCHOOL))
        .willReturn(Optional.of(new SchoolEventFetch(SCHOOL, "J10", NOW)));
  }

  private static NeisClient.NeisMeal lunch(LocalDate day, String dish) {
    return new NeisClient.NeisMeal(day, "중식", List.of("흑미밥", dish));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(clock.instant()).willReturn(NOW);
    given(
            eventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
                anyString(), any(), any()))
        .willReturn(List.of());
  }

  @Test
  @DisplayName("학교를 안 골랐으면 school: null, 빈 목록이고 NEIS 를 부르지 않는다")
  void noSchool() {
    hasSchool(null);

    assertThat(service.getMyToday(null, null)).isEqualTo(SchoolTodayResponse.noSchool(TODAY));
    verifyNoInteractions(neisClient);
  }

  @Test
  @DisplayName("예약 작업용 급식 미리 받기: 학교 번호만으로 오늘 · 내일 급식을 받아 두고, 6시간 안에는 다시 부르지 않는다")
  void prefetchesMeals() {
    hasSavedOfficeCode();
    given(neisClient.fetchMeals("J10", SCHOOL, TODAY))
        .willReturn(Optional.of(List.of(lunch(TODAY, "나주곰탕"))));
    given(neisClient.fetchMeals("J10", SCHOOL, TOMORROW)).willReturn(Optional.of(List.of()));

    service.prefetchMeals(SCHOOL);
    service.prefetchMeals(SCHOOL);

    verify(neisClient, times(1)).fetchMeals("J10", SCHOOL, TODAY);
    verify(neisClient, times(1)).fetchMeals("J10", SCHOOL, TOMORROW);
  }

  @Test
  @DisplayName("교육청 코드를 못 찾는 학교는 급식을 미리 받지 않는다")
  void prefetchWithoutOfficeCode() {
    given(fetchRepository.findById(SCHOOL)).willReturn(Optional.empty());
    given(neisClient.findOfficeCode(SCHOOL)).willReturn(Optional.empty());

    service.prefetchMeals(SCHOOL);

    verify(neisClient, never()).fetchMeals(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("오늘 · 내일 급식과 30일 안의 학사일정을 남은 날(dDay) · 시험 표시와 함께 돌려준다")
  void mealsAndEvents() {
    hasSchool(SCHOOL);
    hasSavedOfficeCode();
    given(neisClient.fetchMeals("J10", SCHOOL, TODAY))
        .willReturn(Optional.of(List.of(lunch(TODAY, "나주곰탕"))));
    given(neisClient.fetchMeals("J10", SCHOOL, TOMORROW))
        .willReturn(Optional.of(List.of(lunch(TOMORROW, "카레"))));
    given(
            eventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
                SCHOOL, TODAY, TODAY.plusDays(30)))
        .willReturn(
            List.of(
                new SchoolEvent(SCHOOL, LocalDate.of(2026, 11, 2), "2학기 2차 정기시험", null, "1,2")));

    SchoolTodayResponse today = service.getMyToday(null, null);

    assertThat(today.school().name()).isEqualTo("한국디지털미디어고등학교");
    assertThat(today.meals())
        .containsExactly(
            new SchoolTodayResponse.Meal(TODAY, "중식", List.of("흑미밥", "나주곰탕")),
            new SchoolTodayResponse.Meal(TOMORROW, "중식", List.of("흑미밥", "카레")));
    assertThat(today.events())
        .containsExactly(
            new SchoolTodayResponse.Event(
                LocalDate.of(2026, 11, 2), "2학기 2차 정기시험", List.of(1, 2), true, 14));
    // 저장해 둔 교육청 코드를 써서 NEIS 에 다시 묻지 않습니다.
    verify(neisClient, never()).findOfficeCode(anyString());
  }

  @Test
  @DisplayName("6시간 안에는 메모리의 급식을 쓰고, 지나면 NEIS 에서 새로 받는다")
  void mealsAreCachedForSixHours() {
    hasSchool(SCHOOL);
    hasSavedOfficeCode();
    given(neisClient.fetchMeals("J10", SCHOOL, TODAY))
        .willReturn(Optional.of(List.of(lunch(TODAY, "나주곰탕"))));
    given(neisClient.fetchMeals("J10", SCHOOL, TOMORROW)).willReturn(Optional.of(List.of()));

    service.getMyToday(null, null);
    given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(5)));
    service.getMyToday(null, null);
    verify(neisClient, times(1)).fetchMeals("J10", SCHOOL, TODAY);

    given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(7)));
    service.getMyToday(null, null);
    verify(neisClient, times(2)).fetchMeals("J10", SCHOOL, TODAY);
  }

  @Test
  @DisplayName("NEIS 가 멈추면 받아 둔 급식을 그대로 쓴다")
  void keepsSavedMealsWhenNeisFails() {
    hasSchool(SCHOOL);
    hasSavedOfficeCode();
    given(neisClient.fetchMeals("J10", SCHOOL, TODAY))
        .willReturn(Optional.of(List.of(lunch(TODAY, "나주곰탕"))))
        .willReturn(Optional.empty());
    given(neisClient.fetchMeals("J10", SCHOOL, TOMORROW)).willReturn(Optional.empty());

    service.getMyToday(null, null);
    given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(7)));
    SchoolTodayResponse today = service.getMyToday(null, null);

    assertThat(today.meals())
        .containsExactly(new SchoolTodayResponse.Meal(TODAY, "중식", List.of("흑미밥", "나주곰탕")));
  }

  @Test
  @DisplayName("교육청 코드를 저장해 둔 적이 없으면 NEIS 에 한 번만 묻고, 못 찾으면 급식 없이 학사일정만 준다")
  void findsOfficeCodeOnce() {
    hasSchool(SCHOOL);
    given(fetchRepository.findById(SCHOOL)).willReturn(Optional.empty());
    given(neisClient.findOfficeCode(SCHOOL)).willReturn(Optional.of("J10"));
    given(neisClient.fetchMeals(anyString(), anyString(), any()))
        .willReturn(Optional.of(List.of()));

    service.getMyToday(null, null);
    service.getMyToday(null, null);
    verify(neisClient, times(1)).findOfficeCode(SCHOOL);

    // 다른 학교 번호인데 교육청 코드를 못 찾는 경우
    hasSchool("0000000");
    given(fetchRepository.findById("0000000")).willReturn(Optional.empty());
    given(neisClient.findOfficeCode("0000000")).willReturn(Optional.empty());
    assertThat(service.getMyToday(null, null).meals()).isEmpty();
    verify(neisClient, never()).fetchMeals(anyString(), eq("0000000"), any());
  }

  @Test
  @DisplayName("학년 · 반을 주면 오늘 그 반 시간표를 넣고, 안 주면 시간표는 null 이다")
  void timetableOnlyWithClass() {
    hasSchool(SCHOOL);
    hasSavedOfficeCode();
    given(neisClient.fetchMeals(anyString(), anyString(), any()))
        .willReturn(Optional.of(List.of()));
    given(neisClient.hasApiKey()).willReturn(true);
    given(neisClient.fetchTimetable("J10", SCHOOL, 2, 1, TODAY))
        .willReturn(
            Optional.of(
                List.of(new NeisClient.NeisPeriod(1, "미적분Ⅰ"), new NeisClient.NeisPeriod(2, "문학"))));

    SchoolTodayResponse withClass = service.getMyToday(2, 1);
    assertThat(withClass.timetable())
        .containsExactly(
            new SchoolTodayResponse.Period(1, "미적분Ⅰ"), new SchoolTodayResponse.Period(2, "문학"));
    assertThat(withClass.timetablePartial()).isFalse();

    assertThat(service.getMyToday(null, null).timetable()).isNull();
  }

  @Test
  @DisplayName("키가 없는데 5줄이 꽉 차서 오면 뒤 교시가 잘렸을 수 있다고 알린다")
  void timetablePartialWithoutKey() {
    hasSchool(SCHOOL);
    hasSavedOfficeCode();
    given(neisClient.fetchMeals(anyString(), anyString(), any()))
        .willReturn(Optional.of(List.of()));
    given(neisClient.hasApiKey()).willReturn(false);
    given(neisClient.fetchTimetable("J10", SCHOOL, 2, 1, TODAY))
        .willReturn(
            Optional.of(
                List.of(
                    new NeisClient.NeisPeriod(1, "미적분Ⅰ"),
                    new NeisClient.NeisPeriod(2, "마케팅과 광고"),
                    new NeisClient.NeisPeriod(3, "문학"),
                    new NeisClient.NeisPeriod(4, "회계 정보 처리 시스템"),
                    new NeisClient.NeisPeriod(5, "스포츠 생활1"))));

    assertThat(service.getMyToday(2, 1).timetablePartial()).isTrue();
  }

  @Test
  @DisplayName("학년 · 반 중 하나만 주거나 범위를 넘으면 400(VALIDATION_ERROR) 이고 NEIS 를 부르지 않는다")
  void rejectsBadClass() {
    hasSchool(SCHOOL);

    assertThatThrownBy(() -> service.getMyToday(2, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.getMyToday(null, 1)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.getMyToday(4, 1)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.getMyToday(2, 31)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.getMyToday(0, 0)).isInstanceOf(ApiException.class);
    verifyNoInteractions(neisClient);
  }

  @Test
  @DisplayName("대본 줄: 시간표 · 오늘 급식 · 우리 학년 학사일정(3개까지)을 쓰고, 학교 이름은 넣지 않는다")
  void scriptLinesForChat() {
    SchoolTodayResponse today =
        SchoolTodayResponse.of(
            TODAY,
            SCHOOL,
            "한국디지털미디어고등학교",
            List.of(
                new SchoolTodayResponse.Period(1, "미적분Ⅰ"), new SchoolTodayResponse.Period(2, "문학")),
            true,
            List.of(
                new SchoolTodayResponse.Meal(TODAY, "중식", List.of("흑미밥", "나주곰탕")),
                new SchoolTodayResponse.Meal(TOMORROW, "중식", List.of("카레"))),
            List.of(
                new SchoolTodayResponse.Event(TODAY, "체육대회", List.of(), false, 0),
                new SchoolTodayResponse.Event(
                    LocalDate.of(2026, 11, 2), "3학년 모의평가", List.of(3), true, 14),
                new SchoolTodayResponse.Event(
                    LocalDate.of(2026, 11, 2), "2학기 2차 정기시험", List.of(1, 2), true, 14)));

    List<String> lines = SchoolTodayService.linesOf(today, 2);

    assertThat(lines)
        .containsExactly(
            "- 오늘 시간표: 1교시 미적분Ⅰ, 2교시 문학 (5교시까지만 알아)",
            "- 오늘 중식: 흑미밥, 나주곰탕",
            "- 다가오는 학사일정: 10월 19일 체육대회(오늘) / 11월 2일 2학기 2차 정기시험(시험, 14일 남음)");
    assertThat(String.join("\n", lines)).doesNotContain("한국디지털미디어고등학교");
    // 학교를 안 골랐으면 빈 목록
    assertThat(SchoolTodayService.linesOf(SchoolTodayResponse.noSchool(TODAY), null)).isEmpty();
  }
}
