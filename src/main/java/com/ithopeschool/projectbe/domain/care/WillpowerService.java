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

// 지금 로그인한 사람의 의지력 지수(0 ~ 100, 내부 값)를 구합니다. (이슈 #231, 최종 기획서 「5.3」)
// DB 에서 최근 14일의 판정이 끝난 카드 하루치를 받아(CardResultService.getJudgedDays) 순수 함수 Willpower 에 넘깁니다.
//
// ⚠️ 이 값은 어떤 API 응답에도 넣지 않습니다. 그래서 Controller 가 없고, 관리 단계 판정(#62)만 이 메서드를 부릅니다.
//    (낙인 방지, 6번 오류 · WillpowerNotInResponseTest)
@Service
@RequiredArgsConstructor
public class WillpowerService {

  private final CurrentUserProvider currentUserProvider;
  private final CardResultService cardResultService;
  // "오늘이 며칠인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // 오늘(새벽 4시 기준) 포함 최근 14일로 의지력을 구합니다.
  // scores: 온보딩 심리검사의 세 척도. 검사를 안 했으면 null (그러면 데이터만 씁니다)
  // ⚠️ 서버에는 아직 심리검사 점수가 저장되지 않아서(#219) 부르는 쪽이 넘깁니다. #219 가 저장하면 여기서 읽도록 바꿉니다.
  @Transactional(readOnly = true)
  public double getWillpower(Willpower.TestScores scores) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    List<JudgedCardDay> days =
        cardResultService.getJudgedDays(
            userId, today.minusDays(Willpower.DATA_PERIOD_DAYS - 1), today);
    return Willpower.getWillpower(days, scores);
  }
}
