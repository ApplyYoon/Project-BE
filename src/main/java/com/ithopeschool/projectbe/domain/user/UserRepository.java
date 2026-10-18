package com.ithopeschool.projectbe.domain.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// users 테이블의 창구입니다. save·findById 같은 기본 기능은 JpaRepository 가 이미 가지고 있습니다.
public interface UserRepository extends JpaRepository<User, Long> {

  // 온보딩을 마친 사람들, 번호 순서대로 (예약 작업이 모든 학생을 돌 때, 이슈 #62)
  List<User> findByOnboardedAtIsNotNullOrderByIdAsc();
}
