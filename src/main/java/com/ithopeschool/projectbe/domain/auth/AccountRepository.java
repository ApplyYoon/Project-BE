package com.ithopeschool.projectbe.domain.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// accounts 테이블의 창구입니다. 메서드 이름만 적으면 Spring 이 이름을 읽고 SQL 을 만들어 줍니다.
public interface AccountRepository extends JpaRepository<Account, Long> {

  // 이메일로 계정을 찾습니다. 없으면 빈 Optional 입니다. (로그인할 때 씁니다)
  Optional<Account> findByEmail(String email);

  // 이 이메일로 가입한 계정이 이미 있는지 알려줍니다. (가입할 때 씁니다)
  boolean existsByEmail(String email);
}
