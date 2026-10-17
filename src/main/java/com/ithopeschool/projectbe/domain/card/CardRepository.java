package com.ithopeschool.projectbe.domain.card;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// cards 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 카드가 섞이지 않습니다.
public interface CardRepository extends JpaRepository<Card, Long> {

  // 내 카드를 모두, 만든 순서(번호 순서)대로 찾습니다. 없으면 빈 목록입니다.
  List<Card> findByUserIdOrderById(Long userId);

  // 내 카드 중에서 번호로 하나 찾습니다. 없는 번호이거나 남의 카드면 빈 Optional 입니다.
  Optional<Card> findByIdAndUserId(Long id, Long userId);

  // 그 날짜에 "할 수도 있는" 내 카드를 번호 순서로 찾습니다. (후보)
  //   - 1회성 카드(repeatDays 가 비어 있음): 카드 날짜가 그날인 것
  //   - 반복 카드: 반복을 시작한 날짜가 그날이거나 그 전인 것
  // 요일이 맞는지는 여기서 보지 않습니다. 요일은 "1,3,5" 같은 글자라 SQL 로 보기 어려워서,
  // Service 가 후보마다 Card.isOnDate 로 한 번 더 봅니다. (FE 의 isCardOnDate 와 같은 규칙을 자바 한 곳에 두려는 것)
  // 값은 SQL 문자열에 붙이지 않고 :이름 자리에 넘깁니다.
  @Query(
      "select c from Card c"
          + " where c.userId = :userId"
          + " and ((c.repeatDays is null and c.cardDate = :date)"
          + "   or (c.repeatDays is not null and c.cardDate <= :date))"
          + " order by c.id")
  List<Card> findCandidatesOn(@Param("userId") Long userId, @Param("date") LocalDate date);
}
