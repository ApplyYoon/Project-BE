package com.ithopeschool.projectbe.domain.cardday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.cardday.dto.CardMarkResultResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.FailRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.LateEntryRequest;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeLimitResponse;
import com.ithopeschool.projectbe.domain.cardday.dto.PostponeRequest;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.care.Stage4;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.session.StudySessionService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// 미루기 · 실패 · 사후 입력의 검사 순서와 결과를 확인합니다. DB 는 가짜로 바꿉니다. (이슈 #225)
// 한도 · 마감 계산 자체는 PostponeRuleTest · LateEntryRuleTest 가 확인합니다.
class CardDayServiceTest {

  private static final Long ME = 1L;
  // 2026-10-12 는 월요일입니다.
  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);
  // 지금 = 10-12(월) 20:30 (한국 시간). 19:00 카드가 끝난 뒤입니다.
  private static final Instant NOW = kst(12, 20, 30);
  // 위기 표현 없이 100자를 채운 이유
  private static final String REASON_100 =
      "숙제가 많아서 오늘은 못 하겠다. 내일 저녁 8시에 한다. ".repeat(4) + "가".repeat(20);

  private CardRepository cardRepository;
  private CardDayMarkRepository cardDayMarkRepository;
  private CallRecordRepository callRecordRepository;
  private StudySessionService studySessionService;
  private Stage4Service stage4Service;
  private CardDayService service;

  // 반복 카드(10-01 시작, 월·수 19:00 60분) · 1회성 카드(10-12 19:00) · 절대 카드(10-12 19:00)
  private Card repeatCard;
  private Card oneOffCard;
  private Card absoluteCard;

  private static Instant kst(int day, int hour, int minute) {
    return ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  private static Card card(long id, LocalDate date, List<Integer> repeatDays, Instant createdAt) {
    Card card = new Card(ME, "수학", date, 60, List.of(new ChecklistItem("i1", "42쪽")), createdAt);
    card.changeRepeatDays(repeatDays);
    card.changeStartTime(LocalTime.of(19, 0));
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    cardRepository = mock(CardRepository.class);
    cardDayMarkRepository = mock(CardDayMarkRepository.class);
    callRecordRepository = mock(CallRecordRepository.class);
    studySessionService = mock(StudySessionService.class);
    // 기본은 4단계가 아닙니다. (4단계 테스트에서만 켭니다)
    stage4Service = mock(Stage4Service.class);
    service =
        new CardDayService(
            currentUserProvider,
            cardRepository,
            cardDayMarkRepository,
            callRecordRepository,
            studySessionService,
            stage4Service,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);

    repeatCard = card(7L, LocalDate.of(2026, 10, 1), List.of(1, 3), kst(1, 9, 0));
    oneOffCard = card(8L, OCT_12, List.of(), kst(10, 9, 0));
    absoluteCard = card(9L, OCT_12, List.of(), kst(10, 9, 0));
    absoluteCard.changePriority(CardPriority.ABSOLUTE);
    given(cardRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(cardRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(repeatCard));
    given(cardRepository.findByIdAndUserId(8L, ME)).willReturn(Optional.of(oneOffCard));
    given(cardRepository.findByIdAndUserId(9L, ME)).willReturn(Optional.of(absoluteCard));
    // 처음에는 처리한 날도, 미룬 횟수도, 통화도 없습니다.
    given(cardDayMarkRepository.save(any()))
        .willAnswer(
            invocation -> {
              CardDayMark mark = invocation.getArgument(0);
              ReflectionTestUtils.setField(mark, "id", 100L);
              return mark;
            });
    given(
            callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
                any(), any(), any()))
        .willReturn(List.of());
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  private CardDayMark savedMark() {
    ArgumentCaptor<CardDayMark> captor = ArgumentCaptor.forClass(CardDayMark.class);
    verify(cardDayMarkRepository).save(captor.capture());
    return captor.getValue();
  }

  private static PostponeRequest postpone(LocalDate date, String reason) {
    return new PostponeRequest(date, reason, OCT_12.plusDays(1), "20:00");
  }

  // ---- 미루기 ----

  @Test
  @DisplayName("1회성 카드를 미루면 원래 날짜에 표시가 남고, 카드는 새 날짜·시각으로 옮겨지며, 1번 남는다")
  void postponeOneOffMovesCard() {
    CardMarkResultResponse result = service.postpone(8L, postpone(OCT_12, REASON_100));

    CardDayMark mark = savedMark();
    assertThat(mark.getMark()).isEqualTo(CardDayMarkType.POSTPONED);
    assertThat(mark.getMarkDate()).isEqualTo(OCT_12);
    assertThat(mark.getReason()).isEqualTo(REASON_100);
    assertThat(mark.getNewDate()).isEqualTo(OCT_12.plusDays(1));
    assertThat(mark.getNewStartTime()).isEqualTo(LocalTime.of(20, 0));
    assertThat(oneOffCard.getCardDate()).isEqualTo(OCT_12.plusDays(1));
    assertThat(oneOffCard.getStartTime()).isEqualTo(LocalTime.of(20, 0));
    assertThat(result.postponesLeft()).isEqualTo(1);
    assertThat(result.safetyBrake()).isFalse();
    assertThat(result.mark().mark()).isEqualTo("postponed");
    assertThat(result.mark().newStartTime()).isEqualTo("20:00");
  }

  @Test
  @DisplayName("반복 카드를 미루면 카드는 그대로이고 표시에만 새 날짜·시각이 남는다 (이번 달 2번 썼으면 2번 남음)")
  void postponeRepeatKeepsCard() {
    given(
            cardDayMarkRepository.countByUserIdAndCardIdAndMarkAndMarkDateBetween(
                ME,
                7L,
                CardDayMarkType.POSTPONED,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)))
        .willReturn(2L);

    CardMarkResultResponse result = service.postpone(7L, postpone(OCT_12, REASON_100));

    assertThat(repeatCard.getCardDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(repeatCard.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(savedMark().getNewDate()).isEqualTo(OCT_12.plusDays(1));
    assertThat(result.postponesLeft()).isEqualTo(2);
  }

  @Test
  @DisplayName("한도를 넘으면 409: 절대 카드 · 반복 카드 이번 달 5번 · 1회성 카드 2번")
  void postponeLimits() {
    assertError(
        () -> service.postpone(9L, postpone(OCT_12, REASON_100)),
        ErrorCode.CONFLICT,
        "절대 카드는 미룰 수 없어요");

    given(
            cardDayMarkRepository.countByUserIdAndCardIdAndMarkAndMarkDateBetween(
                eq(ME), eq(7L), eq(CardDayMarkType.POSTPONED), any(), any()))
        .willReturn(5L);
    assertError(
        () -> service.postpone(7L, postpone(OCT_12, REASON_100)),
        ErrorCode.CONFLICT,
        "이번 달에는 이 카드를 더 미룰 수 없어요 (반복 카드는 한 달에 5번까지)");

    given(cardDayMarkRepository.countByUserIdAndCardIdAndMark(ME, 8L, CardDayMarkType.POSTPONED))
        .willReturn(2L);
    assertError(
        () -> service.postpone(8L, postpone(OCT_12, REASON_100)),
        ErrorCode.CONFLICT,
        "이 카드는 더 미룰 수 없어요 (1회성 카드는 2번까지)");
    verify(cardDayMarkRepository, never()).save(any());
  }

  @Test
  @DisplayName("그날 하는 카드가 아니면 400, 이미 처리한 날이면 409, 새 시각이 지금보다 앞이면 400")
  void postponeChecks() {
    // 2026-10-13 은 화요일이라 월·수 카드를 하지 않습니다.
    assertError(
        () -> service.postpone(7L, postpone(OCT_12.plusDays(1), REASON_100)),
        ErrorCode.VALIDATION_ERROR,
        "그날 하는 카드가 아니에요");

    given(cardDayMarkRepository.existsByUserIdAndCardIdAndMarkDate(ME, 7L, OCT_12))
        .willReturn(true);
    assertError(
        () -> service.postpone(7L, postpone(OCT_12, REASON_100)),
        ErrorCode.CONFLICT,
        "이미 미루거나 실패로 기록한 날이에요");

    assertError(
        () -> service.postpone(8L, new PostponeRequest(OCT_12, REASON_100, OCT_12, "20:00")),
        ErrorCode.VALIDATION_ERROR,
        "새 시각은 지금보다 뒤로 정해 주세요");
    verify(cardDayMarkRepository, never()).save(any());
  }

  @Test
  @DisplayName("미루는 이유에 위기 표현이 있으면 미루기는 그대로 하되 safetyBrake 가 켜지고, 이유 글은 남기지 않는다")
  void postponeCrisisReason() {
    String crisis = "요즘 너무 힘들어서 죽고 싶다는 생각이 든다. " + "가".repeat(90);

    CardMarkResultResponse result = service.postpone(8L, postpone(OCT_12, crisis));

    assertThat(result.safetyBrake()).isTrue();
    CardDayMark mark = savedMark();
    assertThat(mark.isCrisisDetected()).isTrue();
    assertThat(mark.getReason()).isNull();
    assertThat(result.mark().reason()).isNull();
  }

  @Test
  @DisplayName("남은 미루기 횟수를 미리 알려 준다 (1회성 1번 썼으면 1, 절대 카드는 0 과 이유)")
  void postponeLimitQuery() {
    given(cardDayMarkRepository.countByUserIdAndCardIdAndMark(ME, 8L, CardDayMarkType.POSTPONED))
        .willReturn(1L);

    assertThat(service.getPostponeLimit(8L, OCT_12)).isEqualTo(new PostponeLimitResponse(1, null));
    assertThat(service.getPostponeLimit(9L, OCT_12))
        .isEqualTo(new PostponeLimitResponse(0, "절대 카드는 미룰 수 없어요"));
  }

  @Test
  @DisplayName("관리 4단계면 한도가 남아도 미루기가 잠긴다: 409, 남은 횟수 0 과 이유 (#234)")
  void postponeLockedInStage4() {
    given(stage4Service.isOn(ME)).willReturn(true);

    assertError(
        () -> service.postpone(8L, postpone(OCT_12, REASON_100)),
        ErrorCode.CONFLICT,
        Stage4.POSTPONE_LOCK_REASON);
    verify(cardDayMarkRepository, never()).save(any());
    assertThat(oneOffCard.getCardDate()).isEqualTo(OCT_12);
    assertThat(service.getPostponeLimit(8L, OCT_12))
        .isEqualTo(new PostponeLimitResponse(0, Stage4.POSTPONE_LOCK_REASON));
  }

  // ---- 실패로 기록 ----

  @Test
  @DisplayName("실패로 기록하면 원인 태그와 사유가 남는다 ('미루다 보니' 도 된다)")
  void fail() {
    CardMarkResultResponse result =
        service.fail(7L, new FailRequest(OCT_12, "procrastinated", "유튜브 보다가 시간이 지났다"));

    CardDayMark mark = savedMark();
    assertThat(mark.getMark()).isEqualTo(CardDayMarkType.FAILED);
    assertThat(mark.getCause()).isEqualTo(MissReason.PROCRASTINATED);
    assertThat(mark.getReason()).isEqualTo("유튜브 보다가 시간이 지났다");
    assertThat(result.postponesLeft()).isNull();
    assertThat(result.mark().cause()).isEqualTo("procrastinated");
    assertThat(result.safetyBrake()).isFalse();
  }

  @Test
  @DisplayName("실패: 원인이 8가지가 아니면 400, 아직 오지 않은 날이면 400, 이미 처리한 날이면 409")
  void failChecks() {
    assertError(
        () -> service.fail(7L, new FailRequest(OCT_12, "lazy", null)),
        ErrorCode.VALIDATION_ERROR,
        "실패 원인은 tired, event, phone, tooMuch, unwilling, sick, procrastinated, etc 중에서 골라 주세요");
    assertError(
        () -> service.fail(7L, new FailRequest(LocalDate.of(2026, 10, 14), "tired", null)),
        ErrorCode.VALIDATION_ERROR,
        "아직 오지 않은 날은 실패로 기록할 수 없어요");
    given(cardDayMarkRepository.existsByUserIdAndCardIdAndMarkDate(ME, 7L, OCT_12))
        .willReturn(true);
    assertError(
        () -> service.fail(7L, new FailRequest(OCT_12, "tired", null)),
        ErrorCode.CONFLICT,
        "이미 미루거나 실패로 기록한 날이에요");
    assertError(
        () -> service.fail(99L, new FailRequest(OCT_12, "tired", null)),
        ErrorCode.NOT_FOUND,
        "카드를 찾을 수 없어요");
    verify(cardDayMarkRepository, never()).save(any());
  }

  @Test
  @DisplayName("실패 사유에 위기 표현이 있으면 safetyBrake 가 켜진다 (사유 없이 원인만 고르면 꺼져 있다)")
  void failCrisis() {
    assertThat(service.fail(7L, new FailRequest(OCT_12, "etc", "그냥 사라지고 싶었다")).safetyBrake())
        .isTrue();
    assertThat(service.fail(8L, new FailRequest(OCT_12, "tired", "")).safetyBrake()).isFalse();
  }

  // ---- 사후 입력 ----

  private static LateEntryRequest lateEntry(Instant from, Instant to) {
    return new LateEntryRequest(OCT_12, from.toEpochMilli(), to.toEpochMilli(), null);
  }

  @Test
  @DisplayName("사후 입력: 조건이 맞으면 카드 길이를 목표로 사후 입력 세션 저장을 부른다")
  void lateEntryOk() {
    service.lateEntry(7L, lateEntry(kst(12, 19, 0), kst(12, 19, 50)));

    verify(studySessionService).createSelfReport(7L, 60, kst(12, 19, 0), kst(12, 19, 50), null);
  }

  @Test
  @DisplayName("사후 입력: 카드 길이보다 길거나 · 그날 밖의 시각이거나 · 끝이 시작 전이면 400")
  void lateEntryValues() {
    assertError(
        () -> service.lateEntry(7L, lateEntry(kst(12, 19, 0), kst(12, 20, 1))),
        ErrorCode.VALIDATION_ERROR,
        "카드 예정 시간(60분)보다 길게 적을 수 없어요");
    assertError(
        () -> service.lateEntry(7L, lateEntry(kst(12, 3, 0), kst(12, 3, 30))),
        ErrorCode.VALIDATION_ERROR,
        "사후 입력 시각은 그 카드의 날짜 안이어야 해요");
    assertError(
        () -> service.lateEntry(7L, lateEntry(kst(12, 19, 30), kst(12, 19, 0))),
        ErrorCode.VALIDATION_ERROR,
        "끝 시각은 시작 시각보다 뒤여야 해요");
    verify(studySessionService, never()).createSelfReport(any(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("사후 입력: 카드 시작 뒤에 통화가 있었거나 · 카드를 나중에 만들었으면 409")
  void lateEntryDeadline() {
    given(
            callRecordRepository.findByUserIdAndCallDateBetweenOrderByStartedAtAscIdAsc(
                ME, OCT_12, OCT_12))
        .willReturn(List.of(new CallRecord(ME, OCT_12, kst(12, 20, 0), null)));
    assertError(
        () -> service.lateEntry(7L, lateEntry(kst(12, 19, 0), kst(12, 19, 50))),
        ErrorCode.CONFLICT,
        "사후 입력은 카드 시간이 지난 뒤, 그날 다음 통화 전까지만 할 수 있어요");

    // 카드 시간(19:00)이 지난 뒤에 만든 카드
    Card lateCard = card(10L, OCT_12, List.of(), kst(12, 20, 0));
    given(cardRepository.findByIdAndUserId(10L, ME)).willReturn(Optional.of(lateCard));
    assertError(
        () -> service.lateEntry(10L, lateEntry(kst(12, 19, 0), kst(12, 19, 50))),
        ErrorCode.CONFLICT,
        "미리 만들어 둔 카드에만 사후 입력할 수 있어요");
    verify(studySessionService, never()).createSelfReport(any(), anyInt(), any(), any(), any());
  }

  // ---- 목록 ----

  @Test
  @DisplayName("표시 목록은 시작이 끝보다 늦거나 31일을 넘으면 400")
  void marksRange() {
    assertError(
        () -> service.getMarks(OCT_12, OCT_12.minusDays(1)),
        ErrorCode.VALIDATION_ERROR,
        "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertError(
        () -> service.getMarks(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 31일까지만 조회할 수 있어요");
  }
}
