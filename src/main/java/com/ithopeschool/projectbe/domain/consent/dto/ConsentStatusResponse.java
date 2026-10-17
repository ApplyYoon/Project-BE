package com.ithopeschool.projectbe.domain.consent.dto;

import com.ithopeschool.projectbe.domain.consent.ConsentPolicy;
import com.ithopeschool.projectbe.domain.consent.ConsentRecord;

// "내 동의 상태" 의 응답 모양입니다. 로그인한 본인에게만 돌려줍니다.
//   { "currentVersion": "v1", "agreedVersion": "v1", "agreedAt": 1792204469467, "needsConsent":
// false }
// - currentVersion : 서버가 지금 받고 있는 동의 문구의 버전
// - agreedVersion  : 내가 마지막으로 동의한 문구의 버전. 동의한 적이 없으면 null
// - agreedAt       : 내가 마지막으로 동의한 시각(밀리초 숫자). 동의한 적이 없으면 null
// - needsConsent   : true 면 최신 문구에 다시 동의해야 합니다. (그 전에는 데이터를 저장할 수 없습니다)
public record ConsentStatusResponse(
    String currentVersion, String agreedVersion, Long agreedAt, boolean needsConsent) {

  // latest: 내 가장 최근 동의 기록. 한 번도 동의한 적이 없으면 null
  public static ConsentStatusResponse of(ConsentRecord latest) {
    if (latest == null) {
      return new ConsentStatusResponse(ConsentPolicy.CURRENT_VERSION, null, null, true);
    }
    return new ConsentStatusResponse(
        ConsentPolicy.CURRENT_VERSION,
        latest.getTextVersion(),
        latest.getAgreedAt().toEpochMilli(),
        !ConsentPolicy.isCurrent(latest.getTextVersion()));
  }
}
