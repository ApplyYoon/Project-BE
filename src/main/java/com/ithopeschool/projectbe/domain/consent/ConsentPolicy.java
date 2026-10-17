package com.ithopeschool.projectbe.domain.consent;

// "지금 서비스가 받고 있는 동의 문구의 버전" 을 정해 두는 곳입니다. (이슈 #71)
//
// FE 는 가입 화면(과 다시 동의 화면)에 동의 문구를 보여 주면서, 그 문구의 버전을 서버에 같이 보냅니다.
// 서버는 보낸 버전이 아래 CURRENT_VERSION 과 같을 때만 동의로 받아 줍니다.
// → FE 가 낡은 문구를 보여 주고 받은 동의는 저장되지 않습니다.
//
// 동의 문구를 바꿀 때 (Project#8 문구 검토 뒤 등)
//   1. FE 의 문구와 버전을 바꿉니다.
//   2. 여기 CURRENT_VERSION 을 새 버전으로 바꿉니다. (예: "v1" → "v2")
//   3. 그러면 옛 버전에 동의한 사람은 "최신 문구에 동의하지 않은 사용자" 가 되어, 다시 동의하기 전까지
//      데이터를 저장할 수 없습니다. (POST·PUT·PATCH 가 403 CONSENT_REQUIRED, 보기·지우기는 됩니다)
//   4. FE 는 GET /api/v1/auth/consent 로 다시 동의가 필요한지 보고, POST /api/v1/auth/consent 로 동의를 받습니다.
public final class ConsentPolicy {

  // 지금 받는 동의 문구의 버전. 글자 20자까지 (consent_records.text_version)
  public static final String CURRENT_VERSION = "v1";

  // 값만 다루는 도구라서 객체를 만들 일이 없습니다.
  private ConsentPolicy() {}

  // 보낸 버전이 지금 받는 버전과 같은지 알려줍니다. 비어 있거나 다르면 false 입니다.
  public static boolean isCurrent(String version) {
    return CURRENT_VERSION.equals(version);
  }
}
