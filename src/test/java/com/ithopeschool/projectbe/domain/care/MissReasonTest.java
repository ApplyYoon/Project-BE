package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 못 한 이유 8가지의 FE 글자와 내부/외부 분류가 FE 와 같은지 확인합니다. (이슈 #225)
// 분류는 FE 의 getCauseKind (src/lib/careStage.ts) 예시와 같습니다.
class MissReasonTest {

  @Test
  @DisplayName("'미루다 보니' 가 더해졌고 FE 글자는 procrastinated 이다")
  void procrastinated() {
    assertThat(MissReason.fromValue("procrastinated")).isEqualTo(MissReason.PROCRASTINATED);
    assertThat(MissReason.PROCRASTINATED.toValue()).isEqualTo("procrastinated");
    assertThat(MissReason.values()).hasSize(8);
  }

  @Test
  @DisplayName("폰·하기 싫음·미루다 보니는 내 쪽, 아픔·행사는 어쩔 수 없음, 계획 과다·피곤은 과부하, 기타는 모름")
  void causeKinds() {
    assertThat(MissReason.PHONE.causeKind()).isEqualTo(CauseKind.INTERNAL);
    assertThat(MissReason.UNWILLING.causeKind()).isEqualTo(CauseKind.INTERNAL);
    assertThat(MissReason.PROCRASTINATED.causeKind()).isEqualTo(CauseKind.INTERNAL);
    assertThat(MissReason.SICK.causeKind()).isEqualTo(CauseKind.UNAVOIDABLE);
    assertThat(MissReason.EVENT.causeKind()).isEqualTo(CauseKind.UNAVOIDABLE);
    assertThat(MissReason.TOO_MUCH.causeKind()).isEqualTo(CauseKind.OVERLOAD);
    assertThat(MissReason.TIRED.causeKind()).isEqualTo(CauseKind.OVERLOAD);
    assertThat(MissReason.ETC.causeKind()).isEqualTo(CauseKind.UNKNOWN);
  }
}
