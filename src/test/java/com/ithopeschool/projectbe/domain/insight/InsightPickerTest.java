package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ithopeschool.projectbe.domain.insight.InsightPicker.Candidate;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.History;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.Picked;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.SelfView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 인사이트를 고르는 규칙을 숫자로 확인합니다. DB 없이 후보와 기록만 넣어 봅니다.
class InsightPickerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  private static final List<LocalDate> SOME_DATES =
      List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));

  // 다른 기준은 같게 두고 한 가지만 바꿔 보기 위한 기본 후보
  private static Candidate candidate(
      String key,
      List<Integer> dayCounts,
      int differencePercent,
      SelfView selfView,
      String action) {
    return new Candidate(
        key, key + " 문장", SOME_DATES, dayCounts, differencePercent, selfView, action);
  }

  // ---- 기준 하나씩 ----

  @Test
  @DisplayName("의외성: 스스로 말한 것과 다르면 100 · 모르면 50 · 같으면 0")
  void surprise() {
    assertThat(InsightPicker.surpriseOf(SelfView.DIFFERENT)).isEqualTo(100);
    assertThat(InsightPicker.surpriseOf(SelfView.UNKNOWN)).isEqualTo(50);
    assertThat(InsightPicker.surpriseOf(SelfView.SAME)).isEqualTo(0);
  }

  @Test
  @DisplayName("확실성: 적은 쪽 일수(14일이면 50점) + 차이(50% 이상이면 50점), 마지막에 한 번만 반올림")
  void certainty() {
    assertThat(InsightPicker.certaintyOf(List.of(14, 20), 50)).isEqualTo(100);
    assertThat(InsightPicker.certaintyOf(List.of(7, 30), 20)).isEqualTo(45);
    // 4일 → 14.28… 점, 차이 80% → 50점 (넘쳐도 50) → 64
    assertThat(InsightPicker.certaintyOf(List.of(4, 10), 80)).isEqualTo(64);
    // 일수도 차이도 없으면 0
    assertThat(InsightPicker.certaintyOf(List.of(), 0)).isEqualTo(0);
    // 30일이 쌓여도 일수 점수는 50 까지
    assertThat(InsightPicker.certaintyOf(List.of(30, 30), 0)).isEqualTo(50);
  }

  @Test
  @DisplayName("새로움: 처음 100 · 말한 지 28일 이상 50 · 28일 안 0")
  void novelty() {
    assertThat(InsightPicker.noveltyOf(null, TODAY)).isEqualTo(100);
    assertThat(InsightPicker.noveltyOf(new History("k", TODAY.minusDays(28), null), TODAY))
        .isEqualTo(50);
    assertThat(InsightPicker.noveltyOf(new History("k", TODAY.minusDays(27), null), TODAY))
        .isEqualTo(0);
  }

  @Test
  @DisplayName("실행 가능성: 해 볼 행동이 붙어 있으면 100 · 없거나 빈 글자면 0")
  void actionability() {
    Candidate withAction = candidate("a", List.of(14, 14), 50, SelfView.SAME, "어려운 과목을 오전에");
    Candidate withoutAction = candidate("b", List.of(14, 14), 50, SelfView.SAME, " ");

    assertThat(InsightPicker.score(withAction, null, TODAY).actionability()).isEqualTo(100);
    assertThat(InsightPicker.score(withoutAction, null, TODAY).actionability()).isEqualTo(0);
  }

  @Test
  @DisplayName("총점 = 의외성 40% + 확실성 30% + 실행 가능성 20% + 새로움 10%")
  void totalUsesTheWeights() {
    // 의외성 100 · 확실성 45 · 실행 100 · 새로움 100 → 40 + 13.5 + 20 + 10 = 83.5 → 84
    Picked picked =
        InsightPicker.score(
            candidate("k", List.of(7, 9), 20, SelfView.DIFFERENT, "행동"), null, TODAY);

    assertThat(picked.surprise()).isEqualTo(100);
    assertThat(picked.certainty()).isEqualTo(45);
    assertThat(picked.total()).isEqualTo(84);
  }

  // ---- 반응 버튼 ----

  @Test
  @DisplayName("지난번에 '몰랐는데 맞아' 를 누른 종류는 총점 +10, 100 은 넘지 않는다")
  void surprisedAddsTen() {
    Candidate same = candidate("k", List.of(7, 9), 20, SelfView.SAME, null);
    // 의외성 0 · 확실성 45 · 실행 0 · 새로움 0 → 13.5 → 14, +10 → 24
    History surprised = new History("k", TODAY.minusDays(3), InsightResponse.SURPRISED);
    assertThat(InsightPicker.score(same, surprised, TODAY).total()).isEqualTo(24);

    Candidate perfect = candidate("p", List.of(14, 14), 50, SelfView.DIFFERENT, "행동");
    // 의외성 100 · 확실성 100 · 실행 100 · 새로움 50 → 95, +10 → 100 (넘지 않음)
    History old = new History("p", TODAY.minusDays(40), InsightResponse.SURPRISED);
    assertThat(InsightPicker.score(perfect, old, TODAY).total()).isEqualTo(100);
  }

  @Test
  @DisplayName("지난번에 '이미 알던 거야' 를 누른 종류는 점수가 그대로다")
  void alreadyKnewKeepsTheScore() {
    Candidate same = candidate("k", List.of(7, 9), 20, SelfView.SAME, null);
    History knew = new History("k", TODAY.minusDays(3), InsightResponse.ALREADY_KNEW);
    History noResponse = new History("k", TODAY.minusDays(3), null);

    assertThat(InsightPicker.score(same, knew, TODAY).total())
        .isEqualTo(InsightPicker.score(same, noResponse, TODAY).total());
  }

  @Test
  @DisplayName("지난번에 '틀렸어' 를 누른 종류는 점수가 높아도 고르지 않는다")
  void wrongIsNeverPicked() {
    Candidate best = candidate("best", List.of(14, 14), 50, SelfView.DIFFERENT, "행동");
    Candidate weak = candidate("weak", List.of(5, 5), 10, SelfView.SAME, null);
    History wrong = new History("best", TODAY.minusDays(60), InsightResponse.WRONG);

    List<Picked> picked = InsightPicker.pick(List.of(best, weak), List.of(wrong), TODAY);

    assertThat(picked).extracting(p -> p.candidate().key()).containsExactly("weak");
  }

  // ---- 그 밖의 규칙 ----

  @Test
  @DisplayName("근거 날짜가 없는 후보는 고르지 않는다")
  void needsEvidenceDates() {
    Candidate noDates =
        new Candidate("k", "문장", List.of(), List.of(14, 14), 50, SelfView.DIFFERENT, "행동");

    assertThat(InsightPicker.pick(List.of(noDates), List.of(), TODAY)).isEmpty();
  }

  @Test
  @DisplayName("비교 조건 중 하나라도 5일 미만이면 가설이다 (빼지는 않는다)")
  void underFiveDaysIsHypothesis() {
    Picked hypothesis =
        InsightPicker.score(
            candidate("k", List.of(6, 4), 50, SelfView.DIFFERENT, "행동"), null, TODAY);
    Picked notHypothesis =
        InsightPicker.score(
            candidate("k", List.of(6, 5), 50, SelfView.DIFFERENT, "행동"), null, TODAY);
    Picked noDays =
        InsightPicker.score(candidate("k", null, 50, SelfView.DIFFERENT, "행동"), null, TODAY);

    assertThat(hypothesis.isHypothesis()).isTrue();
    assertThat(notHypothesis.isHypothesis()).isFalse();
    assertThat(noDays.isHypothesis()).isTrue();
  }

  @Test
  @DisplayName("총점이 높은 순서로 최대 2개. 같으면 확실성이 높은 쪽, 그것도 같으면 종류 이름 순서")
  void picksTopTwoInOrder() {
    Candidate a = candidate("a", List.of(14, 14), 50, SelfView.SAME, null); // 0+30+0+10 = 40
    Candidate b = candidate("b", List.of(14, 14), 50, SelfView.DIFFERENT, null); // 40+30+0+10 = 80
    Candidate c = candidate("c", List.of(14, 14), 50, SelfView.SAME, null); // 40 (a 와 같음)

    List<Picked> picked = InsightPicker.pick(List.of(a, b, c), List.of(), TODAY);

    assertThat(picked).extracting(p -> p.candidate().key()).containsExactly("b", "a");
  }

  // ---- 예시 사용자 (PR 에 붙인 결과) ----

  @Test
  @DisplayName("예시 사용자: 밤형이라고 답한 학생의 후보 5개 중 '오전이 더 잘 됨' 과 'SNS 많이 쓴 날' 을 고른다")
  void exampleStudent() {
    // 온보딩 때 "나는 밤에 집중이 잘 된다" 고 답한 학생
    Candidate morning =
        new Candidate(
            "morning-vs-night",
            "스스로 밤형이라고 했지만, 오전에 공부한 날의 순공이 40% 더 길었어요",
            List.of(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 10)),
            List.of(9, 6),
            40,
            SelfView.DIFFERENT,
            "어려운 과목 하나를 오전에 먼저 해 보기");
    Candidate sns =
        new Candidate(
            "sns-day",
            "SNS 를 30분 넘게 쓴 날은 순공이 60% 짧았어요",
            List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14)),
            List.of(4, 8),
            60,
            SelfView.UNKNOWN,
            "공부 시작 전 30분은 폰을 서랍에 넣기");
    Candidate sleep =
        new Candidate(
            "sleep-6h",
            "6시간보다 적게 잔 다음 날은 순공이 30% 짧았어요",
            List.of(LocalDate.of(2026, 10, 8)),
            List.of(12, 10),
            30,
            SelfView.SAME,
            "12시 전에 자기");
    Candidate weekday =
        new Candidate(
            "monday-drop",
            "월요일에 순공이 가장 짧았어요",
            List.of(LocalDate.of(2026, 10, 13)),
            List.of(3, 14),
            50,
            SelfView.UNKNOWN,
            null);
    Candidate noEvidence =
        new Candidate(
            "friday", "금요일엔 계획을 다 채웠어요", List.of(), List.of(2, 10), 20, SelfView.UNKNOWN, null);

    List<History> histories =
        List.of(
            // 3주 전에 수면 이야기를 했고 "이미 알던 거야" 를 눌렀음
            new History("sleep-6h", TODAY.minusDays(21), InsightResponse.ALREADY_KNEW),
            // 지난달에 월요일 이야기를 했고 "틀렸어" 를 눌렀음
            new History("monday-drop", TODAY.minusDays(35), InsightResponse.WRONG));

    List<Picked> picked =
        InsightPicker.pick(List.of(sleep, weekday, noEvidence, sns, morning), histories, TODAY);

    // morning-vs-night: 의외성 100 · 확실성 61 (21.4 + 40) · 실행 100 · 새로움 100 → 88.3 → 88
    // sns-day        : 의외성  50 · 확실성 64 (14.3 + 50) · 실행 100 · 새로움 100 → 69.3 → 69 (4일이라 가설)
    // sleep-6h       : 의외성   0 · 확실성 66 (35.7 + 30) · 실행 100 · 새로움   0 → 39.8 → 40 (3등이라 빠짐)
    // monday-drop    : "틀렸어" 라 빠짐 / friday: 근거 날짜가 없어 빠짐
    assertThat(picked)
        .extracting(
            p -> p.candidate().key(),
            Picked::surprise,
            Picked::certainty,
            Picked::actionability,
            Picked::novelty,
            Picked::total,
            Picked::isHypothesis)
        .containsExactly(
            tuple("morning-vs-night", 100, 61, 100, 100, 88, false),
            tuple("sns-day", 50, 64, 100, 100, 69, true));
    assertThat(InsightPicker.score(sleep, histories.get(0), TODAY).total()).isEqualTo(40);
  }
}
