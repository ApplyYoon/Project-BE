package com.ithopeschool.projectbe.domain.reflection;

import com.ithopeschool.projectbe.domain.call.CallSummary;
import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 성찰일기를 처방할지, 한다면 어느 이유의 질문 세트로 할지 정합니다. (이슈 #61 "어떤 상황에서 처방할지")
// 기획서 v2 2장: "성찰일기는 AI 가 처방할 때만, 정해진 기간에 정해진 질문으로 쓴다."
//
// 위에서부터 차례로 따지고, 먼저 걸리는 줄에서 끝냅니다.
//
//   0) 안전 브레이크(최근 7일 위기 표현 · 기분 저하 6일 연속)   →  처방 안 함  (ReflectionService 가 이 규칙보다 먼저 봅니다)
//   1) 지난 처방이 끝난 지 7일이 안 지남                      →  처방 안 함  (쉬는 기간)
//   2) 최근 7일 중 같은 "못 채운 이유" 가 3일 이상             →  그 이유의 질문 세트로 5일 처방
//      그런 이유가 여럿이면 날 수가 많은 것, 날 수도 같으면 MissReason 에 먼저 적힌 것
//   3) 그 밖                                                 →  처방 안 함
//
//   최근 7일 중 '하기 싫음' 3일                     →  하기 싫음 세트, 5일
//   최근 7일 중 '폰·SNS' 2일 · '하기 싫음' 2일       →  처방 안 함  (3일이 된 이유가 없음)
//   최근 7일 중 '아픔' 4일                          →  처방 안 함  (아플 때는 쉬는 게 먼저)
//   지난 처방이 3일 전에 끝남 + '폰·SNS' 5일          →  처방 안 함  (쉬는 기간)
//
// "같은 이유가 며칠" 은 날 수로 셉니다. 같은 날 같은 이유가 여러 번(체크인 + 타이머를 그만둔 이유) 나와도 하루입니다.
// 이 숫자들(7일 · 3일 · 5일 · 쉬는 7일)은 이슈에 정해진 값이 없어서 정했습니다. FE 의 예시 처방
// ("최근 '하기 싫음'이 3번 나와서, 5일 동안 짧게 돌아보기로 했어") 에 맞췄습니다. 팀에서 바꿀 수 있습니다.
public final class ReflectionRule {

  // 이유를 살펴볼 기간(일). 오늘을 넣어 7일입니다.
  public static final int LOOKBACK_DAYS = 7;

  // 같은 이유가 이 날 수 이상이면 처방합니다.
  public static final int MIN_REASON_DAYS = 3;

  // 처방 기간(일). 처방한 날이 첫날입니다.
  public static final int PRESCRIPTION_DAYS = 5;

  // 처방이 끝난 뒤 쉬는 기간(일). 이 기간에는 새로 처방하지 않습니다. (매주 숙제처럼 되지 않게)
  public static final int REST_DAYS = 7;

  // 어느 날 계획을 못 채운(또는 타이머를 목표 전에 그만둔) 이유 하나. date 는 "그 이유로 못 한 날" 입니다.
  public record ReasonFact(LocalDate date, MissReason reason) {}

  // 처방하기로 했을 때의 결과. reason: 어느 이유의 세트인지 / reasonDays: 최근 7일 중 그 이유가 나온 날 수
  public record Decision(MissReason reason, int reasonDays) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ReflectionRule() {}

  // 새로 처방하면 Decision, 하지 않으면 null 을 돌려줍니다. (위 표의 1 ~ 3)
  //
  // today: 오늘 (새벽 4시 기준, DayRule)
  // facts: 최근의 이유들. 기간 밖의 것이 섞여 있어도 됩니다. (여기서 걸러 냅니다)
  // lastEndDate: 지난 처방의 마지막 날. 처방을 받은 적이 없으면 null
  public static Decision decide(LocalDate today, List<ReasonFact> facts, LocalDate lastEndDate) {
    // 1) 지난 처방이 끝난 다음 날부터 7일은 쉽니다. (처방이 아직 안 끝났을 때도 여기서 걸립니다)
    if (lastEndDate != null && !today.isAfter(lastEndDate.plusDays(REST_DAYS))) {
      return null;
    }

    // 이유마다 "그 이유가 나온 날짜들" 을 모읍니다. Set 이라서 같은 날은 한 번만 들어갑니다.
    LocalDate from = today.minusDays(LOOKBACK_DAYS - 1);
    Map<MissReason, Set<LocalDate>> datesByReason = new EnumMap<>(MissReason.class);
    for (ReasonFact fact : facts) {
      if (fact.date().isBefore(from) || fact.date().isAfter(today)) {
        continue;
      }
      datesByReason.computeIfAbsent(fact.reason(), reason -> new HashSet<>()).add(fact.date());
    }

    // 2) MissReason 순서대로 보면서, 날 수가 "더 많을 때만" 바꿉니다. 그래서 날 수가 같으면 앞의 것이 남습니다.
    Decision best = null;
    for (MissReason reason : MissReason.values()) {
      // 질문 세트가 없는 이유(아픔)는 처방하지 않습니다.
      if (ReflectionQuestions.of(reason).isEmpty()) {
        continue;
      }
      int days = datesByReason.getOrDefault(reason, Set.of()).size();
      if (days >= MIN_REASON_DAYS && (best == null || days > best.reasonDays())) {
        best = new Decision(reason, days);
      }
    }
    return best;
  }

  // 학생에게 보여 줄 "왜 처방했는지" 한 줄입니다. FE 예시 처방과 같은 반말입니다.
  //   ('하기 싫음', 3일, 5일)  ->  "최근 일주일 동안 '하기 싫음' 때문에 못 채운 날이 3일 있었어. 5일 동안 짧게 돌아보자."
  // '기타' 는 "'기타' 때문에" 가 어색해서 "다른 이유로" 라고 씁니다.
  public static String reasonSentence(MissReason reason, int reasonDays, int prescriptionDays) {
    String cause =
        reason == MissReason.ETC ? "다른 이유로" : "'" + CallSummary.missReasonLabel(reason) + "' 때문에";
    return "최근 일주일 동안 "
        + cause
        + " 못 채운 날이 "
        + reasonDays
        + "일 있었어. "
        + prescriptionDays
        + "일 동안 짧게 돌아보자.";
  }
}
