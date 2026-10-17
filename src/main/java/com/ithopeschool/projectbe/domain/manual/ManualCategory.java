package com.ithopeschool.projectbe.domain.manual;

// "나 사용설명서" 항목의 분류입니다. (이슈 #46)
// FE 의 ManualItemCategory('rhythm' | 'cautionDay' | 'effectiveMethod' | 'careStyle') 와 같은 4개입니다.
public enum ManualCategory {
  // 리듬 (예: 오전 집중형)
  RHYTHM,
  // 주의할 날 (예: 약속 다음 날 주의)
  CAUTION_DAY,
  // 효과 있는 방법 (예: 폰을 다른 방에 두면 효과 있음)
  EFFECTIVE_METHOD,
  // 잘 맞는 관리 방식 (예: 독촉에 잘 반응)
  CARE_STYLE
}
