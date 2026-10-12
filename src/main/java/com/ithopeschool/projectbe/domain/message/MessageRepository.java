package com.ithopeschool.projectbe.domain.message;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// messages 테이블의 창구입니다. 메서드 이름만 적으면 Spring 이 이름을 읽고 SQL 을 만들어 줍니다.
public interface MessageRepository extends JpaRepository<Message, Long> {

  // 내 메시지 중에서 번호가 after 보다 크고 before 보다 작은 것을, 번호가 큰 순서(최신순)로 찾습니다.
  // 몇 개까지 가져올지는 pageable 이 정합니다. (한 번에 20개)
  List<Message> findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
      Long userId, Long after, Long before, Pageable pageable);

  // 번호로 내 메시지 하나를 찾습니다. 없거나 다른 사람의 것이면 빈 Optional 입니다.
  Optional<Message> findByIdAndUserId(Long id, Long userId);
}
