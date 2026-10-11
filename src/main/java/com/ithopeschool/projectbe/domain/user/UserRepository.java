package com.ithopeschool.projectbe.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

// users 테이블의 창구입니다. save·findById 같은 기본 기능은 JpaRepository 가 이미 가지고 있습니다.
public interface UserRepository extends JpaRepository<User, Long> {}
