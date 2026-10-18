package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// robots.txt 규칙 테스트: 어느 로봇에게라도 막은 경로는 우리도 가지 않는지 봅니다.
class RobotsRulesTest {

  // 분당고(2026-10-18)와 같은 모양: 이름 있는 로봇들에게만 몇 곳을 막아 둠
  private static final String SCHOOL_ROBOTS =
      """
      User-agent: Yeti
      User-agent: Googlebot
      User-Agent: Daumoa
      User-agent: NaverBot
      Disallow: /editor/
      Disallow: /kcase/
      Disallow: /upload/
      Disallow: /dext5editordata/
      """;

  @Test
  @DisplayName("다른 로봇에게 막은 경로도 우리는 가지 않고, 게시판 목록은 간다")
  void followsAnyGroup() {
    RobotsRules rules = RobotsRules.parse(SCHOOL_ROBOTS);

    assertThat(rules.allows("/upload/letter.hwp")).isFalse();
    assertThat(rules.allows("/editor/a.js")).isFalse();
    assertThat(rules.allows("/bundang-h/main.do")).isTrue();
    assertThat(rules.allows("/bundang-h/na/ntt/selectNttList.do?mi=20164&bbsId=15687")).isTrue();
  }

  @Test
  @DisplayName("Disallow: / 가 있으면 아무 데도 가지 않는다")
  void blocksEverything() {
    RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /\n");

    assertThat(rules.allows("/")).isFalse();
    assertThat(rules.allows("/bundang-h/main.do")).isFalse();
  }

  @Test
  @DisplayName("빈 Disallow 는 막는 것이 없다는 뜻이고, '#' 뒤의 설명은 무시한다")
  void emptyDisallowAndComments() {
    RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow:\nDisallow: /admin/ # 관리자\n");

    assertThat(rules.allows("/main.do")).isTrue();
    assertThat(rules.allows("/admin/login.do")).isFalse();
  }

  @Test
  @DisplayName("'*' 는 아무 글자, 끝의 '$' 는 거기서 끝")
  void wildcard() {
    RobotsRules rules =
        RobotsRules.parse("User-agent: *\nDisallow: /*.hwp$\nDisallow: /na/*/print\n");

    assertThat(rules.allows("/upload/letter.hwp")).isFalse();
    assertThat(rules.allows("/upload/letter.hwp?x=1")).isTrue();
    assertThat(rules.allows("/na/ntt/print.do")).isFalse();
    assertThat(rules.allows("/na/ntt/selectNttList.do")).isTrue();
  }

  @Test
  @DisplayName("robots.txt 가 없으면 모두 허용, 못 읽었으면 모두 막음")
  void allowAllAndDenyAll() {
    assertThat(RobotsRules.allowAll().allows("/anything")).isTrue();
    assertThat(RobotsRules.denyAll().allows("/anything")).isFalse();
  }
}
