package com.ithopeschool.projectbe.domain.consent;

import com.ithopeschool.projectbe.domain.consent.dto.ConsentAgreeRequest;
import com.ithopeschool.projectbe.domain.consent.dto.ConsentStatusResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 개인정보 동의 기록을 남기고, 내 동의 상태를 알려 주는 로직입니다. (이슈 #71, docs/API.md "동의")
//
//   가입할 때   : AuthService 가 recordAtSignup 을 불러서 동의 기록 한 줄을 남깁니다.
//   동의 상태   : 내 가장 최근 동의가 지금 받는 문구 버전인지 알려 줍니다.
//   다시 동의   : 문구가 바뀌었을 때 새 줄을 더합니다. 옛 기록은 지우지 않습니다. (이력)
//
// 이 안의 두 API(동의 상태 · 다시 동의)는 동의하지 않은 사람도 써야 해서, 동의 검사가 없는
// getCurrentUserIdWithoutConsent() 로 누구인지만 확인합니다. 로그인하지 않은 요청은 똑같이 401 입니다.
@Service
@RequiredArgsConstructor
public class ConsentService {

  private final CurrentUserProvider currentUserProvider;
  private final ConsentRecordRepository consentRecordRepository;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 보낸 동의 문구 버전이 지금 받는 버전이 아니면 409 입니다.
  // FE 가 바뀌기 전의 낡은 문구를 보여 주고 받은 동의를 저장하지 않으려는 검사입니다.
  public void checkVersion(String version) {
    if (!ConsentPolicy.isCurrent(version)) {
      throw new ApiException(ErrorCode.CONFLICT, "동의 문구가 바뀌었어요. 화면을 새로 고쳐서 최신 문구에 동의해 주세요");
    }
  }

  // 가입할 때 동의 기록을 남깁니다. 가입 화면에서 만 14세 이상이라고 확인했으므로 ageConfirmedOver14 는 true 입니다.
  // AuthService 의 가입 과정(트랜잭션) 안에서 불립니다. 계정을 만드는 데 실패하면 이 기록도 같이 취소됩니다.
  @Transactional
  public void recordAtSignup(Long userId, String version) {
    checkVersion(version);
    consentRecordRepository.save(new ConsentRecord(userId, Instant.now(clock), version, true));
  }

  // 내 동의 상태. 한 번도 동의한 적이 없으면 agreedVersion 이 null 이고 needsConsent 가 true 입니다.
  @Transactional(readOnly = true)
  public ConsentStatusResponse getStatus() {
    Long userId = currentUserProvider.getCurrentUserIdWithoutConsent();
    return ConsentStatusResponse.of(
        consentRecordRepository.findFirstByUserIdOrderByIdDesc(userId).orElse(null));
  }

  // 다시 동의하기: 새 줄을 더합니다. 이미 지금 받는 문구에 동의한 사람이 또 보내면 409 입니다.
  // (같은 문구에 두 번 동의한 줄이 쌓이지 않게 합니다. 버튼을 두 번 눌렀을 때 등)
  @Transactional
  public ConsentStatusResponse agreeAgain(ConsentAgreeRequest request) {
    Long userId = currentUserProvider.getCurrentUserIdWithoutConsent();
    checkVersion(request.version());

    ConsentRecord latest =
        consentRecordRepository.findFirstByUserIdOrderByIdDesc(userId).orElse(null);
    if (latest != null && ConsentPolicy.isCurrent(latest.getTextVersion())) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 이 동의 문구에 동의했어요");
    }

    ConsentRecord saved =
        consentRecordRepository.save(
            new ConsentRecord(userId, Instant.now(clock), request.version(), true));
    return ConsentStatusResponse.of(saved);
  }
}
