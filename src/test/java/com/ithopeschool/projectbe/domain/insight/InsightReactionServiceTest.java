package com.ithopeschool.projectbe.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.insight.InsightPicker.Candidate;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.History;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.Picked;
import com.ithopeschool.projectbe.domain.insight.InsightPicker.SelfView;
import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionRequest;
import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionResponse;
import com.ithopeschool.projectbe.domain.insight.dto.InsightStatsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 반응 저장 · 비율 · 다음 선정용 기록 로직만 확인합니다.
class InsightReactionServiceTest {

  private static final Long ME = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final InsightRepository insightRepository = mock(InsightRepository.class);
  private final InsightReactionService service =
      new InsightReactionService(currentUserProvider, insightRepository);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(insightRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private static Insight insight(
      long id, String key, LocalDate shownDate, InsightResponse reaction) {
    Insight insight = new Insight(ME, key + " 문장", key, false, shownDate, List.of(shownDate));
    ReflectionTestUtils.setField(insight, "id", id);
    insight.react(reaction);
    return insight;
  }

  private void expectRejected(Long id, String response, ErrorCode code, String message) {
    assertThatThrownBy(() -> service.react(id, new InsightReactionRequest(response)))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  // ---- 반응 저장 ----

  @Test
  @DisplayName("내 인사이트에 반응을 저장하고, 다시 누르면 바뀐다")
  void reactsAndChanges() {
    Insight mine = insight(3, "k", TODAY, null);
    given(insightRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(mine));

    InsightReactionResponse first = service.react(3L, new InsightReactionRequest("wrong"));
    assertThat(first.id()).isEqualTo("3");
    assertThat(first.response()).isEqualTo("wrong");

    InsightReactionResponse changed = service.react(3L, new InsightReactionRequest("alreadyKnew"));
    assertThat(changed.response()).isEqualTo("alreadyKnew");
    assertThat(mine.getReaction()).isEqualTo(InsightResponse.ALREADY_KNEW);
  }

  @Test
  @DisplayName("반응은 surprised · alreadyKnew · wrong 셋만 받는다 (DB 를 보기 전에 400)")
  void acceptsOnlyThreeValues() {
    String message = "반응은 surprised, alreadyKnew, wrong 중에서 골라 주세요";
    expectRejected(3L, "like", ErrorCode.VALIDATION_ERROR, message);
    expectRejected(3L, "SURPRISED", ErrorCode.VALIDATION_ERROR, message);
    verify(insightRepository, never()).findByIdAndUserId(any(), any());
  }

  @Test
  @DisplayName("없는 인사이트나 남의 인사이트에는 반응할 수 없다 (둘 다 404)")
  void cannotReactToOthers() {
    // 내 번호로 찾아서 안 나오면 없는 것이든 남의 것이든 똑같이 404 입니다.
    given(insightRepository.findByIdAndUserId(99L, ME)).willReturn(Optional.empty());

    expectRejected(99L, "surprised", ErrorCode.NOT_FOUND, "인사이트를 찾을 수 없어요");
  }

  // ---- 비율 ----

  @Test
  @DisplayName("[몰랐는데 맞아] 비율은 반응을 누른 것 중에서 세고, 소수점은 버린다")
  void surprisedPercent() {
    given(insightRepository.countByUserIdAndReactionIsNotNull(ME)).willReturn(3L);
    given(insightRepository.countByUserIdAndReaction(ME, InsightResponse.SURPRISED)).willReturn(1L);

    InsightStatsResponse stats = service.getStats();

    assertThat(stats.reactedCount()).isEqualTo(3);
    assertThat(stats.surprisedCount()).isEqualTo(1);
    assertThat(stats.surprisedPercent()).isEqualTo(33);
  }

  @Test
  @DisplayName("아직 하나도 안 눌렀으면 비율은 0 이 아니라 null 이다")
  void noReactionsYet() {
    given(insightRepository.countByUserIdAndReactionIsNotNull(ME)).willReturn(0L);
    given(insightRepository.countByUserIdAndReaction(ME, InsightResponse.SURPRISED)).willReturn(0L);

    assertThat(service.getStats().surprisedPercent()).isNull();
  }

  // ---- 다음 선정에 반영 (#41) ----

  @Test
  @DisplayName("고른 인사이트를 보여 준 인사이트로 남긴다. 반응은 비어 있고 근거 날짜가 같이 저장된다")
  void recordsShownInsights() {
    Candidate candidate =
        new Candidate(
            "morning-vs-night",
            "오전에 더 오래 집중했어요",
            List.of(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 10)),
            List.of(9, 4),
            40,
            SelfView.DIFFERENT,
            "어려운 과목을 오전에");
    Picked picked = InsightPicker.score(candidate, null, TODAY);

    List<Insight> saved = service.recordShown(ME, List.of(picked), TODAY);

    assertThat(saved).hasSize(1);
    Insight insight = saved.get(0);
    assertThat(insight.getUserId()).isEqualTo(ME);
    assertThat(insight.getInsightKey()).isEqualTo("morning-vs-night");
    assertThat(insight.isHypothesis()).isTrue();
    assertThat(insight.getShownDate()).isEqualTo(TODAY);
    assertThat(insight.getEvidenceDates()).hasSize(2);
    assertThat(insight.getReaction()).isNull();
  }

  @Test
  @DisplayName("종류마다 마지막으로 보여 준 날과 가장 최근에 누른 반응을 기록으로 만든다")
  void historiesForThePicker() {
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(
            List.of(
                // 최근 순서
                insight(4, "morning", LocalDate.of(2026, 10, 15), null),
                insight(3, "sns", LocalDate.of(2026, 10, 10), InsightResponse.WRONG),
                insight(2, "morning", LocalDate.of(2026, 10, 1), InsightResponse.SURPRISED),
                insight(1, "sleep", LocalDate.of(2026, 9, 1), null)));

    List<History> histories = service.historiesOf(ME);

    assertThat(histories)
        .containsExactly(
            // 마지막에 보여 준 10/15 에는 아직 안 눌렀어도, 그 전에 누른 "몰랐는데 맞아" 가 남습니다.
            new History("morning", LocalDate.of(2026, 10, 15), InsightResponse.SURPRISED),
            new History("sns", LocalDate.of(2026, 10, 10), InsightResponse.WRONG),
            new History("sleep", LocalDate.of(2026, 9, 1), null));
  }

  @Test
  @DisplayName("기록을 InsightPicker 에 넘기면 '틀렸어' 를 누른 종류는 다시 고르지 않는다")
  void wrongIsNotPickedAgain() {
    given(insightRepository.findByUserIdOrderByShownDateDescIdDesc(ME))
        .willReturn(List.of(insight(1, "sns", LocalDate.of(2026, 9, 1), InsightResponse.WRONG)));
    Candidate sns =
        new Candidate(
            "sns",
            "SNS 쓴 날은 짧았어요",
            List.of(TODAY),
            List.of(14, 14),
            50,
            SelfView.DIFFERENT,
            "폰 치우기");

    List<Picked> picked = InsightPicker.pick(List.of(sns), service.historiesOf(ME), TODAY);

    assertThat(picked).isEmpty();
  }
}
