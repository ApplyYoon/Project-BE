package com.ithopeschool.projectbe.domain.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// login_tokens 테이블의 창구입니다. save 같은 기본 기능은 JpaRepository 가 이미 가지고 있습니다.
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

  // 토큰의 해시로 토큰을 찾습니다. 없으면 빈 Optional 입니다. (요청에 온 토큰이 진짜인지 확인할 때 씁니다)
  Optional<LoginToken> findByTokenHash(String tokenHash);
}
