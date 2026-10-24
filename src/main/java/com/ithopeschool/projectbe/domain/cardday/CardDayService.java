package com.ithopeschool.projectbe.domain.cardday;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardday.dto.CardDayMarkResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.CardMarkResultResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.FailRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.LateEntryRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeLimitResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeRequest;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.session.StudySessionService;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 카드 하루치 처리 — 미루기 · 실패로 기록 · 사후 입력 로직입니다. (이슈 #225, docs/API.md "미루기 · 실패 · 사후 입력")
// 한도와 기한은 서버가 검사합니다. 화면에서만 막으면 조작할 수 있기 때문입니다.
//   한도 계산: PostponeRule / 사후 입력 마감: LateEntryRule (둘 다 DB 를 모르는 순수 함수)
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 남의 카드 번호를 보내면 404 입니다.
// 미루기 · 실패 사유 글은 위기 표현(#56) 검사를 하고, 걸리면 기록은 그대로 하되 safetyBrake 를 켜서 돌려줍니다.
@Service
@RequiredArgsConstructor
public class CardDayService {

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. (공부 기록 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;

  private final CurrentUserProvider currentUserProvider;
  private final CardRepository cardRepository;
  private final CardDayMarkRepository cardDayMarkRepository;
  // 사후 입력 마감("다음 통화 전까지")을 보려고 그날의 통화 기록을 읽습니다.
  private final CallRecordRepository callRecordRepository;
  // 사후 입력 세션을 저장할 때 씁니다. (겹침 검사 · 저장 규칙은 그쪽에 있습니다)
  private final StudySessionService studySessionService;
  // 관리 4단계면 미루기가 잠깁니다. (이슈 #234)
  private final Stage4Service stage4Service;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // ---- 미루기 ----

  // 그 카드 · 그 날짜를 몇 번 더 미룰 수 있는지 알려 줍니다. ([미루기 (n회 남음)] 버튼)
  @Transactional(readOnly = true)
  public PostponeLimitResponse getPostponeLimit(Long cardId, LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    Card card = findMyCard(userId, cardId);
    checkCardOnDate(card, date);

    long usedThisMonth = countPostponesInMonth(userId, cardId, date);
    long usedEver = countPostponesEver(userId, cardId);
    boolean stage4Locked = stage4Service.isOn(userId);
    return new PostponeLimitResponse(
        PostponeRule.postponesLeft(
            card.getPriority(), card.isOneOff(), usedThisMonth, usedEver, stage4Locked),
        PostponeRule.blockReason(
            card.getPriority(), card.isOneOff(), usedThisMonth, usedEver, stage4Locked));
  }

  // 카드 하루치를 미룹니다.
  //   - 1회성 카드: 카드 자체를 새 날짜 · 시각으로 옮기고, 원래 날짜에 "미룸" 표시를 남깁니다.
  //   - 반복 카드: 카드는 그대로 두고, 그 날짜에 "미룸" 표시와 새 날짜 · 시각만 남깁니다. (반복 카드는 한 장이라)
  // 검사 순서: 내 카드(404) → 그날 하는 카드(400) → 이미 처리한 날(409) → 한도 · 4단계 잠금(409) → 새 시각이 지금 뒤(400)
  @Transactional
  public CardMarkResultResponse postpone(Long cardId, PostponeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Card card = findMyCard(userId, cardId);
    LocalDate date = request.date();
    checkCardOnDate(card, date);
    checkNotMarkedYet(userId, cardId, date);

    boolean isOneOff = card.isOneOff();
    long usedThisMonth = countPostponesInMonth(userId, cardId, date);
    long usedEver = countPostponesEver(userId, cardId);
    boolean stage4Locked = stage4Service.isOn(userId);
    String blockReason =
        PostponeRule.blockReason(
            card.getPriority(), isOneOff, usedThisMonth, usedEver, stage4Locked);
    if (blockReason != null) {
      throw new ApiException(ErrorCode.CONFLICT, blockReason);
    }

    // 모양("HH:mm")은 요청 DTO 의 @Pattern 이 이미 검사했습니다.
    LocalTime newStartTime = LocalTime.parse(request.newStartTime());
    if (!Card.startAtOf(request.newDate(), newStartTime).isAfter(clock.instant())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "새 시각은 지금보다 뒤로 정해 주세요");
    }

    boolean crisisDetected = CrisisText.hasCrisisText(List.of(request.reason()));
    CardDayMark mark =
        cardDayMarkRepository.save(
            CardDayMark.postponed(
                userId,
                cardId,
                date,
                request.reason(),
                request.newDate(),
                newStartTime,
                crisisDetected,
                clock.instant()));
    if (isOneOff) {
      card.changeCardDate(request.newDate());
      card.changeStartTime(newStartTime);
    }

    // 방금 한 번 썼으므로 1을 더해서 남은 횟수를 셉니다.
    int left =
        PostponeRule.postponesLeft(
            card.getPriority(), isOneOff, usedThisMonth + 1, usedEver + 1, false);
    return new CardMarkResultResponse(CardDayMarkResponse.from(mark), left, crisisDetected);
  }

  // ---- 실패로 기록 ----

  // 카드 하루치를 실패로 기록합니다. 원인 태그는 꼭 있어야 합니다.
  // 검사 순서: 내 카드(404) → 그날 하는 카드(400) → 아직 오지 않은 날(400) → 원인(400) → 이미 처리한 날(409)
  @Transactional
  public CardMarkResultResponse fail(Long cardId, FailRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Card card = findMyCard(userId, cardId);
    LocalDate date = request.date();
    checkCardOnDate(card, date);
    if (date.isAfter(DayRule.dateOf(clock.instant()))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날은 실패로 기록할 수 없어요");
    }
    MissReason cause = MissReason.fromValue(request.cause());
    if (cause == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "실패 원인은 tired, event, phone, tooMuch, unwilling, sick, procrastinated, etc 중에서 골라 주세요");
    }
    checkNotMarkedYet(userId, cardId, date);

    String reason =
        request.reason() == null || request.reason().isEmpty() ? null : request.reason();
    boolean crisisDetected = reason != null && CrisisText.hasCrisisText(List.of(reason));
    CardDayMark mark =
        cardDayMarkRepository.save(
            CardDayMark.failed(
                userId, cardId, date, cause, reason, crisisDetected, clock.instant()));
    return new CardMarkResultResponse(CardDayMarkResponse.from(mark), null, crisisDetected);
  }

  // ---- 사후 입력 ----

  // 오프라인 세션을 켜지 못했을 때, 끝난 뒤에 세션을 적습니다. 잰 방법은 사후 입력(selfReport)입니다.
  // 검사 순서: 내 카드(404) → 그날 하는 카드 · 시각이 그날 안 · 카드 길이 이하(400)
  //          → 미리 만든 카드 · 그날 다음 통화 전(409) → 저장 규칙(StudySessionService.createSelfReport)
  @Transactional
  public StudySessionResponse lateEntry(Long cardId, LateEntryRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Card card = findMyCard(userId, cardId);
    LocalDate date = request.date();
    checkCardOnDate(card, date);

    Instant startedAt = Instant.ofEpochMilli(request.startedAt());
    Instant endedAt = Instant.ofEpochMilli(request.endedAt());
    if (!endedAt.isAfter(startedAt)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "끝 시각은 시작 시각보다 뒤여야 해요");
    }
    if (!DayRule.dateOf(startedAt).equals(date)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "사후 입력 시각은 그 카드의 날짜 안이어야 해요");
    }
    // 카드 예정 시간 이하로만 적을 수 있습니다. (60분 카드면 시작 ~ 끝이 60분 이하)
    long enteredMillis = endedAt.toEpochMilli() - startedAt.toEpochMilli();
    if (enteredMillis > card.getDurationMinutes() * 60_000L) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "카드 예정 시간(" + card.getDurationMinutes() + "분)보다 길게 적을 수 없어요");
    }

    // 그날 카드 예정 시작 순간 (시작 시각이 없는 옛 카드는 그날 04:00)
    Instant cardStartAt =
        card.getStartTime() == null
            ? DayRule.startOf(date)
            : Card.startAtOf(date, card.getStartTime());
    // 미리 만들어 둔 카드에만 받습니다. (없던 세션을 새로 만들 수 없게)
    if (card.getCreatedAt().isAfter(cardStartAt)) {
      throw new ApiException(ErrorCode.CONFLICT, "미리 만들어 둔 카드에만 사후 입력할 수 있어요");
    }
    List<Instant> callStartedAts = new ArrayList<>();
    for (CallRecord call :
        callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
            userId, date, date)) {
      callStartedAts.add(call.getStartedAt());
    }
    if (!LateEntryRule.isOpen(date, cardStartAt, clock.instant(), callStartedAts)) {
      throw new ApiException(ErrorCode.CONFLICT, "사후 입력은 카드 시간이 지난 뒤, 그날 다음 통화 전까지만 할 수 있어요");
    }

    return studySessionService.createSelfReport(
        cardId, card.getDurationMinutes(), startedAt, endedAt, request.endReport());
  }

  // ---- 목록 ----

  // from ~ to (두 날짜 모두 포함) 의 내 표시(미루기 · 실패) 목록. 31일까지. 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<CardDayMarkResponse> getMarks(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }
    List<CardDayMarkResponse> marks = new ArrayList<>();
    for (CardDayMark mark :
        cardDayMarkRepository.findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
            userId, from, to)) {
      marks.add(CardDayMarkResponse.from(mark));
    }
    return marks;
  }

  // ---- 도우미 ----

  // 내 카드 중에서 번호로 찾습니다. 없는 번호이거나 남의 카드면 똑같이 404 입니다.
  private Card findMyCard(Long userId, Long cardId) {
    return cardRepository
        .findByIdAndUserId(cardId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요"));
  }

  // 그 날짜에 하는 카드인지 봅니다. 아니면 400 입니다. (1회성은 그 날짜, 반복은 시작한 날부터 그 요일)
  private void checkCardOnDate(Card card, LocalDate date) {
    if (!card.isOnDate(date)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "그날 하는 카드가 아니에요");
    }
  }

  // 그 카드 · 그 날짜에 이미 미루기나 실패 표시가 있으면 409 입니다. (한 날에는 한 번만 처리)
  private void checkNotMarkedYet(Long userId, Long cardId, LocalDate date) {
    if (cardDayMarkRepository.existsByUserIdAndCardIdAndMarkDate(userId, cardId, date)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 미루거나 실패로 기록한 날이에요");
    }
  }

  // date 가 있는 달(1일 ~ 말일)에 이 카드를 미룬 횟수
  private long countPostponesInMonth(Long userId, Long cardId, LocalDate date) {
    LocalDate firstDay = date.withDayOfMonth(1);
    LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
    return cardDayMarkRepository.countByUserIdAndCardIdAndMarkAndMarkDateBetween(
        userId, cardId, CardDayMarkType.POSTPONED, firstDay, lastDay);
  }

  // 이 카드를 지금까지 미룬 횟수
  private long countPostponesEver(Long userId, Long cardId) {
    return cardDayMarkRepository.countByUserIdAndCardIdAndMark(
        userId, cardId, CardDayMarkType.POSTPONED);
  }
}
