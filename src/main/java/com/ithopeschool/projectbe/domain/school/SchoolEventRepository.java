package com.ithopeschool.projectbe.domain.school;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// school_events 테이블의 창구입니다.
public interface SchoolEventRepository extends JpaRepository<SchoolEvent, Long> {

  // 한 학교의 from ~ to 일정 (날짜 순서)
  List<SchoolEvent> findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
      String schoolCode, LocalDate from, LocalDate to);

  // 새로 받기 전에 한 학교의 from ~ to 일정을 지웁니다. (받은 것으로 통째로 바꾸려고)
  @Modifying
  @Query(
      "delete from SchoolEvent e where e.schoolCode = :schoolCode and e.eventDate between :from and :to")
  void deleteRange(String schoolCode, LocalDate from, LocalDate to);
}
