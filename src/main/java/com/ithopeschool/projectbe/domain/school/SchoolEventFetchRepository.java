package com.ithopeschool.projectbe.domain.school;

import org.springframework.data.jpa.repository.JpaRepository;

// school_event_fetches 테이블의 창구입니다. (학교 번호로 찾음)
public interface SchoolEventFetchRepository extends JpaRepository<SchoolEventFetch, String> {}
