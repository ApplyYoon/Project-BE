package com.ithopeschool.projectbe.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.consent.dto.ConsentAgreeRequest;
import com.ithopeschool.projectbe.domain.consent.dto.ConsentStatusResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 동의 기록·동의 상태·다시 동의 로직만 확인합니다.
class ConsentServiceTest {

  private static final Long USER_ID = 7L;
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  private static final String OLD_TEXT_MESSAGE = "동의 문구가 바뀌었어요. 화면을 새로 고쳐서 최신 문구에 동의해 주세요";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ConsentRecordRepository consentRecordRepository =
      mock(ConsentRecordRepository.class);
  private final ConsentService service =
      new ConsentService(
          currentUserProvider, consentRecordRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    // 동의 상태·다시 동의는 "동의 검사가 없는 쪽" 으로 누구인지만 확인합니다.
    given(currentUserProvider.getCurrentUserIdWithoutConsent()).willReturn(USER_ID);
    given(consentRecordRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private static ConsentAgreeRequest agree(String version) {
    return new ConsentAgreeRequest(true, true, true, version);
  }

  private void latestIs(String version) {
    given(consentRecordRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(new ConsentRecord(USER_ID, NOW.minusSeconds(3600), version, true)));
  }

  private static void assertConflict(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);
  }

  @Test
  @DisplayName("가입할 때 동의 기록 한 줄을 남긴다: 가입한 사람 · 지금 시각 · 문구 버전 · 만 14세 확인(true)")
  void recordAtSignup() {
    service.recordAtSignup(USER_ID, ConsentPolicy.CURRENT_VERSION);

    ArgumentCaptor<ConsentRecord> saved = ArgumentCaptor.forClass(ConsentRecord.class);
    verify(consentRecordRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getAgreedAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getTextVersion()).isEqualTo("v1");
    assertThat(saved.getValue().getAgeConfirmedOver14()).isTrue();
  }

  @Test
  @DisplayName("지금 받는 버전이 아니면(옛 버전 · 모르는 버전 · 비어 있음) 409 이고 아무것도 저장하지 않는다")
  void signupWithWrongVersion() {
    for (String version : new String[] {"v0", "v2", "V1", " v1", "", null}) {
      assertConflict(() -> service.recordAtSignup(USER_ID, version), OLD_TEXT_MESSAGE);
      assertConflict(() -> service.checkVersion(version), OLD_TEXT_MESSAGE);
    }
    verify(consentRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("동의 상태: 한 번도 동의한 적이 없으면 agreedVersion·agreedAt 이 null 이고 다시 동의가 필요하다")
  void statusWithoutAnyRecord() {
    given(consentRecordRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());

    assertThat(service.getStatus()).isEqualTo(new ConsentStatusResponse("v1", null, null, true));
  }

  @Test
  @DisplayName("동의 상태: 최신 문구에 동의했으면 필요 없고, 옛 문구에만 동의했으면 다시 동의가 필요하다")
  void statusByVersion() {
    latestIs("v1");
    assertThat(service.getStatus())
        .isEqualTo(
            new ConsentStatusResponse("v1", "v1", NOW.minusSeconds(3600).toEpochMilli(), false));

    latestIs("v0");
    ConsentStatusResponse old = service.getStatus();
    assertThat(old.agreedVersion()).isEqualTo("v0");
    assertThat(old.needsConsent()).isTrue();
  }

  @Test
  @DisplayName("다시 동의하기: 옛 문구에 동의했던 사람이 최신 문구에 동의하면 새 줄이 쌓이고, 더는 필요 없다고 알려 준다")
  void agreeAgain() {
    latestIs("v0");

    ConsentStatusResponse response = service.agreeAgain(agree("v1"));

    ArgumentCaptor<ConsentRecord> saved = ArgumentCaptor.forClass(ConsentRecord.class);
    verify(consentRecordRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getAgreedAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getTextVersion()).isEqualTo("v1");
    assertThat(saved.getValue().getAgeConfirmedOver14()).isTrue();
    assertThat(response.needsConsent()).isFalse();
    assertThat(response.agreedVersion()).isEqualTo("v1");
  }

  @Test
  @DisplayName("한 번도 동의한 적이 없는 사람도 다시 동의하기로 동의할 수 있다")
  void agreeAgainWithoutAnyRecord() {
    given(consentRecordRepository.findFirstByUserIdOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());

    assertThat(service.agreeAgain(agree("v1")).needsConsent()).isFalse();
    verify(consentRecordRepository).save(any());
  }

  @Test
  @DisplayName("이미 최신 문구에 동의했으면 409 이고, 같은 줄이 또 쌓이지 않는다")
  void agreeAgainWhenAlreadyCurrent() {
    latestIs("v1");

    assertConflict(() -> service.agreeAgain(agree("v1")), "이미 이 동의 문구에 동의했어요");
    verify(consentRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("다시 동의하기에서 낡은 버전을 보내면 409 이고 저장하지 않는다")
  void agreeAgainWithOldVersion() {
    latestIs("v0");

    assertConflict(() -> service.agreeAgain(agree("v0")), OLD_TEXT_MESSAGE);
    verify(consentRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("동의 상태와 다시 동의하기는 동의 검사가 없는 쪽으로 사용자를 확인하고, 로그인하지 않았으면 401")
  void usesTheLenientCheckAndRequiresLogin() {
    latestIs("v0");
    service.getStatus();
    service.agreeAgain(agree("v1"));
    // 동의 검사가 있는 쪽을 부르면, 동의하지 않은 사람이 동의하러 오는 길이 막혀 버립니다.
    verify(currentUserProvider, never()).getCurrentUserId();

    given(currentUserProvider.getCurrentUserIdWithoutConsent())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    assertThatThrownBy(service::getStatus)
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    assertThatThrownBy(() -> service.agreeAgain(agree("v1")))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }
}
