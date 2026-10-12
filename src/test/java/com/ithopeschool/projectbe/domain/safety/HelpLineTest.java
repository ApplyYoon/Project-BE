package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;

// 도움 연결 안내에 쓰는 상담 전화 목록(이슈 #56)을 확인합니다.
class HelpLineTest {

  @Test
  void hasTheTwoHelpLinesFromTheIssue() {
    // 이슈 #56 에 적힌 두 곳입니다: 청소년상담 1388, 자살예방상담 109
    // 번호가 실수로 바뀌면 위기 상황의 학생이 엉뚱한 곳에 전화하게 되므로 테스트로 묶어 둡니다.
    assertThat(HelpLine.ALL)
        .extracting(HelpLine::name, HelpLine::number)
        .containsExactly(tuple("청소년상담", "1388"), tuple("자살예방상담", "109"));
  }

  @Test
  void everyHelpLineHasADetailLine() {
    // 번호 아래에 붙는 설명이 비어 있지 않습니다.
    assertThat(HelpLine.ALL).allSatisfy(line -> assertThat(line.detail()).isNotBlank());
  }
}
