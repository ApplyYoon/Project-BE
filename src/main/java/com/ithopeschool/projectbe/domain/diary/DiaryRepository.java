package com.ithopeschool.projectbe.domain.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// diaries 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 일기는 찾지도 지우지도 못합니다.
public interface DiaryRepository extends JpaRepository<Diary, Long> {

  // 내 일기 하나. 번호가 없거나 다른 사람의 일기면 빈 Optional 입니다. (둘 다 404 로 답합니다)
  Optional<Diary> findByIdAndUserId(Long id, Long userId);

  // 내 from ~ to (둘 다 포함) 사이의 일기들, 최근에 쓴 것부터.
  List<Diary> findByUserIdAndDiaryDateBetweenOrderByWrittenAtDesc(
      Long userId, LocalDate from, LocalDate to);
}
