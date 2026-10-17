package com.ithopeschool.projectbe.domain.focus;

import java.util.List;
import java.util.regex.Pattern;

// 집중 모드(앱 잠금)의 규칙입니다. (이슈 #67, 기획서 v2 5장 「채찍 상세」 강제적 채찍의 1단계: 마찰형)
// 집중 모드 중에 다른 화면으로 가려면 10초를 기다린 뒤 해제 이유를 한 줄 적어야 합니다.
// 해제 기록은 벌이 아니라 "어떤 시간대에 약한지" 보여 주는 데이터입니다.
//
//   잠금 허용 켬 + 관리 강도 최대 한도 4   →  집중 모드를 쓸 수 있음
//   잠금 허용 켬 + 최대 한도 3            →  쓸 수 없음 (강제적 채찍까지 허용하지 않음)
//   잠금 허용 끔 + 최대 한도 4            →  쓸 수 없음 (잠금을 허용하지 않음)
//
// 쓸 수 없는 사용자의 해제 기록은 받지 않습니다. 안전 브레이크가 켜지면 설정과 상관없이 집중 모드를 풉니다.
//
// FE 의 canUseFocusMode · isValidUnlockReason · getAutoUnlockReason (src/lib/focusMode.ts) 과 같은
// 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 판단이 어긋나지 않습니다.
public final class FocusMode {

  // 해제 화면에서 이유를 쓰기 전에 기다리는 시간(초)
  public static final int UNLOCK_WAIT_SECONDS = 10;

  // 해제 이유의 최대 글자 수 (한 줄)
  public static final int UNLOCK_REASON_MAX_LENGTH = 50;

  // 해제 화면의 "빠르게 고르기" 버튼에 있는 이유들
  public static final List<String> QUICK_UNLOCK_REASONS = List.of("급한 연락", "쉬는 시간", "그냥 하기 싫어");

  // 집중 모드를 쓰려면 관리 강도 최대 한도가 이 단계여야 합니다. (4 = 강제적 채찍)
  public static final int REQUIRED_MAX_CARE_LEVEL = 4;

  // 글의 앞뒤에 붙은 빈칸들입니다. 띄어쓰기·탭·줄바꿈과, 전각 공백(U+3000) 같은 특수 빈칸을 모두 봅니다.
  // (자바의 \s 는 특수 빈칸을 보지 않아서, FE(자바스크립트)의 trim 과 똑같이 떼려고 그 글자들을 직접 더 적었습니다)
  private static final Pattern EDGE_SPACES =
      Pattern.compile(
          "^[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+"
              + "|[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+$");

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private FocusMode() {}

  // 집중 모드를 쓸 수 있는 사용자인지 알려줍니다.
  // "집중 모드 잠금 허용" 을 켰고, 관리 강도 최대 한도가 4(강제적 채찍)일 때만 true 입니다.
  //
  // allowFocusMode: 설정의 "집중 모드 잠금 허용" / maxCareLevel: 설정의 관리 강도 최대 한도 (0~4)
  public static boolean canUse(boolean allowFocusMode, int maxCareLevel) {
    return allowFocusMode && maxCareLevel == REQUIRED_MAX_CARE_LEVEL;
  }

  // 해제 이유로 받아도 되는 글인지 알려줍니다. 앞뒤 빈칸을 뺀 글자가 1자 이상, 50자 이하여야 합니다.
  //
  //   "엄마가 불러서"  →  true
  //   "   "           →  false  (빈칸만 있음)
  public static boolean isValidUnlockReason(String text) {
    if (text == null) {
      return false;
    }
    String trimmed = EDGE_SPACES.matcher(text).replaceAll("");
    return !trimmed.isEmpty() && trimmed.length() <= UNLOCK_REASON_MAX_LENGTH;
  }

  // 집중 모드를 자동으로 풀어야 하는 이유를 돌려줍니다. 풀 필요가 없으면 null 입니다.
  // 안전 브레이크가 설정보다 먼저입니다.
  //
  //   쓸 수 있음, 브레이크 꺼짐  →  null
  //   쓸 수 있음, 브레이크 켜짐  →  "지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요."
  //   쓸 수 없음, 브레이크 꺼짐  →  "설정에서 집중 모드 잠금을 꺼서 집중 모드가 풀렸어요."
  //
  // canUse: canUse 함수의 결과 / isSafetyBrakeOn: 안전 브레이크가 켜졌는지 (SafetyBrake 의 stopWhip 또는 recoveryMode)
  public static String autoUnlockReason(boolean canUse, boolean isSafetyBrakeOn) {
    if (isSafetyBrakeOn) {
      return "지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요.";
    }
    if (!canUse) {
      return "설정에서 집중 모드 잠금을 꺼서 집중 모드가 풀렸어요.";
    }
    return null;
  }
}
