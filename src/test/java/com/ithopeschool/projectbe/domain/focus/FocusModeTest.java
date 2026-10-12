package com.ithopeschool.projectbe.domain.focus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 집중 모드의 규칙(이슈 #67)을 확인합니다.
// feExample 로 시작하는 3개는 FE 함수(src/lib/focusMode.ts) 주석의 예시를 그대로 옮긴 것입니다.
class FocusModeTest {

  // ─── canUse: 집중 모드를 쓸 수 있는 사용자인지 ───

  @Test
  void feExampleCanUseFocusMode() {
    // FE 예) { allowFocusMode: true, maxCareLevel: 4 } → true
    assertThat(FocusMode.canUse(true, 4)).isTrue();
    // FE 예) { allowFocusMode: true, maxCareLevel: 3 } → false  (강제적 채찍까지 허용하지 않음)
    assertThat(FocusMode.canUse(true, 3)).isFalse();
    // FE 예) { allowFocusMode: false, maxCareLevel: 4 } → false  (잠금을 허용하지 않음)
    assertThat(FocusMode.canUse(false, 4)).isFalse();
  }

  @Test
  void onlyTheHighestLimitWithPermissionCanUse() {
    // 한도 0 ~ 4 와 허용 여부를 모두 넣어 봐도, "허용 + 한도 4" 하나만 쓸 수 있습니다.
    for (int maxCareLevel = 0; maxCareLevel <= 4; maxCareLevel++) {
      assertThat(FocusMode.canUse(false, maxCareLevel)).as("허용 끔, 한도 %d", maxCareLevel).isFalse();
      assertThat(FocusMode.canUse(true, maxCareLevel))
          .as("허용 켬, 한도 %d", maxCareLevel)
          .isEqualTo(maxCareLevel == 4);
    }
  }

  // ─── isValidUnlockReason: 해제 이유 ───

  @Test
  void feExampleUnlockReason() {
    // FE 예) '엄마가 불러서' → true   /   '   ' → false  (빈칸만 있음)
    assertThat(FocusMode.isValidUnlockReason("엄마가 불러서")).isTrue();
    assertThat(FocusMode.isValidUnlockReason("   ")).isFalse();
  }

  @Test
  void emptyReasonIsNotAccepted() {
    // 비어 있거나, 줄바꿈·탭·전각 공백 같은 빈칸만 있으면 받지 않습니다. 값이 없어도(null) 받지 않습니다.
    assertThat(FocusMode.isValidUnlockReason("")).isFalse();
    assertThat(FocusMode.isValidUnlockReason("\n\t ")).isFalse();
    assertThat(FocusMode.isValidUnlockReason("　 ")).isFalse();
    assertThat(FocusMode.isValidUnlockReason(null)).isFalse();
  }

  @Test
  void reasonCanBeUpToFiftyCharacters() {
    // 경계값: 50자까지 받고, 51자부터 받지 않습니다.
    assertThat(FocusMode.isValidUnlockReason("가".repeat(50))).isTrue();
    assertThat(FocusMode.isValidUnlockReason("가".repeat(51))).isFalse();
    assertThat(FocusMode.isValidUnlockReason("가")).isTrue();
  }

  @Test
  void spacesAroundTheReasonAreNotCounted() {
    // 글자 수는 앞뒤 빈칸을 뺀 뒤에 셉니다. 50자 앞뒤에 빈칸이 붙어 있어도 받습니다.
    assertThat(FocusMode.isValidUnlockReason("   " + "가".repeat(50) + "  \n")).isTrue();
    // 가운데 띄어쓰기는 글자로 셉니다. 25자 + 띄어쓰기 + 25자 = 51자
    assertThat(FocusMode.isValidUnlockReason("가".repeat(25) + " " + "나".repeat(25))).isFalse();
  }

  @Test
  void quickReasonsAreAllValid() {
    // "빠르게 고르기" 버튼의 이유들은 모두 받을 수 있는 글이어야 합니다.
    assertThat(FocusMode.QUICK_UNLOCK_REASONS)
        .containsExactly("급한 연락", "쉬는 시간", "그냥 하기 싫어")
        .allSatisfy(reason -> assertThat(FocusMode.isValidUnlockReason(reason)).isTrue());
  }

  // ─── autoUnlockReason: 자동으로 풀어야 하는지 ───

  @Test
  void feExampleAutoUnlockReason() {
    // FE 예) { canUse: true, isSafetyBrakeOn: false } → null
    assertThat(FocusMode.autoUnlockReason(true, false)).isNull();
    // FE 예) { canUse: true, isSafetyBrakeOn: true } → '지금은 쉬어 가는 게 좋아서 ...'
    assertThat(FocusMode.autoUnlockReason(true, true)).isEqualTo("지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요.");
    // FE 예) { canUse: false, isSafetyBrakeOn: false } → '설정에서 ...'
    assertThat(FocusMode.autoUnlockReason(false, false))
        .isEqualTo("설정에서 집중 모드 잠금을 꺼서 집중 모드가 풀렸어요.");
  }

  @Test
  void safetyBrakeComesBeforeTheSetting() {
    // 안전 브레이크가 설정보다 먼저입니다. 둘 다 해당하면 안전 브레이크의 문장이 나옵니다.
    assertThat(FocusMode.autoUnlockReason(false, true)).isEqualTo("지금은 쉬어 가는 게 좋아서 집중 모드를 풀었어요.");
  }

  @Test
  void autoUnlockReasonsCanBeSavedAsUnlockReasons() {
    // 자동으로 풀릴 때의 안내 문구도 해제 기록의 이유 칸에 그대로 저장할 수 있는 길이입니다.
    assertThat(FocusMode.isValidUnlockReason(FocusMode.autoUnlockReason(true, true))).isTrue();
    assertThat(FocusMode.isValidUnlockReason(FocusMode.autoUnlockReason(false, false))).isTrue();
  }

  @Test
  void waitTimeIsTenSeconds() {
    // 마찰형 잠금: 해제 이유를 쓰기 전에 10초를 기다립니다.
    assertThat(FocusMode.UNLOCK_WAIT_SECONDS).isEqualTo(10);
  }
}
