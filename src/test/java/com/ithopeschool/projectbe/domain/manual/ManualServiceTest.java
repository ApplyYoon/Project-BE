package com.ithopeschool.projectbe.domain.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRule;
import com.ithopeschool.projectbe.domain.experiment.ExperimentService;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStatus;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStrategy;
import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.insight.InsightRepository;
import com.ithopeschool.projectbe.domain.insight.InsightResponse;
import com.ithopeschool.projectbe.domain.manual.dto.ManualItemResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: 실험 · 인사이트 창구를 가짜로 바꿔서, 사용설명서에 무엇이 어떤 순서로 올라오는지만 확인합니다.
class ManualServiceTest {

  private static final Long ME = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final ExperimentService experimentService = mock(ExperimentService.class);
  private final InsightRepository insightRepository = mock(InsightRepository.class);
  private final ManualHiddenItemRepository manualHiddenItemRepository =
      mock(ManualHiddenItemRepository.class);
  private final ManualService service =
      new ManualService(
          currentUserProvider, experimentService, insightRepository, manualHiddenItemRepository);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(experimentService.getEffectiveExperiments(ME)).willReturn(List.of());
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME)).willReturn(List.of());
    given(manualHiddenItemRepository.findByUserId(ME)).willReturn(List.of());
  }

  // 효과 있음으로 판정된 실험: 2026-10-15 낮에 판정, 기준선 50% → 실험 70%
  private static Experiment effectiveExperiment(long id) {
    Experiment experiment =
        new Experiment(
            ME,
            "폰 때문에 놓친다",
            null,
            ExperimentStrategy.PHONE_BAG,
            Set.of(12L),
            LocalDate.of(2026, 10, 1),
            10,
            Instant.parse("2026-10-01T00:00:00Z"));
    experiment.judge(
        new ExperimentRule.Judgement(ExperimentStatus.EFFECTIVE, 0.5, 0.7),
        Instant.parse("2026-10-15T03:00:00Z"));
    ReflectionTestUtils.setField(experiment, "id", id);
    return experiment;
  }

  private static Insight insight(
      String key, boolean hypothesis, InsightResponse reaction, LocalDate shownDate) {
    Insight insight =
        new Insight(ME, key + " 문장", key, hypothesis, shownDate, List.of(shownDate.minusDays(3)));
    insight.react(reaction);
    return insight;
  }

  @Test
  @DisplayName("아무것도 없으면 빈 배열이다")
  void emptyWhenNothingIsVerified() {
    assertThat(service.list()).isEmpty();
  }

  @Test
  @DisplayName("효과 있음 실험은 전략 규칙을 제목으로, 실행률 변화를 근거로 하는 '효과 있는 방법' 이 된다")
  void effectiveExperimentBecomesEffectiveMethod() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3)));

    assertThat(service.list())
        .containsExactly(
            new ManualItemResponse(
                "experiment-3",
                "effectiveMethod",
                "시작할 때 폰을 가방에 넣기",
                LocalDate.of(2026, 10, 15),
                "14일 실험: 카드 실행률 50% → 70% (+20%p)"));
  }

  @Test
  @DisplayName("가설인 인사이트 · 분류를 모르는 종류 · 틀렸어를 누른 종류는 올리지 않는다")
  void onlyCertainKnownInsightsAreListed() {
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(
            List.of(
                // 올라가는 것
                insight("morning-vs-night", false, null, LocalDate.of(2026, 10, 16)),
                // 가설(5일 미만)
                insight("monday-drop", true, null, LocalDate.of(2026, 10, 15)),
                // 사용설명서 분류가 정해지지 않은 종류
                insight("sns-day", false, null, LocalDate.of(2026, 10, 14))));

    List<ManualItemResponse> items = service.list();

    assertThat(items).hasSize(1);
    assertThat(items.get(0).id()).isEqualTo("insight-morning-vs-night");
    assertThat(items.get(0).category()).isEqualTo("rhythm");
    assertThat(items.get(0).evidence()).isEqualTo("근거 1일 (10/13 ~ 10/13)");
  }

  @Test
  @DisplayName("한 번이라도 틀렸어를 누른 종류는 다른 날 보여 준 것까지 통째로 뺀다")
  void wrongInsightRemovesTheWholeKind() {
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(
            List.of(
                insight("monday-drop", false, null, LocalDate.of(2026, 10, 16)),
                insight("monday-drop", false, InsightResponse.WRONG, LocalDate.of(2026, 9, 1))));

    assertThat(service.list()).isEmpty();
  }

  @Test
  @DisplayName("같은 종류를 여러 번 보여 줬어도 가장 최근 한 줄만 올라가고, id 는 종류로 정해진다")
  void sameKindIsListedOnce() {
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(
            List.of(
                insight("morning-vs-night", false, null, LocalDate.of(2026, 10, 16)),
                insight(
                    "morning-vs-night",
                    false,
                    InsightResponse.SURPRISED,
                    LocalDate.of(2026, 9, 1))));

    List<ManualItemResponse> items = service.list();

    assertThat(items).hasSize(1);
    assertThat(items.get(0).id()).isEqualTo("insight-morning-vs-night");
    assertThat(items.get(0).confirmedDate()).isEqualTo(LocalDate.of(2026, 10, 16));
  }

  @Test
  @DisplayName("실험과 인사이트를 섞어서 최근에 확인된 것이 앞에 오고, 같은 날이면 id 순서다")
  void newestFirst() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3))); // 10/15
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(
            List.of(
                insight("morning-vs-night", false, null, LocalDate.of(2026, 10, 16)),
                insight("monday-drop", false, null, LocalDate.of(2026, 10, 15))));

    List<String> ids = service.list().stream().map(ManualItemResponse::id).toList();

    // 10/16 인사이트 → 10/15 에 확인된 두 개(experiment-3 이 insight-monday-drop 보다 앞)
    assertThat(ids)
        .containsExactly("insight-morning-vs-night", "experiment-3", "insight-monday-drop");
  }

  @Test
  @DisplayName("내 번호로만 실험과 인사이트를 꺼낸다")
  void usesOnlyMyUserId() {
    service.list();

    verify(currentUserProvider).getCurrentUserId();
    verify(experimentService).getEffectiveExperiments(ME);
    verify(insightRepository).findByUserIdOrderByShownDateDescIdDesc(ME);
    verify(manualHiddenItemRepository).findByUserId(ME);
  }

  // ---- 숨기기 ----

  @Test
  @DisplayName("내가 숨긴 항목은 목록에서 빠지고, 나머지는 그대로 남는다")
  void hiddenItemsAreLeftOut() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3)));
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(List.of(insight("morning-vs-night", false, null, LocalDate.of(2026, 10, 16))));
    given(manualHiddenItemRepository.findByUserId(ME))
        .willReturn(List.of(new ManualHiddenItem(ME, "experiment-3")));

    assertThat(service.list())
        .extracting(ManualItemResponse::id)
        .containsExactly("insight-morning-vs-night");
  }

  @Test
  @DisplayName("내 목록에 있는 항목을 숨기면 내 번호와 이름표로 한 줄을 저장한다")
  void hideSavesOneRow() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3)));

    service.hide("experiment-3");

    ArgumentCaptor<ManualHiddenItem> saved = ArgumentCaptor.forClass(ManualHiddenItem.class);
    verify(manualHiddenItemRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(ME);
    assertThat(saved.getValue().getItemId()).isEqualTo("experiment-3");
  }

  @Test
  @DisplayName("이미 숨긴 항목을 또 숨겨도 에러 없이 끝나고 줄을 더 만들지 않는다")
  void hidingTwiceKeepsOneRow() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3)));
    given(manualHiddenItemRepository.existsByUserIdAndItemId(ME, "experiment-3")).willReturn(true);

    service.hide("experiment-3");

    verify(manualHiddenItemRepository, never()).save(any());
  }

  @Test
  @DisplayName("내 목록에 없는 이름표는 404 이고 아무것도 저장하지 않는다")
  void hidingUnknownItemIsNotFound() {
    given(experimentService.getEffectiveExperiments(ME))
        .willReturn(List.of(effectiveExperiment(3)));

    assertThatThrownBy(() -> service.hide("experiment-999"))
        .isInstanceOf(ApiException.class)
        .hasMessage("사용설명서 항목을 찾을 수 없어요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(manualHiddenItemRepository, never()).save(any());
  }
}
