package com.ithopeschool.projectbe.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardday.CardDayMark;
import com.ithopeschool.projectbe.domain.cardday.CardDayMarkRepository;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: 판정 · 카드 · 표시 · 체크인을 가짜로 두고, 날마다 모으는 규칙만 확인합니다.
class CalendarServiceTest {

  private static final Long ME = 7L;
  // 지금은 2026-10-18 정오(한국 시간)입니다. 오늘 날짜는 2026-10-18 입니다.
  private static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CardResultService cardResultService = mock(CardResultService.class);
  private final CardRepository cardRepository = mock(CardRepository.class);
  private final CardDayMarkRepository cardDayMarkRepository = mock(CardDayMarkRepository.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final CalendarService service =
      new CalendarService(
          currentUserProvider,
          cardResultService,
          cardRepository,
          cardDayMarkRepository,
          checkInRepository,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void loggedIn() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  private static JudgedCardDay day(
      long cardId, LocalDate date, CardResult result, MissReason cause) {
    return new JudgedCardDay(
        cardId, date, true, CardPriority.FLEXIBLE, result, 0, cause, null, null);
  }

  private static Card card(long id, String title) {
    Card card = mock(Card.class);
    given(card.getId()).willReturn(id);
    given(card.getTitle()).willReturn(title);
    return card;
  }

  @Test
  @DisplayName("10월의 1일 ~ 31일이 빠짐없이 있고, 판정은 달과 최근 4주를 합친 기간으로 한 번만 묻는다")
  void everyDayOfTheMonth() {
    CalendarResponse response = service.getMonth(OCTOBER);

    assertThat(response.month()).isEqualTo("2026-10");
    assertThat(response.days()).hasSize(31);
    assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(response.days().get(30).date()).isEqualTo(LocalDate.of(2026, 10, 31));
    // 최근 4주는 9-21 ~ 10-18 이라, 9-21 ~ 10-31 을 한 번에 묻습니다.
    verify(cardResultService)
        .getJudgedDays(ME, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 31));
    // 못 한 카드가 없으면 카드 제목은 묻지 않습니다.
    verify(cardRepository, never()).findByUserIdAndIdIn(any(), any());
  }

  @Test
  @DisplayName("하루에 실행 · 지연 · 미실행 · 휴식을 같은 무게로 세고, 못 한 카드에는 제목 · 결과 · 고른 원인 · 미룬 사유를 붙인다")
  void countsAndMisses() {
    LocalDate oct5 = LocalDate.of(2026, 10, 5);
    given(cardResultService.getJudgedDays(any(), any(), any()))
        .willReturn(
            List.of(
                day(1, oct5, CardResult.DONE, null),
                day(2, oct5, CardResult.ENDED_EARLY, null),
                day(3, oct5, CardResult.LATE, null),
                day(4, oct5, CardResult.FAILED, MissReason.TIRED),
                day(5, oct5, CardResult.POSTPONED, null),
                day(6, oct5, CardResult.REST, null),
                // 9월 날은 달력에 넣지 않습니다. (최근 4주 실행률에만 씁니다)
                day(1, LocalDate.of(2026, 9, 30), CardResult.FAILED, null)));
    List<Card> cards = List.of(card(4, "밤 수학 오답 정리"), card(5, "영어 듣기"));
    given(cardRepository.findByUserIdAndIdIn(any(), any())).willReturn(cards);
    CardDayMark postponed = mock(CardDayMark.class);
    given(postponed.getCardId()).willReturn(5L);
    given(postponed.getMarkDate()).willReturn(oct5);
    given(postponed.getReason()).willReturn("학원 보강이 생겨서 내일 아침으로 옮겨요");
    given(
            cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of(postponed));

    CalendarResponse.Day day = service.getMonth(OCTOBER).days().get(4);

    assertThat(day.date()).isEqualTo(oct5);
    assertThat(List.of(day.done(), day.late(), day.missed(), day.rest()))
        .containsExactly(2, 1, 2, 1);
    assertThat(day.misses())
        .containsExactly(
            new CalendarResponse.Miss("4", "밤 수학 오답 정리", "failed", "피곤·수면", null),
            new CalendarResponse.Miss("5", "영어 듣기", "postponed", null, "학원 보강이 생겨서 내일 아침으로 옮겨요"));
  }

  @Test
  @DisplayName("감정 태그는 그날 체크인에서 가져오고(정해진 순서), 체크인을 안 한 날은 빈 목록이다")
  void emotions() {
    CheckIn checkIn = mock(CheckIn.class);
    given(checkIn.getCheckInDate()).willReturn(LocalDate.of(2026, 10, 3));
    given(checkIn.getEmotions()).willReturn(Set.of(EmotionTag.TIRED, EmotionTag.STUCK));
    given(
            checkInRepository.findWithEmotionsBetween(
                ME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .willReturn(List.of(checkIn));

    List<CalendarResponse.Day> days = service.getMonth(OCTOBER).days();

    assertThat(days.get(2).emotions()).containsExactly("STUCK", "TIRED");
    assertThat(days.get(3).emotions()).isEmpty();
  }

  @Test
  @DisplayName("최근 4주 실행률은 오늘 포함 28일, 휴식을 빼고 (실행 + 지연) ÷ 나머지. 셀 카드가 없으면 null")
  void recentRate() {
    List<JudgedCardDay> judged =
        List.of(
            day(1, TODAY, CardResult.DONE, null),
            day(1, TODAY.minusDays(1), CardResult.LATE, null),
            day(1, TODAY.minusDays(2), CardResult.FAILED, null),
            day(1, TODAY.minusDays(3), CardResult.REST, null),
            // 28일보다 전이라 빼고, 오늘보다 뒤라 뺍니다.
            day(1, TODAY.minusDays(28), CardResult.FAILED, null),
            day(1, TODAY.plusDays(1), CardResult.FAILED, null));

    CalendarResponse.Recent recent = CalendarService.recentOf(judged, TODAY.minusDays(27), TODAY);

    assertThat(recent).isEqualTo(new CalendarResponse.Recent(TODAY.minusDays(27), TODAY, 2, 3, 67));
    assertThat(CalendarService.recentOf(List.of(), TODAY.minusDays(27), TODAY).rate()).isNull();
  }
}
