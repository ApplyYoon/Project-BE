package com.ithopeschool.projectbe.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInResponse;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 저장·덮어쓰기·검사 로직만 확인합니다.
class CheckInServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-17");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final CheckInRepository checkInRepository = mock(CheckInRepository.class);
  private final CheckInService service =
      new CheckInService(currentUserProvider, checkInRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(checkInRepository.findByUserIdAndCheckInDate(any(), any())).willReturn(Optional.empty());
    given(checkInRepository.save(any()))
        .willAnswer(
            invocation -> {
              CheckIn checkIn = invocation.getArgument(0);
              ReflectionTestUtils.setField(checkIn, "id", 3L);
              return checkIn;
            });
  }

  // source 말고는 다 비어 있는 요청
  private static CheckInSaveRequest onlySource(String source) {
    return new CheckInSaveRequest(source, null, null, null, null, null, null, null, null, null);
  }

  private static CheckInSaveRequest withReason(String missReason, String note) {
    return new CheckInSaveRequest(
        "manual", null, null, missReason, note, null, null, null, null, null);
  }

  private static CheckInSaveRequest withPromise(String promiseIf, String promiseThen) {
    return new CheckInSaveRequest(
        "manual", null, null, null, null, null, promiseIf, promiseThen, null, null);
  }

  private void expectRejected(LocalDate date, CheckInSaveRequest request, String message) {
    // 같은 테스트 안에서 앞서 성공한 저장이 있어도, 이번 요청만 보도록 지금까지 부른 기록을 지웁니다.
    clearInvocations(checkInRepository);
    assertThatThrownBy(() -> service.save(date, request))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    verify(checkInRepository, never()).save(any());
  }

  @Test
  @DisplayName("그날 처음 보내면 새 줄을 만들고, source · 이유는 FE 글자로 돌려준다")
  void createsNewDay() {
    CheckInResponse response =
        service.save(
            TODAY,
            new CheckInSaveRequest(
                "manual", 4, 3, "tooMuch", null, 120, "폰을 보고 싶으면", "물 한 잔 마신다", true, null));

    assertThat(response.id()).isEqualTo("3");
    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.source()).isEqualTo("manual");
    assertThat(response.condition()).isEqualTo(4);
    assertThat(response.mood()).isEqualTo(3);
    assertThat(response.missReason()).isEqualTo("tooMuch");
    assertThat(response.expectedMinutes()).isEqualTo(120);
    assertThat(response.promiseIf()).isEqualTo("폰을 보고 싶으면");
    assertThat(response.promiseThen()).isEqualTo("물 한 잔 마신다");
    assertThat(response.suggestionKept()).isTrue();
  }

  @Test
  @DisplayName("같은 날을 다시 보내면 새 줄을 만들지 않고 덮어쓴다. 이번에 안 보낸 칸은 비워진다")
  void overwritesSameDay() {
    CheckIn existing =
        new CheckIn(
            USER_ID,
            TODAY,
            CheckInSource.QUICK,
            3,
            3,
            MissReason.PHONE,
            null,
            90,
            null,
            null,
            false);
    ReflectionTestUtils.setField(existing, "id", 3L);
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, TODAY))
        .willReturn(Optional.of(existing));

    CheckInResponse response =
        service.save(
            TODAY,
            new CheckInSaveRequest("manual", null, 5, null, null, null, null, null, null, null));

    verify(checkInRepository, never()).save(any());
    assertThat(response.id()).isEqualTo("3");
    assertThat(response.source()).isEqualTo("manual");
    assertThat(response.mood()).isEqualTo(5);
    // 이번에 안 보낸 칸은 예전 값이 남지 않습니다.
    assertThat(response.condition()).isNull();
    assertThat(response.missReason()).isNull();
    assertThat(response.expectedMinutes()).isNull();
    assertThat(response.suggestionKept()).isNull();
  }

  @Test
  @DisplayName("source 만 보내도 저장된다. 답하지 않은 칸은 0 이 아니라 비어 있다")
  void savesWithOnlySource() {
    CheckInResponse response = service.save(TODAY, onlySource("quick"));

    assertThat(response.source()).isEqualTo("quick");
    assertThat(response.mood()).isNull();
    assertThat(response.expectedMinutes()).isNull();
  }

  @Test
  @DisplayName("source 는 manual · call · quick 셋만 받는다")
  void acceptsOnlyKnownSources() {
    assertThat(service.save(TODAY, onlySource("call")).source()).isEqualTo("call");
    expectRejected(TODAY, onlySource("phone"), "source 는 manual, call, quick 중에서 골라 주세요");
  }

  @Test
  @DisplayName("못 채운 이유는 정해진 7가지만 받는다")
  void acceptsOnlyKnownReasons() {
    expectRejected(
        TODAY,
        withReason("lazy", null),
        "못 채운 이유는 tired, event, phone, tooMuch, unwilling, sick, etc 중에서 골라 주세요");
  }

  @Test
  @DisplayName("이유 글은 '기타' 를 골랐을 때만 받는다")
  void acceptsNoteOnlyForEtc() {
    CheckInResponse response = service.save(TODAY, withReason("etc", "동아리 발표"));
    assertThat(response.missReason()).isEqualTo("etc");
    assertThat(response.missReasonNote()).isEqualTo("동아리 발표");

    // '기타' 를 골라도 글은 안 적어도 됩니다.
    assertThat(service.save(TODAY, withReason("etc", null)).missReasonNote()).isNull();

    String message = "이유 글은 '기타(etc)' 를 골랐을 때만 적을 수 있어요";
    expectRejected(TODAY, withReason("phone", "폰을 봄"), message);
    expectRejected(TODAY, withReason(null, "이유 없이 글만"), message);
  }

  @Test
  @DisplayName("빈 글자나 띄어쓰기만 보낸 글 칸은 안 보낸 것으로 본다")
  void treatsBlankTextAsEmpty() {
    // 'phone' 인데 글이 띄어쓰기뿐이면, 글을 안 보낸 것이라 통과합니다.
    assertThat(service.save(TODAY, withReason("phone", "  ")).missReasonNote()).isNull();
    CheckInResponse response = service.save(TODAY, withPromise("", " "));
    assertThat(response.promiseIf()).isNull();
    assertThat(response.promiseThen()).isNull();
  }

  @Test
  @DisplayName("약속은 두 칸을 다 적거나 다 비워야 한다 (반쪽 약속은 400)")
  void rejectsHalfPromise() {
    String message = "약속은 '만약 ~하면' 과 '~한다' 를 둘 다 적어 주세요";
    expectRejected(TODAY, withPromise("폰을 보고 싶으면", null), message);
    expectRejected(TODAY, withPromise(null, "물 한 잔 마신다"), message);
  }

  @Test
  @DisplayName("아직 오지 않은 날짜는 400 이다. 오늘은 새벽 4시 기준이다")
  void rejectsFutureDate() {
    expectRejected(TODAY.plusDays(1), onlySource("manual"), "아직 오지 않은 날짜예요");

    // 10/18 새벽 1시(한국 시간)는 아직 10/17 이라, 10/18 체크인은 미래입니다.
    CheckInService earlyMorning =
        new CheckInService(
            currentUserProvider,
            checkInRepository,
            Clock.fixed(Instant.parse("2026-10-17T16:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> earlyMorning.save(TODAY.plusDays(1), onlySource("manual")))
        .hasMessage("아직 오지 않은 날짜예요");
    assertThat(earlyMorning.save(TODAY, onlySource("manual")).date()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("기간 목록은 내 번호로 찾아서 돌려준다")
  void getsList() {
    LocalDate from = LocalDate.parse("2026-10-01");
    LocalDate to = LocalDate.parse("2026-10-31");
    CheckIn checkIn =
        new CheckIn(USER_ID, TODAY, CheckInSource.QUICK, 3, 3, null, null, 90, null, null, null);
    ReflectionTestUtils.setField(checkIn, "id", 3L);
    given(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                USER_ID, from, to))
        .willReturn(List.of(checkIn));

    List<CheckInResponse> list = service.getCheckIns(from, to);

    assertThat(list).extracting(CheckInResponse::source).containsExactly("quick");
  }

  @Test
  @DisplayName("기간이 뒤집혔거나, 31일보다 길거나, 날짜가 터무니없으면 400 이다")
  void rejectsBadPeriods() {
    assertThatThrownBy(
            () -> service.getCheckIns(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-01")))
        .hasMessage("시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertThatThrownBy(
            () -> service.getCheckIns(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01")))
        .hasMessage("한 번에 31일까지만 조회할 수 있어요");
    assertThatThrownBy(
            () -> service.getCheckIns(LocalDate.parse("1969-12-31"), LocalDate.parse("1970-01-01")))
        .hasMessage("날짜는 1970-01-01 부터 9999-12-31 사이로 보내 주세요");
  }

  // 감정 태그만 다르고 나머지는 비어 있는 요청 (이슈 #220)
  private static CheckInSaveRequest withEmotions(List<String> emotions) {
    return new CheckInSaveRequest("quick", 3, null, null, null, null, null, null, null, emotions);
  }

  @Test
  @DisplayName("감정 태그를 여러 개 저장하고, 같은 것을 두 번 보내면 하나로 본다. 응답은 정해진 순서다")
  void savesEmotions() {
    CheckInResponse response =
        service.save(TODAY, withEmotions(List.of("TIRED", "STUCK", "TIRED")));

    assertThat(response.emotions()).containsExactly("STUCK", "TIRED");
    assertThat(response.condition()).isEqualTo(3);
    // 컨디션만 보냈으면 기분은 비어 있습니다. (기분에 복사하지 않습니다)
    assertThat(response.mood()).isNull();
  }

  @Test
  @DisplayName("다시 보내면 감정도 전부 바뀌고, 빈 배열은 비우기, 아예 안 보내면 그대로 둔다")
  void overwritesEmotions() {
    CheckIn existing =
        new CheckIn(
            USER_ID, TODAY, CheckInSource.QUICK, 3, null, null, null, null, null, null, null);
    existing.replaceEmotions(EnumSet.of(EmotionTag.ANXIOUS, EmotionTag.STUCK));
    given(checkInRepository.findByUserIdAndCheckInDate(USER_ID, TODAY))
        .willReturn(Optional.of(existing));

    assertThat(service.save(TODAY, withEmotions(List.of("CALM"))).emotions())
        .containsExactly("CALM");
    // 감정 태그를 모르는 화면이 다시 보낸 경우 (emotions 없음) → 그대로
    assertThat(service.save(TODAY, withEmotions(null)).emotions()).containsExactly("CALM");
    // 빈 배열 → 비우기
    assertThat(service.save(TODAY, withEmotions(List.of())).emotions()).isEmpty();
  }

  @Test
  @DisplayName("정해진 6가지가 아닌 감정 태그가 있으면 400")
  void rejectsUnknownEmotion() {
    expectRejected(
        TODAY,
        withEmotions(List.of("STUCK", "HAPPY")),
        "감정 태그는 STUCK, ANXIOUS, TIRED, ANNOYED, CALM, MOTIVATED 중에서 골라 주세요");
  }

  @Test
  @DisplayName("처음 저장할 때 감정 태그를 안 보내면 빈 배열이다")
  void newCheckInWithoutEmotionsHasNone() {
    assertThat(service.save(TODAY, withEmotions(null)).emotions()).isEmpty();
  }

  @Test
  @DisplayName("모두 지우기는 로그인한 사람의 번호로만 지운다")
  void deleteAllUsesMyUserId() {
    service.deleteAll();

    verify(checkInRepository).deleteAllOfUser(USER_ID);
  }
}
