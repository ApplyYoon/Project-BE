package com.ithopeschool.projectbe.global.auth;

import org.springframework.stereotype.Component;

// "지금 요청한 사용자가 누구인지" 알려 주는 곳입니다. 이 프로젝트에서 이 일은 여기 한 곳에서만 합니다.
//
// 지금은 로그인이 없어서(#19, #20 에서 만듦) 누가 불러도 항상 테스트 사용자(1번)를 돌려줍니다.
// 다른 API 는 user_id 가 필요하면 직접 숫자를 쓰지 말고 반드시 getCurrentUserId() 를 부릅니다.
// 그래야 #20 에서 이 메서드 안만 바꾸면 모든 API 가 진짜 로그인 사용자로 바뀝니다.
//
// 사용 예 (Controller 나 Service 에서):
//   private final CurrentUserProvider currentUserProvider;
//   Long userId = currentUserProvider.getCurrentUserId();
@Component
public class CurrentUserProvider {

  // 로그인 전까지 모든 요청이 쓰는 테스트 사용자 번호입니다.
  // users 테이블이 생기면(#3) 이 번호로 테스트 사용자 한 줄을 넣어 둡니다.
  public static final Long TEST_USER_ID = 1L;

  public Long getCurrentUserId() {
    // TODO(#20): 진짜 로그인이 생기면 요청에 담긴 토큰을 보고 사용자 번호를 꺼내도록 바꿉니다.
    return TEST_USER_ID;
  }
}
