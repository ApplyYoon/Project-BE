package com.ithopeschool.projectbe.domain.cardresult;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.cardday.CardDayMark;
import com.ithopeschool.projectbe.domain.cardday.CardDayMarkRepository;
import com.ithopeschool.projectbe.domain.cardday.CardDayMarkType;
import com.ithopeschool.projectbe.domain.cardresult.dto.CardDayResultResponse;
import com.ithopeschool.projectbe.domain.cardresult.dto.PlanPatternResponse;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.restday.RestDay;
import com.ithopeschool.projectbe.domain.restday.RestDayRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카드 결과 조회 로직입니다. (이슈 #229, docs/API.md "카드 결과")
// DB 에서 카드 · 공부 기록 · 미루기/실패 표시 · 휴식 날을 읽어 카드 하루치(CardDay)를 만들고,
// 판정은 순수 함수 CardResultJudge 에 맡깁니다. 결과는 저장하지 않고 부를 때마다 계산합니다.
//
// 카드 하루치는 이렇게 만듭니다.
//   - 그날 하는 카드(Card.isOnDate) 마다 하나. 미룬 1회성 카드는 카드가 새 날짜로 옮겨져서,
//     원래 날짜에 남은 "미룸" 표시로 그 날짜의 카드 하루치를 하나 더 만듭니다.
//   - 세션: 그 카드로 켠 공부 기록 중 "시작한 날"(새벽 4시 기준, DayRule)이 그 날짜인 것. 노는 기록은 뺍니다.
//   - 표시: 휴식 날이면 그날 모든 카드가 REST, 아니면 그 카드 · 그 날짜의 미루기/실패 표시
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 남의 카드 · 기록은 섞이지 않습니다.
@Service
@RequiredArgsConstructor
public class CardResultService {

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. (공부 기록 · 표시 목록 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;
  // 계획 패턴을 보는 기간(일). 오늘을 포함한 최근 4주입니다. (기획서 「5.1」 "최근 4주")
  static final int PATTERN_DAYS = 28;

  // 그날 카드의 정렬 기준: 시작 시각이 빠른 순 → 시작 시각이 없는 카드는 뒤로 → 같으면 만든 순서(번호 순)
  // CardService 의 목록과 같은 순서입니다. (FE 의 getCardsOnDate)
  private static final Comparator<Card> CARD_ORDER =
      Comparator.comparing(
              Card::getStartTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
          .thenComparing(Card::getId);

  private final CurrentUserProvider currentUserProvider;
  private final CardRepository cardRepository;
  private final StudySessionRepository studySessionRepository;
  private final CardDayMarkRepository cardDayMarkRepository;
  private final RestDayRepository restDayRepository;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // 카드 하루치 하나와 그 카드, 실패로 기록할 때 고른 원인(없으면 null). (이 Service 안에서만 씁니다)
  private record CardDayOfCard(Card card, CardDay day, MissReason missReason) {}

  // "어느 카드의 어느 날짜" 를 가리키는 열쇠. 같은 카드 · 같은 날짜면 같은 열쇠입니다. (record 라서 equals 가 저절로 생김)
  private record CardDateKey(Long cardId, LocalDate date) {}

  // from ~ to (두 날짜 모두 포함) 의 카드 하루치 결과를 날짜 순 → 그날 카드 순서로 돌려줍니다. 31일까지.
  // 카드가 없는 기간은 빈 목록입니다. 아직 판정할 수 없는 카드(카드 시간 전)는 result 가 null 입니다.
  @Transactional(readOnly = true)
  public List<CardDayResultResponse> getResults(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    Instant now = clock.instant();
    List<CardDayResultResponse> responses = new ArrayList<>();
    for (CardDayOfCard entry : loadCardDays(userId, from, to)) {
      CardResult result = CardResultJudge.getCardResult(entry.day(), now);
      responses.add(CardDayResultResponse.from(entry.card().getId(), entry.day(), result));
    }
    return responses;
  }

  // 한 목표의 최근 4주(오늘 포함 28일) 계획 패턴을 돌려줍니다.
  // 그 목표에 연결한 카드의 하루치 중 세션이 있었던 날의 체크리스트 달성률로 정합니다. 휴식 날은 뺍니다.
  // 카드 하루치가 5개보다 적으면 pattern 은 null 입니다. (아직 말하지 않음)
  @Transactional(readOnly = true)
  public PlanPatternResponse getPlanPattern(Long goalId) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate from = today.minusDays(PATTERN_DAYS - 1);

    List<Double> rates = new ArrayList<>();
    for (CardDayOfCard entry : loadCardDays(userId, from, today)) {
      CardDay day = entry.day();
      // 다른 목표의 카드, 휴식 날, 세션이 없는 날(미루기 · 실패 · 미처리 · 아직 안 함)은 계획 크기를 말해 주지 않습니다.
      if (!goalId.equals(entry.card().getGoalId())
          || day.mark() == CardResult.REST
          || !CardResultJudge.hasCountedSession(day)) {
        continue;
      }
      rates.add(CardResultJudge.getChecklistRate(day));
    }

    Double average = null;
    if (!rates.isEmpty()) {
      double sum = 0;
      for (double rate : rates) {
        sum += rate;
      }
      average = sum / rates.size();
    }
    PlanPattern pattern = CardResultJudge.getPlanPattern(rates);
    return new PlanPatternResponse(
        String.valueOf(goalId),
        from,
        today,
        rates.size(),
        average,
        pattern == null ? null : pattern.toValue());
  }

  // from ~ to (두 날짜 모두 포함) 의 판정이 끝난 카드 하루치를 날짜 순 → 그날 카드 순서로 돌려줍니다. (이슈 #230)
  // 재발 신호(RelapseSignalService) · 의지력처럼 다른 Service 가 부릅니다. 기간 제한은 없습니다.
  // userId 는 부르는 쪽이 currentUserProvider.getCurrentUserId() 로 얻은 값을 넘깁니다.
  // 아직 판정 전(result 가 null)인 날은 넣지 않습니다. (FE JudgedCardDay 와 같음)
  @Transactional(readOnly = true)
  public List<JudgedCardDay> getJudgedDays(Long userId, LocalDate from, LocalDate to) {
    Instant now = clock.instant();
    List<JudgedCardDay> judged = new ArrayList<>();
    for (CardDayOfCard entry : loadCardDays(userId, from, to)) {
      CardDay day = entry.day();
      CardResult result = CardResultJudge.getCardResult(day, now);
      if (result == null) {
        continue;
      }
      Card card = entry.card();
      judged.add(
          new JudgedCardDay(
              card.getId(),
              day.date(),
              !card.isOneOff(),
              card.getPriority(),
              result,
              // 미실행은 실행률 0 (응답 CardDayResultResponse 와 같은 규칙)
              result.isMiss() ? 0 : CardResultJudge.getRunRate(day),
              entry.missReason(),
              CardResultJudge.isStartedOnTime(day),
              CardResultJudge.hasCountedSession(day)
                  ? CardResultJudge.getChecklistRate(day)
                  : null));
    }
    return judged;
  }

  // ---- 도우미 ----

  // from ~ to 의 카드 하루치를 날짜 순 → 그날 카드 순서로 만듭니다.
  // 날짜마다 DB 에 묻지 않고, 기간의 카드 · 표시 · 휴식 날 · 기록을 한 번씩 읽어 와서 자바에서 나눕니다.
  private List<CardDayOfCard> loadCardDays(Long userId, LocalDate from, LocalDate to) {
    List<CardDayMark> marks =
        cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
            userId, from, to);
    Set<LocalDate> restDates = new HashSet<>();
    for (RestDay restDay :
        restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(userId, from, to)) {
      restDates.add(restDay.getRestDate());
    }

    // 카드: 기간에 할 수도 있는 카드(후보) + 표시만 남은 카드(미뤄서 옮겨진 1회성 카드)
    Map<Long, Card> cardsById = new LinkedHashMap<>();
    for (Card card : cardRepository.findCandidatesBetween(userId, from, to)) {
      cardsById.put(card.getId(), card);
    }
    Set<Long> markedOnlyIds = new HashSet<>();
    Map<CardDateKey, CardDayMark> markByKey = new HashMap<>();
    for (CardDayMark mark : marks) {
      markByKey.put(new CardDateKey(mark.getCardId(), mark.getMarkDate()), mark);
      if (!cardsById.containsKey(mark.getCardId())) {
        markedOnlyIds.add(mark.getCardId());
      }
    }
    if (!markedOnlyIds.isEmpty()) {
      for (Card card : cardRepository.findByUserIdAndIdIn(userId, markedOnlyIds)) {
        cardsById.put(card.getId(), card);
      }
    }

    // 세션: (카드, 시작한 날) 마다 묶습니다. 노는 기록과 카드 없이 켠 옛 기록은 카드 판정에 넣지 않습니다.
    Map<CardDateKey, List<CardDay.Session>> sessionsByKey = new HashMap<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))) {
      if (session.getKind() == PlanKind.PLAY || session.getCardId() == null) {
        continue;
      }
      CardDateKey key =
          new CardDateKey(session.getCardId(), DayRule.dateOf(session.getStartedAt()));
      sessionsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(toJudgeSession(session));
    }

    List<CardDayOfCard> days = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      // 그날 하는 카드 + 그날 표시가 남은 카드
      List<Card> cardsOfDate = new ArrayList<>();
      for (Card card : cardsById.values()) {
        if (card.isOnDate(date) || markByKey.containsKey(new CardDateKey(card.getId(), date))) {
          cardsOfDate.add(card);
        }
      }
      cardsOfDate.sort(CARD_ORDER);

      for (Card card : cardsOfDate) {
        CardDateKey key = new CardDateKey(card.getId(), date);
        // 휴식 날이면 그 카드의 표시와 상관없이 휴식입니다. (휴식은 모든 집계에서 빠지는 것이 먼저)
        CardResult mark = restDates.contains(date) ? CardResult.REST : toResult(markByKey.get(key));
        List<CardDay.Session> sessions = sessionsByKey.getOrDefault(key, List.of());
        CardDayMark cardDayMark = markByKey.get(key);
        MissReason cause = cardDayMark == null ? null : cardDayMark.getCause();
        days.add(new CardDayOfCard(card, toCardDay(card, date, sessions, mark), cause));
      }
    }
    return days;
  }

  // 카드 한 장 · 날짜 하나로 판정에 넣는 카드 하루치를 만듭니다.
  private static CardDay toCardDay(
      Card card, LocalDate date, List<CardDay.Session> sessions, CardResult mark) {
    List<String> checklistKeys = new ArrayList<>();
    for (ChecklistItem item : card.getChecklist()) {
      checklistKeys.add(item.getItemKey());
    }
    return new CardDay(
        card.getStartTime(),
        card.getDurationMinutes(),
        checklistKeys,
        card.getExpectedDone(),
        date,
        sessions,
        mark);
  }

  // 공부 기록 하나를 판정에 넣는 세션으로 바꿉니다.
  // ⚠️ proofRequired(관리 4단계라 증빙이 꼭 필요했던 세션)는 아직 서버에 저장하지 않아서 false 로 넣습니다.
  private static CardDay.Session toJudgeSession(StudySession session) {
    // FE 의 hasProof 와 같습니다: 링크나 요약 중 하나라도 글자가 있으면 증빙이 있는 것입니다.
    boolean hasProof = hasText(session.getProofLink()) || hasText(session.getProofSummary());
    return new CardDay.Session(
        session.getStartedAt(),
        StudyTime.studyMillis(session.getStartedAt(), session.getEndedAt(), session.getPausedMs()),
        session.modeOrNormal(),
        Boolean.TRUE.equals(session.getEndedEarly()),
        new HashSet<>(session.getCheckedItems().keySet()),
        hasProof,
        false);
  }

  // 미루기 · 실패 표시를 결과로 바꿉니다. 표시가 없으면 null
  private static CardResult toResult(CardDayMark mark) {
    if (mark == null) {
      return null;
    }
    return mark.getMark() == CardDayMarkType.POSTPONED ? CardResult.POSTPONED : CardResult.FAILED;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
