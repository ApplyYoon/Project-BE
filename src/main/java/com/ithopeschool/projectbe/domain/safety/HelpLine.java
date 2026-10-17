package com.ithopeschool.projectbe.domain.safety;

import java.util.List;

// 힘들 때 도움받을 수 있는 상담 전화입니다. (이슈 #56)
// 안전 브레이크(SafetyBrake)의 showHelp 가 true 면 이 목록을 응답에 담아 바로 안내합니다.
//
// ⚠️ 문구와 연결처는 전문가 검토(Tmax-IT-Hope-School/Project#8) 뒤에 확정합니다.
// FE 의 helpLines (src/lib/helpLines.ts) 와 같은 목록입니다. 바뀌면 양쪽을 같이 고칩니다.
//
// name: 상담 이름 / number: 전화번호 / detail: 번호 아래에 붙이는 한 줄 설명
public record HelpLine(String name, String number, String detail) {

  // 안내할 상담 전화 전체입니다. 번호를 여러 곳에 따로 적지 않고 여기 것만 씁니다.
  public static final List<HelpLine> ALL =
      List.of(
          new HelpLine("청소년상담", "1388", "24시간 · 무료 · 비밀 보장"),
          new HelpLine("자살예방상담", "109", "24시간 · 무료 · 비밀 보장"));
}
