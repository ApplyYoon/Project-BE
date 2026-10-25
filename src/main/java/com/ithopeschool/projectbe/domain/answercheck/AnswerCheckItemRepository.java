package com.ithopeschool.projectbe.domain.answercheck;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// answer_check_items 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 문제는 찾지 못합니다.
public interface AnswerCheckItemRepository extends JpaRepository<AnswerCheckItem, Long> {

  // 내 채점 기록 여러 개의 문제들을 한 번에, 기록 번호 · 사진 속 순서대로.
  // (기록마다 따로 부르면 PC 가 3초마다 부를 때 DB 를 여러 번 불러서 한 번에 꺼냅니다)
  List<AnswerCheckItem> findByUserIdAndAnswerCheckIdInOrderByAnswerCheckIdAscSortOrderAsc(
      Long userId, Collection<Long> answerCheckIds);

  // 내 채점 기록 하나의 문제들, 사진 속 순서대로.
  List<AnswerCheckItem> findByUserIdAndAnswerCheckIdOrderBySortOrderAsc(
      Long userId, Long answerCheckId);

  // 내 채점 기록 하나 안의 문제 하나. 번호가 없거나, 그 기록의 문제가 아니거나, 남의 것이면 빈 Optional 입니다.
  Optional<AnswerCheckItem> findByIdAndAnswerCheckIdAndUserId(
      Long id, Long answerCheckId, Long userId);
}
