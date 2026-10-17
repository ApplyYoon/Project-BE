package com.ithopeschool.projectbe.domain.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// accounts 테이블의 창구입니다. 메서드 이름만 적으면 Spring 이 이름을 읽고 SQL 을 만들어 줍니다.
public interface AccountRepository extends JpaRepository<Account, Long> {

  // 이메일로 계정을 찾습니다. 없으면 빈 Optional 입니다. (로그인할 때 씁니다)
  Optional<Account> findByEmail(String email);

  // 이 이메일로 가입한 계정이 이미 있는지 알려줍니다. (가입할 때 씁니다)
  boolean existsByEmail(String email);

  // 구글 고유 번호로 계정을 찾습니다. 없으면 빈 Optional 입니다. (구글로 시작할 때 씁니다, 이슈 #19)
  Optional<Account> findByGoogleSubject(String googleSubject);

  // 이 구글 계정으로 가입한 계정이 이미 있는지 알려줍니다. (구글로 가입할 때 씁니다)
  boolean existsByGoogleSubject(String googleSubject);

  // 사용자 번호로 계정을 찾습니다. 없으면 빈 Optional 입니다. (내 정보를 보거나 고칠 때 씁니다)
  Optional<Account> findByUserId(Long userId);

  // 학생들이 고른 학교 번호들 (겹치지 않게, 고르지 않은 계정은 빼고). 학사일정 예약 작업이 씁니다. (이슈 #282)
  @Query(
      "select distinct a.schoolCode from Account a where a.schoolCode is not null and a.schoolCode <> ''")
  List<String> findDistinctSchoolCodes();
}
