package com.ithopeschool.projectbe.domain.plan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// 계획을 "한 번 눌러서" 넣는 데 쓰는 계산입니다. (FE 의 "어제 계획 가져오기" · "자주 쓰는 계획", Project-FE#235)
// 학생의 하루는 대부분 비슷해서, 어제와 같은 계획을 매일 다시 적지 않게 하려는 것입니다.
//
//   어제 계획 가져오기   copy       다른 날의 계획을 새 날짜의 계획으로 복사할 재료를 만듦
//   자주 쓰는 계획       frequent   최근 14일 동안 자주 쓴 조합을 많이 쓴 순으로 5개까지 고름
//
// 규칙 — 가져오기 (copy)
//   1. 과목 · 할 일 · 목표 시간 · 시작 시각 · 종류는 그대로, 날짜만 새 날짜로 바꿉니다. 원래 날의 계획은 바꾸지 않습니다.
//   2. 지워진 과목의 계획은 가져오지 않습니다. 노는 계획은 과목이 없어서 과목 검사 없이 가져옵니다.
//   3. 순서는 받은 순서 그대로입니다.
//
// 규칙 — 자주 쓰는 계획 (frequent)
//   4. 과목 · 할 일 · 목표 시간이 모두 같아야 같은 조합입니다. ("단어 50개 30분" 과 "단어 50개 40분" 은 다른 조합)
//   5. 오늘의 13일 전 ~ 오늘 사이 날짜의 계획만 셉니다. (오늘 포함 14일) 앞으로의 계획은 세지 않습니다.
//   6. 지워진 과목의 계획은 뺍니다. 과목이 없는 노는 계획도 뺍니다.
//   7. 많이 쓴 순서입니다. 횟수가 같으면 더 최근에 쓴 조합이 앞에 옵니다.
//      마지막으로 쓴 날짜까지 같으면 계획 목록에서 뒤에 나온 조합이 앞에 옵니다.
//   8. 5개까지만 돌려줍니다.
//
// 예시 — 가져오기 (남아 있는 과목: 수학, 영어)
//   어제 계획 [수학 60분(완료), 영어 30분 19:00, 한국사 40분(과목이 지워짐), 놀기 20분] 을 10/10 으로
//     →  [10/10 수학 60분, 10/10 영어 30분 19:00, 10/10 놀기 20분]
//
// 예시 — 자주 쓰는 계획 (오늘 10/10)
//   10/8 수학 문제집 60분, 10/9 수학 문제집 60분, 10/9 영어 단어 30분, 9/20 국어 비문학 50분(14일보다 전)
//     →  [수학 문제집 60분 (2번), 영어 단어 30분 (1번)]
//
// 이 함수들은 계산만 합니다. 가져온 계획을 저장하는 일은 부르는 쪽이 합니다.
// (저장할 때 번호는 DB 가 만들고, 새 계획은 항상 "아직 안 함" 으로 시작합니다. 루틴이 만든 계획도 직접 만든 계획으로 가져옵니다)
//
// FE 의 copyPlans · getFrequentPlans (src/lib/planShortcuts.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버가 고르는 계획이 어긋나지 않습니다.
public final class PlanShortcuts {

  // 자주 쓰는 계획을 찾을 때 며칠 전까지 볼지 (오늘 포함)
  public static final int FREQUENT_LOOKBACK_DAYS = 14;

  // 자주 쓰는 계획을 몇 개까지 돌려줄지
  public static final int FREQUENT_MAX_COUNT = 5;

  // 계획 하나에서 이 계산에 쓰는 값만 모은 것입니다.
  // date: 계획한 날짜 / subjectId: 과목 번호. 노는 계획이면 null
  // content: 할 일 내용 / targetMinutes: 목표 시간(분)
  // startTime: 시작할 시각. 안 정했으면 null / kind: 공부 계획인지 노는 계획인지
  public record PlanItem(
      LocalDate date,
      Long subjectId,
      String content,
      int targetMinutes,
      LocalTime startTime,
      PlanKind kind) {}

  // 자주 쓰는 계획 하나입니다. 과목 · 할 일 · 목표 시간이 모두 같은 계획을 한 묶음으로 셉니다.
  // count: 최근 14일 동안 이 조합을 몇 번 썼는지
  public record FrequentPlan(Long subjectId, String content, int targetMinutes, int count) {}

  // 조합 하나를 세는 통입니다. 이 파일 안에서만 씁니다.
  // count: 지금까지 센 횟수 / lastDate: 이 조합을 마지막으로 쓴 날짜
  private static final class Usage {
    private final Long subjectId;
    private final String content;
    private final int targetMinutes;
    private int count;
    private LocalDate lastDate;

    private Usage(PlanItem plan) {
      this.subjectId = plan.subjectId();
      this.content = plan.content();
      this.targetMinutes = plan.targetMinutes();
      this.count = 1;
      this.lastDate = plan.date();
    }

    // 이 계획이 같은 조합이면 true 입니다. (규칙 4)
    private boolean isSameAs(PlanItem plan) {
      return subjectId.equals(plan.subjectId())
          && Objects.equals(content, plan.content())
          && targetMinutes == plan.targetMinutes();
    }
  }

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private PlanShortcuts() {}

  // 다른 날의 계획을 targetDate 로 가져올 때 쓸 새 계획의 재료를 만듭니다. (규칙 1 ~ 3)
  //
  // sourcePlans: 가져올 날의 계획들 (예: 어제 계획)
  // targetDate: 계획을 넣을 날짜
  // validSubjectIds: 지금 남아 있는 내 과목의 번호들
  public static List<PlanItem> copy(
      List<PlanItem> sourcePlans, LocalDate targetDate, Collection<Long> validSubjectIds) {
    List<PlanItem> copies = new ArrayList<>();
    for (PlanItem plan : sourcePlans) {
      // 규칙 2: 노는 계획은 과목이 없어서 과목 검사를 하지 않고 그대로 가져옵니다.
      boolean isPlay = plan.kind() == PlanKind.PLAY;
      if (!isPlay && !hasValidSubject(plan, validSubjectIds)) {
        continue;
      }
      // 규칙 1: 날짜만 바꿉니다. 종류가 비어 있던 계획은 공부 계획으로 가져옵니다.
      copies.add(
          new PlanItem(
              targetDate,
              plan.subjectId(),
              plan.content(),
              plan.targetMinutes(),
              plan.startTime(),
              isPlay ? PlanKind.PLAY : PlanKind.STUDY));
    }
    return copies;
  }

  // 최근 14일(오늘 포함)의 계획에서 자주 쓴 조합을 많이 쓴 순으로 5개까지 고릅니다. (규칙 4 ~ 8)
  //
  // allPlans: 내 계획들. 기간 밖의 계획이 섞여 있어도 됩니다
  // today: 오늘 날짜 (하루의 기준인 새벽 4시로 정한 날짜, DayRule)
  // validSubjectIds: 지금 남아 있는 내 과목의 번호들
  public static List<FrequentPlan> frequent(
      List<PlanItem> allPlans, LocalDate today, Collection<Long> validSubjectIds) {
    // 규칙 5: 오늘이 10/10 이면 9/27 부터입니다.
    LocalDate from = today.minusDays(FREQUENT_LOOKBACK_DAYS - 1);

    // 조합마다 몇 번 썼는지, 마지막으로 쓴 날짜가 언제인지 모읍니다. 처음 나온 순서대로 담깁니다.
    List<Usage> usages = new ArrayList<>();
    for (PlanItem plan : allPlans) {
      if (plan.date().isBefore(from) || plan.date().isAfter(today)) {
        continue;
      }
      // 규칙 6
      if (!hasValidSubject(plan, validSubjectIds)) {
        continue;
      }

      Usage usage = findUsage(usages, plan);
      if (usage == null) {
        usages.add(new Usage(plan));
      } else {
        usage.count++;
        if (plan.date().isAfter(usage.lastDate)) {
          usage.lastDate = plan.date();
        }
      }
    }

    // 규칙 7: sort 는 값이 같으면 원래 순서를 지킵니다.
    // 그래서 먼저 목록을 뒤집어 두면, 횟수와 날짜가 모두 같은 조합끼리는 뒤에 나온 것이 앞에 옵니다.
    Collections.reverse(usages);
    usages.sort(
        (a, b) -> {
          if (a.count != b.count) {
            // 많이 쓴 조합이 앞
            return b.count - a.count;
          }
          // 횟수가 같으면 더 최근에 쓴 조합이 앞
          return b.lastDate.compareTo(a.lastDate);
        });

    // 규칙 8
    List<FrequentPlan> frequentPlans = new ArrayList<>();
    for (Usage usage : usages) {
      if (frequentPlans.size() == FREQUENT_MAX_COUNT) {
        break;
      }
      frequentPlans.add(
          new FrequentPlan(usage.subjectId, usage.content, usage.targetMinutes, usage.count));
    }
    return frequentPlans;
  }

  // 계획의 과목이 지금 남아 있는 과목이면 true 입니다. 과목이 없는 계획(null)은 false 입니다.
  private static boolean hasValidSubject(PlanItem plan, Collection<Long> validSubjectIds) {
    return plan.subjectId() != null && validSubjectIds.contains(plan.subjectId());
  }

  // 이미 세고 있는 조합 중에서 이 계획과 같은 조합을 찾습니다. 없으면 null 입니다.
  private static Usage findUsage(List<Usage> usages, PlanItem plan) {
    for (Usage usage : usages) {
      if (usage.isSameAs(plan)) {
        return usage;
      }
    }
    return null;
  }
}
