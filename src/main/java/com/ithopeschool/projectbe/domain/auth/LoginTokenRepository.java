package com.ithopeschool.projectbe.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

// login_tokens 테이블의 창구입니다. save 같은 기본 기능은 JpaRepository 가 이미 가지고 있습니다.
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {}
