package com.ithopeschool.projectbe.domain.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// login_tokens 테이블의 창구입니다. save 같은 기본 기능은 JpaRepository 가 이미 가지고 있습니다.
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

  // 토큰의 해시로 토큰을 찾습니다. 없으면 빈 Optional 입니다. (요청에 온 토큰이 진짜인지 확인할 때 씁니다)
  Optional<LoginToken> findByTokenHash(String tokenHash);

  // 이 해시의 토큰을 지웁니다. (로그아웃) 지운 개수를 돌려줍니다.
  long deleteByTokenHash(String tokenHash);

  // 이 사용자의 토큰 중 "이 해시만 빼고" 모두 지웁니다. (비밀번호를 바꾸면 다른 기기의 로그인을 풉니다)
  long deleteByUserIdAndTokenHashNot(Long userId, String tokenHash);

  // 이 사용자의 토큰을 모두 지웁니다. (지금 요청에 토큰이 없을 때의 비밀번호 변경)
  long deleteByUserId(Long userId);
}
