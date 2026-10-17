package com.ithopeschool.projectbe.domain.bias;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 최근 통화에서 "자주 보이는 생각 습관" 하나입니다. (이슈 #29, 기록 화면의 "내 생각 습관")
//
// ■ 규칙
//   1. 오늘을 넣어 최근 14일 동안의 통화만 봅니다.
//   2. 그 습관이 보인 "서로 다른 날" 이 3일 이상이면 자주 보이는 습관입니다.
//      하루에 통화를 여러 번 해서 같은 날 여러 번 보인 것은 하루로 셉니다. 그날 하루의 기분일 수 있어서입니다.
//      한두 번은 우연일 수 있어서, 학생에게 "습관" 이라고 말하려면 근거가 쌓여야 합니다.
//   3. 보인 날이 많은 것부터, 같으면 더 최근에 보인 것부터 늘어놓습니다.
//
// 예시 (오늘 10월 14일)
//   계획 오류가 10/13 · 10/10 · 10/5 에 보임          →  자주 보이는 습관 (3일)
//   상황 탓하기가 10/13 에 두 번, 10/12 에 한 번 보임   →  아님 (서로 다른 날은 2일)
//   미래의 나 믿기가 9/28 · 10/2 · 10/9 에 보임         →  아님 (9/28 은 14일 밖이라 2일)
//
// bias: 습관 / days: 보인 서로 다른 날의 수 / lastDate: 가장 최근에 보인 날
// lastEvidence: 근거가 남아 있는 가장 최근 통화의 근거 한 줄. 하나도 없으면 null
public record BiasHabit(CognitiveBias bias, int days, LocalDate lastDate, String lastEvidence) {

  // 볼 기간(일). 오늘을 넣어 최근 2주
  public static final int WINDOW_DAYS = 14;
  // 서로 다른 날이 이만큼 이상이어야 자주 보이는 습관입니다.
  public static final int MIN_DAYS = 3;

  // 통화 한 번에서 습관이 보인 것. date: 통화한 날 / evidence: 근거 한 줄. 없으면 null
  public record Sighting(LocalDate date, CognitiveBias bias, String evidence) {}

  // 자주 보이는 습관들을 고릅니다. sightings 의 순서는 상관없습니다. 없으면 빈 목록입니다.
  public static List<BiasHabit> of(List<Sighting> sightings, LocalDate today) {
    LocalDate from = today.minusDays(WINDOW_DAYS - 1);
    // 습관마다 보인 날짜들 (Set 이라 같은 날은 한 번만 들어갑니다)
    Map<CognitiveBias, Set<LocalDate>> daysByBias = new EnumMap<>(CognitiveBias.class);
    // 습관마다 근거가 있는 가장 최근 것
    Map<CognitiveBias, Sighting> latestWithEvidence = new EnumMap<>(CognitiveBias.class);

    for (Sighting sighting : sightings) {
      LocalDate date = sighting.date();
      if (sighting.bias() == null || date.isBefore(from) || date.isAfter(today)) {
        continue;
      }
      daysByBias.computeIfAbsent(sighting.bias(), bias -> new HashSet<>()).add(date);
      if (sighting.evidence() != null) {
        Sighting latest = latestWithEvidence.get(sighting.bias());
        if (latest == null || date.isAfter(latest.date())) {
          latestWithEvidence.put(sighting.bias(), sighting);
        }
      }
    }

    List<BiasHabit> habits = new ArrayList<>();
    for (Map.Entry<CognitiveBias, Set<LocalDate>> entry : daysByBias.entrySet()) {
      Set<LocalDate> days = entry.getValue();
      if (days.size() < MIN_DAYS) {
        continue;
      }
      Sighting latest = latestWithEvidence.get(entry.getKey());
      habits.add(
          new BiasHabit(
              entry.getKey(),
              days.size(),
              days.stream().max(Comparator.naturalOrder()).orElseThrow(),
              latest == null ? null : latest.evidence()));
    }
    // 보인 날이 많은 것부터, 같으면 더 최근에 보인 것부터
    habits.sort(
        Comparator.comparingInt(BiasHabit::days)
            .reversed()
            .thenComparing(BiasHabit::lastDate, Comparator.reverseOrder()));
    return habits;
  }
}
