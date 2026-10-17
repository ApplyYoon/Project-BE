package com.ithopeschool.projectbe.domain.suggestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.suggestion.ImprovementPick.Basis;
import com.ithopeschool.projectbe.domain.suggestion.ImprovementPick.RecentReason;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 개선안 3개 제안(#51)에서 원인과 전략을 고르는 규칙을 확인합니다.
class ImprovementPickTest {

  private static final List<Integer> NO_WEEKDAY = List.of(0, 0, 0, 0, 0, 0, 0);

  private static RecentReason recent(MissReason reason, int days) {
    return new RecentReason(reason, days, NO_WEEKDAY);
  }

  // ---- 원인 고르기 ----

  @Test
  @DisplayName("규칙 1: 최근 30일 원인이 합쳐 3일 이상이면 날 수가 가장 많은 원인을 고르고, 온보딩 답은 보지 않는다")
  void recentFirst() {
    ImprovementPick.Pick pick =
        ImprovementPick.causeOf(
            List.of(recent(MissReason.PHONE, 2), recent(MissReason.TIRED, 1)),
            Set.of(FailureCause.WEEKEND));

    assertThat(pick.cause()).isEqualTo(MissReason.PHONE);
    assertThat(pick.basis()).isEqualTo(Basis.RECENT);
    assertThat(pick.situation()).isEqualTo("최근 30일 중 '폰·SNS' 때문에 못 채운 날이 2일");
  }

  @Test
  @DisplayName("규칙 1: 기타 · 아픔은 세지도 고르지도 않는다")
  void etcAndSickAreSkipped() {
    ImprovementPick.Pick pick =
        ImprovementPick.causeOf(
            List.of(
                recent(MissReason.ETC, 9), recent(MissReason.SICK, 5), recent(MissReason.EVENT, 3)),
            Set.of());
    assertThat(pick.cause()).isEqualTo(MissReason.EVENT);

    // 기타 · 아픔을 빼면 2일뿐이라 기록으로는 못 고르고, 온보딩 답도 없으면 null
    assertThat(
            ImprovementPick.causeOf(
                List.of(
                    recent(MissReason.ETC, 9),
                    recent(MissReason.SICK, 5),
                    recent(MissReason.EVENT, 2)),
                Set.of()))
        .isNull();
  }

  @Test
  @DisplayName("규칙 2: 기록이 모자라면 온보딩 답으로 대신하고, 원인이 분명한 것(폰 → 계획 과다 → 학원 → 주말 → 시험 후)부터 고른다")
  void onboardingFallback() {
    ImprovementPick.Pick pick =
        ImprovementPick.causeOf(
            List.of(recent(MissReason.PHONE, 2)),
            Set.of(FailureCause.AFTER_EXAM, FailureCause.LATE_ACADEMY, FailureCause.WEEKEND));

    assertThat(pick.cause()).isEqualTo(MissReason.TIRED);
    assertThat(pick.basis()).isEqualTo(Basis.ONBOARDING);
    assertThat(pick.situation()).isEqualTo("처음 설문에서 '학원이 늦게 끝난 날' 계획이 무너진다고 했음");
  }

  @Test
  @DisplayName("온보딩 답은 폰 → 폰·SNS, 계획을 너무 많이 → 계획 과다, 학원 → 피곤·수면, 주말 · 시험 후 → 기타 로 바꾼다")
  void onboardingMapping() {
    assertThat(ImprovementPick.missReasonOf(FailureCause.PHONE)).isEqualTo(MissReason.PHONE);
    assertThat(ImprovementPick.missReasonOf(FailureCause.TOO_MUCH_PLAN))
        .isEqualTo(MissReason.TOO_MUCH);
    assertThat(ImprovementPick.missReasonOf(FailureCause.LATE_ACADEMY)).isEqualTo(MissReason.TIRED);
    assertThat(ImprovementPick.missReasonOf(FailureCause.WEEKEND)).isEqualTo(MissReason.ETC);
    assertThat(ImprovementPick.missReasonOf(FailureCause.AFTER_EXAM)).isEqualTo(MissReason.ETC);
  }

  @Test
  @DisplayName("규칙 3: 기록도 온보딩 답도 없으면 null 이다")
  void nothing() {
    assertThat(ImprovementPick.causeOf(List.of(), Set.of())).isNull();
  }

  @Test
  @DisplayName("상황 한 줄: 한 요일이 2일 이상이고 가장 많으면 '주로 ~요일' 을 붙이고, 같은 수의 요일이 있으면 안 붙인다")
  void situationWeekday() {
    assertThat(
            ImprovementPick.recentSituation(
                new RecentReason(MissReason.PHONE, 5, List.of(0, 3, 1, 0, 1, 0, 0))))
        .isEqualTo("최근 30일 중 '폰·SNS' 때문에 못 채운 날이 5일, 주로 화요일");
    assertThat(
            ImprovementPick.recentSituation(
                new RecentReason(MissReason.PHONE, 4, List.of(2, 2, 0, 0, 0, 0, 0))))
        .isEqualTo("최근 30일 중 '폰·SNS' 때문에 못 채운 날이 4일");
    assertThat(
            ImprovementPick.recentSituation(
                new RecentReason(MissReason.TIRED, 3, List.of(1, 1, 1, 0, 0, 0, 0))))
        .isEqualTo("최근 30일 중 '피곤·수면' 때문에 못 채운 날이 3일");
  }

  // ---- 전략 고르기 ----

  @Test
  @DisplayName("쉬움 · 보통 · 강함 하나씩 고르고, 몇 번째인지는 주마다 바뀐다 (1/1 ~ 1/7 은 첫째, 1/8 ~ 1/14 는 둘째)")
  void strategiesRotateByWeek() {
    List<Strategy> firstWeek =
        ImprovementPick.strategiesOf(MissReason.PHONE, LocalDate.parse("2026-01-07"));
    List<Strategy> secondWeek =
        ImprovementPick.strategiesOf(MissReason.PHONE, LocalDate.parse("2026-01-08"));

    assertThat(firstWeek)
        .extracting(Strategy::key)
        .containsExactly("phone-easy-1", "phone-normal-1", "phone-hard-1");
    assertThat(secondWeek)
        .extracting(Strategy::key)
        .containsExactly("phone-easy-2", "phone-normal-2", "phone-hard-2");
    assertThat(ImprovementPick.strategiesOf(MissReason.PHONE, LocalDate.parse("2026-01-15")))
        .isEqualTo(firstWeek);
  }

  @Test
  @DisplayName("전략이 없는 원인(아픔)이면 빈 목록이다")
  void sickHasNoStrategies() {
    assertThat(ImprovementPick.strategiesOf(MissReason.SICK, LocalDate.parse("2026-10-17")))
        .isEmpty();
  }
}
