package com.ithopeschool.projectbe.domain.cardresult;

import com.ithopeschool.projectbe.domain.card.CardPriority;
import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.LocalDate;

// 판정이 끝난 카드 하루치 하나 (FE src/lib/relapseSignal.ts 의 JudgedCardDay). 이슈 #230
// 재발 신호(RelapseSignal)와 의지력(#231)의 입력입니다. CardResultService.getJudgedDays 가 만듭니다.
// 아직 판정 전(result 가 null)인 날은 넣지 않습니다.
public record JudgedCardDay(
    // 어느 카드인지 (cards.id)
    Long cardId,
    // 어느 날의 카드인지
    LocalDate date,
    // 반복(루틴) 카드인지
    boolean isRoutine,
    // 우선순위 (절대 · 유연)
    CardPriority priority,
    // CardResultJudge.getCardResult 의 결과 (null 이 아님)
    CardResult result,
    // 실행률 (0 ~ 1). 미실행이면 0
    double runRate,
    // 미실행일 때 고른 원인 태그 (실패로 기록할 때 고른 것). 아직 안 골랐으면 null
    MissReason missReason,
    // CardResultJudge.isStartedOnTime 의 값. 시작하지 않았으면 null (의지력의 정시 시작 비율에 씀)
    Boolean startedOnTime,
    // 그 카드 · 그날 체크리스트 달성률 (0 ~ 1). 세션이 없으면 null
    Double checklistRate) {}
