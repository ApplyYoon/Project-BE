package com.ithopeschool.projectbe.domain.cardday;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// card_day_marks 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 표시가 섞이지 않습니다.
public interface CardDayMarkRepository extends JpaRepository<CardDayMark, Long> {

  // 이 카드의 이 날짜에 이미 표시(미루기·실패)가 있는지 알려 줍니다.
  boolean existsByUserIdAndCardIdAndMarkDate(Long userId, Long cardId, LocalDate markDate);

  // 이 카드를 from ~ to (두 날짜 모두 포함) 사이의 날짜에서 이 표시로 남긴 횟수. (반복 카드의 "이번 달 미루기 횟수")
  long countByUserIdAndCardIdAndMarkAndMarkDateBetween(
      Long userId, Long cardId, CardDayMarkType mark, LocalDate from, LocalDate to);

  // 이 카드에 이 표시를 남긴 횟수 (기간 상관없이). (1회성 카드의 "지금까지 미루기 횟수")
  long countByUserIdAndCardIdAndMark(Long userId, Long cardId, CardDayMarkType mark);

  // from ~ to (두 날짜 모두 포함) 의 내 표시를 날짜 순, 같은 날이면 번호 순으로 찾습니다. 없으면 빈 목록입니다.
  List<CardDayMark> findByUserIdAndMarkDateBetweenOrderByMarkDateAscIdAsc(
      Long userId, LocalDate from, LocalDate to);
}
