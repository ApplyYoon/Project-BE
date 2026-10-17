package com.ithopeschool.projectbe.domain.review;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// reviews 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 회고가 섞이지 않습니다.
public interface ReviewRepository extends JpaRepository<Review, Long> {

  // 내 그 시험의 회고. 아직 안 썼으면 빈 Optional 입니다. (덮어쓸지 새로 만들지 정할 때 씁니다)
  Optional<Review> findByUserIdAndExamId(Long userId, Long examId);

  // 내 그 주(월요일 날짜)의 회고. 아직 안 썼으면 빈 Optional 입니다.
  Optional<Review> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
