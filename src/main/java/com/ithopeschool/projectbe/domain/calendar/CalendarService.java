package com.ithopeschool.projectbe.domain.calendar;

import com.ithopeschool.projectbe.domain.calendar.dto.CalendarResponse;
import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardday.CardDayMark;
import com.ithopeschool.projectbe.domain.cardday.CardDayMarkRepository;
import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 달력 로직입니다. 한 달의 날마다 카드 결과 개수 · 감정 · 못 한 사유와, 최근 4주 실행률을 돌려줍니다. (이슈 #235)
//
// 정해 둔 규칙
//   1. 카드 결과는 판정(CardResultService.getJudgedDays)을 그대로 씁니다. 아직 판정 전인 카드(오늘 아직 시간 전)는 세지 않습니다.
//   2. 네 가지 개수는 같은 무게로 셉니다. 실행만 크게 보이면 "잘한 날만 기억" 하게 되기 때문입니다. (기획서 4.6)
//        done   = 실행 · 중도 종료 · 사후 입력   late = 지연 실행
//        missed = 미루기 · 실패 · 미처리          rest = 휴식
//   3. 최근 4주 실행률 = (done + late) ÷ (휴식을 뺀 카드 수). 오늘 포함 28일이고, 셀 카드가 없으면 null 입니다.
//   4. 쿼리는 날 수와 상관없이 정해진 몇 번입니다. 판정 한 번(달과 최근 4주를 합친 기간) · 카드 제목 한 번 ·
//      미루기/실패 표시 한 번 · 체크인과 감정 한 번(join fetch). 하루마다 묻지 않습니다. (완료 조건 "N+1 없음")
//
// 모든 조회는 currentUserProvider.getCurrentUserId() 로 얻은 내 번호로만 합니다. 로그인하지 않으면 401 입니다.
@Service
@RequiredArgsConstructor
public class CalendarService {

  // 최근 실행률을 보는 기간 (오늘 포함, 4주)
  static final int RECENT_DAYS = 28;

  private final CurrentUserProvider currentUserProvider;
  private final CardResultService cardResultService;
  private final CardRepository cardRepository;
  private final CardDayMarkRepository cardDayMarkRepository;
  private final CheckInRepository checkInRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public CalendarResponse getMonth(YearMonth month) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate monthStart = month.atDay(1);
    LocalDate monthEnd = month.atEndOfMonth();
    LocalDate recentFrom = today.minusDays(RECENT_DAYS - 1);

    // 1) 판정: 달과 최근 4주를 합친 기간을 한 번에 (규칙 4)
    LocalDate from = monthStart.isBefore(recentFrom) ? monthStart : recentFrom;
    LocalDate to = monthEnd.isAfter(today) ? monthEnd : today;
    List<JudgedCardDay> judged = cardResultService.getJudgedDays(userId, from, to);

    // 2) 그 달에 못 한 카드의 제목과, 미루거나 실패로 기록할 때 적은 사유
    Map<Long, String> titles = new HashMap<>();
    List<Long> missedCardIds =
        judged.stream()
            .filter(day -> inMonth(day.date(), monthStart, monthEnd) && day.result().isMiss())
            .map(JudgedCardDay::cardId)
            .distinct()
            .toList();
    if (!missedCardIds.isEmpty()) {
      for (Card card : cardRepository.findByUserIdAndIdIn(userId, missedCardIds)) {
        titles.put(card.getId(), card.getTitle());
      }
    }
    Map<String, String> notes = new HashMap<>();
    for (CardDayMark mark :
        cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
            userId, monthStart, monthEnd)) {
      if (mark.getReason() != null && !mark.getReason().isBlank()) {
        notes.put(keyOf(mark.getCardId(), mark.getMarkDate()), mark.getReason());
      }
    }

    // 3) 체크인 감정 (한 번에)
    Map<LocalDate, List<String>> emotions = new HashMap<>();
    for (CheckIn checkIn :
        checkInRepository.findWithEmotionsBetween(userId, monthStart, monthEnd)) {
      List<String> tags = new ArrayList<>();
      for (EmotionTag tag : EmotionTag.values()) {
        if (checkIn.getEmotions().contains(tag)) {
          tags.add(tag.name());
        }
      }
      emotions.put(checkIn.getCheckInDate(), tags);
    }

    // 4) 날마다 모으기
    Map<LocalDate, DayCount> counts = new HashMap<>();
    for (JudgedCardDay day : judged) {
      if (!inMonth(day.date(), monthStart, monthEnd)) {
        continue;
      }
      DayCount count = counts.computeIfAbsent(day.date(), date -> new DayCount());
      count.add(day, titles, notes);
    }
    List<CalendarResponse.Day> days = new ArrayList<>();
    for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
      DayCount count = counts.getOrDefault(date, new DayCount());
      days.add(
          new CalendarResponse.Day(
              date,
              count.done,
              count.late,
              count.missed,
              count.rest,
              emotions.getOrDefault(date, List.of()),
              count.misses));
    }

    return new CalendarResponse(month.toString(), days, recentOf(judged, recentFrom, today));
  }

  // 최근 4주 실행률 (규칙 3)
  static CalendarResponse.Recent recentOf(
      List<JudgedCardDay> judged, LocalDate recentFrom, LocalDate today) {
    int done = 0;
    int total = 0;
    for (JudgedCardDay day : judged) {
      if (day.date().isBefore(recentFrom)
          || day.date().isAfter(today)
          || day.result() == CardResult.REST) {
        continue;
      }
      total++;
      if (!day.result().isMiss()) {
        done++;
      }
    }
    Integer rate = total == 0 ? null : Math.round(done * 100f / total);
    return new CalendarResponse.Recent(recentFrom, today, done, total, rate);
  }

  private static boolean inMonth(LocalDate date, LocalDate monthStart, LocalDate monthEnd) {
    return !date.isBefore(monthStart) && !date.isAfter(monthEnd);
  }

  private static String keyOf(Long cardId, LocalDate date) {
    return cardId + "@" + date;
  }

  // 하루치 개수와 못 한 카드 목록 (규칙 2)
  private static final class DayCount {
    private int done;
    private int late;
    private int missed;
    private int rest;
    private final List<CalendarResponse.Miss> misses = new ArrayList<>();

    private void add(JudgedCardDay day, Map<Long, String> titles, Map<String, String> notes) {
      switch (day.result()) {
        case DONE, ENDED_EARLY, SELF_REPORT -> done++;
        case LATE -> late++;
        case REST -> rest++;
        case POSTPONED, FAILED, UNHANDLED -> {
          missed++;
          misses.add(
              new CalendarResponse.Miss(
                  String.valueOf(day.cardId()),
                  titles.get(day.cardId()),
                  day.result().toValue(),
                  day.missReason() == null ? null : CallSummary.missReasonLabel(day.missReason()),
                  notes.get(keyOf(day.cardId(), day.date()))));
        }
      }
    }
  }
}
