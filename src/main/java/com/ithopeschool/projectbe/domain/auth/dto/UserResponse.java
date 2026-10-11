package com.ithopeschool.projectbe.domain.auth.dto;

import com.ithopeschool.projectbe.domain.user.User;

// 로그인한 사람을 FE 에 알려 주는 모양입니다. FE 의 AuthUser({ id, name })와 같습니다.
//   { "id": "1", "name": "김티맥" }
// - id 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - name 은 화면에 보이는 별명(users.nickname)입니다. 실명·이메일은 담지 않습니다.
public record UserResponse(String id, String name) {

  public static UserResponse from(User user) {
    return new UserResponse(String.valueOf(user.getId()), user.getNickname());
  }
}
