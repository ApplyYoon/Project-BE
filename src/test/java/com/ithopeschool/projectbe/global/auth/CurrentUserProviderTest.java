package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. new 로 직접 만들어서 결과만 확인합니다.
class CurrentUserProviderTest {

  @Test
  void returnsTestUserBeforeLogin() {
    CurrentUserProvider currentUserProvider = new CurrentUserProvider();

    // 로그인이 생기기 전(#20)에는 누가 불러도 테스트 사용자 번호가 나와야 합니다.
    assertThat(currentUserProvider.getCurrentUserId()).isEqualTo(CurrentUserProvider.TEST_USER_ID);
  }
}
