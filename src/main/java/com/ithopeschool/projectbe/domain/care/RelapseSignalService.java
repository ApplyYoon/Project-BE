package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 지금 로그인한 사람의 재발 신호를 판정합니다. (이슈 #230, 최종 기획서 「5.2 재발 신호」)
// DB 에서 판정이 끝난 카드 하루치를 받아(CardResultService.getJudgedDays) 순수 함수 RelapseSignal.judge 에 넘깁니다.
//
// 아직 API 주소는 없습니다. 관리 단계 매일 판정(#62, 「5.4」)이 통화 때 이 메서드를 부릅니다.
// "지난 판정 다음 날(since)" 은 그 판정이 기억하는 값이라 부르는 쪽이 넘깁니다.
@Service
@RequiredArgsConstructor
public class RelapseSignalService {

  // 판정에 쓰는 기간(일, 오늘 포함).
  // 최근 7일 + 기준선 14일 = 21일에, "기록이 기준선 첫날 전부터 있었는지" 를 보려고 기준선 앞 14일을 더 읽습니다.
  // (FE 는 가진 기록 전체를 넣습니다. 그래서 "기준선 첫날과 그 앞 14일" 동안 카드가 하나도 없었던 사람만 FE 와 달리 ② 를 판정하지 않습니다)
  static final int LOOKBACK_DAYS =
      RelapseSignal.RECENT_DAYS + RelapseSignal.BASELINE_DAYS + RelapseSignal.BASELINE_DAYS;

  private final CurrentUserProvider currentUserProvider;
  private final CardResultService cardResultService;
  // "오늘이 며칠인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // 오늘(새벽 4시 기준)의 재발 신호. since 는 지난 판정 다음 날입니다. (처음 판정이면 오늘을 넘깁니다)
  @Transactional(readOnly = true)
  public RelapseSignal getRelapseSignal(LocalDate since) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    List<JudgedCardDay> days =
        cardResultService.getJudgedDays(userId, today.minusDays(LOOKBACK_DAYS - 1), today);
    return RelapseSignal.judge(days, today, since);
  }
}
