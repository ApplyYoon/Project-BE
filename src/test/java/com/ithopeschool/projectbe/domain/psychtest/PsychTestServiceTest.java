package com.ithopeschool.projectbe.domain.psychtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestResponse;
import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 를 가짜로 바꿔서 답 검사와 저장·응답 모양을 확인합니다.
class PsychTestServiceTest {

  private static final Long ME = 7L;
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final PsychTestRepository psychTestRepository = mock(PsychTestRepository.class);
  private final PsychTestService service =
      new PsychTestService(
          currentUserProvider, psychTestRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  // 35개 모두 같은 답
  private static Map<String, Integer> allAnswers(int value) {
    Map<String, Integer> answers = new LinkedHashMap<>();
    for (PsychItem item : PsychItem.values()) {
      answers.put(item.name(), value);
    }
    return answers;
  }

  // 옛 14문항 검사(척도마다 앞의 두 문장)의 답
  private static Map<String, Integer> oldAnswers(int value) {
    Map<String, Integer> answers = new LinkedHashMap<>();
    for (PsychItem item : PsychItem.values()) {
      if (item.isInOldTest()) {
        answers.put(item.name(), value);
      }
    }
    return answers;
  }

  private static void assertBadRequest(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
        .hasMessage(message);
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(psychTestRepository.save(any()))
        .willAnswer(
            invocation -> {
              PsychTest test = invocation.getArgument(0);
              ReflectionTestUtils.setField(test, "id", 3L);
              return test;
            });
  }

  @Test
  @DisplayName("35개 답을 내 번호 · 지금 시각으로 저장하고, 척도 점수와 높낮이를 계산해 돌려준다")
  void saves() {
    Map<String, Integer> answers = allAnswers(4);
    answers.put("S1", 6);
    answers.put("S2", 5);
    answers.put("S3", 6);
    answers.put("S4", 5);
    answers.put("S5", 5);

    PsychTestResponse response = service.save(new PsychTestSaveRequest(answers));

    ArgumentCaptor<PsychTest> saved = ArgumentCaptor.forClass(PsychTest.class);
    verify(psychTestRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(ME);
    assertThat(saved.getValue().getTakenAt()).isEqualTo(NOW);
    assertThat(saved.getValue().getAnswers()).hasSize(35).containsEntry(PsychItem.S1, 6);

    assertThat(response.id()).isEqualTo("3");
    assertThat(response.answers()).hasSize(35).containsEntry("S2", 5);
    // (6 + 5 + 6 + 5 + 5) / 5 = 5.4
    assertThat(response.scores())
        .containsEntry("startResistance", 5.4)
        .containsEntry("planOptimism", 4.0);
    assertThat(response.levels())
        .containsEntry("startResistance", "high")
        .containsEntry("distraction", "mid");
    assertThat(response.scores().keySet())
        .containsExactly(
            "startResistance",
            "distraction",
            "planOptimism",
            "recordBurden",
            "failSensitivity",
            "externalAttribution",
            "carePreference");
  }

  @Test
  @DisplayName("옛 14문항 검사(척도마다 앞의 두 문장)에 모두 답한 결과도 받고, 답한 14개만 돌려준다")
  void acceptsOldTest() {
    Map<String, Integer> answers = oldAnswers(4);
    answers.put("S1", 6);
    answers.put("S2", 5);

    PsychTestResponse response = service.save(new PsychTestSaveRequest(answers));

    assertThat(response.answers()).hasSize(14).doesNotContainKey("S3");
    assertThat(response.scores()).containsEntry("startResistance", 5.5);
  }

  @Test
  @DisplayName("모르는 코드 · 1 ~ 7 밖 · 일부만 답했으면 400 이고 저장하지 않는다")
  void rejectsBadAnswers() {
    Map<String, Integer> unknown = allAnswers(4);
    unknown.put("Z9", 4);
    assertBadRequest(() -> service.save(new PsychTestSaveRequest(unknown)), "모르는 문항이 있어요: Z9");

    Map<String, Integer> outOfRange = allAnswers(4);
    outOfRange.put("A1", 8);
    assertBadRequest(
        () -> service.save(new PsychTestSaveRequest(outOfRange)), "답은 1 ~ 7 사이로 보내 주세요");

    Map<String, Integer> zero = allAnswers(4);
    zero.put("A1", 0);
    assertBadRequest(() -> service.save(new PsychTestSaveRequest(zero)), "답은 1 ~ 7 사이로 보내 주세요");

    Map<String, Integer> nullAnswer = allAnswers(4);
    nullAnswer.put("A1", null);
    assertBadRequest(
        () -> service.save(new PsychTestSaveRequest(nullAnswer)), "답은 1 ~ 7 사이로 보내 주세요");

    String incomplete = "문장 35개(옛 검사는 14개)에 모두 답해 주세요";
    Map<String, Integer> missing = allAnswers(4);
    missing.remove("C5");
    assertBadRequest(() -> service.save(new PsychTestSaveRequest(missing)), incomplete);
    Map<String, Integer> oldMissing = oldAnswers(4);
    oldMissing.remove("C2");
    assertBadRequest(() -> service.save(new PsychTestSaveRequest(oldMissing)), incomplete);
    // 14개지만 옛 검사의 문장이 아닌 것이 섞임
    Map<String, Integer> mixed = oldAnswers(4);
    mixed.remove("C2");
    mixed.put("C3", 4);
    assertBadRequest(() -> service.save(new PsychTestSaveRequest(mixed)), incomplete);

    verify(psychTestRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("가장 최근 결과를 돌려주고, 안 했으면 404 다")
  void latest() {
    Map<PsychItem, Integer> answers = new java.util.EnumMap<>(PsychItem.class);
    for (PsychItem item : PsychItem.values()) {
      answers.put(item, 2);
    }
    PsychTest test = new PsychTest(ME, NOW, answers);
    ReflectionTestUtils.setField(test, "id", 5L);
    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(ME)).willReturn(Optional.of(test));
    assertThat(service.getLatest().levels()).containsEntry("carePreference", "low");

    given(psychTestRepository.findFirstByUserIdOrderByIdDesc(ME)).willReturn(Optional.empty());
    assertThatThrownBy(service::getLatest)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
        .hasMessage("아직 심리검사를 하지 않았어요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이 그대로 나가고 DB 는 건드리지 않는다")
  void loginRequired() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> service.save(new PsychTestSaveRequest(allAnswers(4))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(service::getLatest).isInstanceOf(ApiException.class);
    verifyNoInteractions(psychTestRepository);
  }
}
