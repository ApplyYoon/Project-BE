package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.plan.PlanDraft.Raw;
import com.ithopeschool.projectbe.domain.plan.PlanDraft.Raw.Item;
import com.ithopeschool.projectbe.domain.plan.dto.PlanParseRequest;
import com.ithopeschool.projectbe.domain.plan.dto.PlanParseResponse;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
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

// Service 단위 테스트: GPT(PlanParseClient)와 DB 를 가짜로 바꿔서 순서와 규칙만 확인합니다.
// GPT 를 진짜로 부르지 않습니다. 가짜 GPT 가 돌려줄 답을 테스트가 정합니다.
class PlanParseServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate DATE = LocalDate.parse("2026-10-11");
  private static final String TEXT = "내일 학원 끝나고 수학 문제집 2시간, 영어 단어 30분, 그다음 게임 1시간";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
  private final PlanParseClient planParseClient = mock(PlanParseClient.class);
  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final PlanParseService service =
      new PlanParseService(currentUserProvider, subjectRepository, planParseClient, gptUsageGuard);

  private static Subject subject(long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  private void gptAnswers(Raw raw) {
    given(planParseClient.parse(anyString(), anyString(), any())).willReturn(Optional.of(raw));
  }

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(planParseClient.isEnabled()).willReturn(true);
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(3, "수학"), subject(4, "영어")));
  }

  @Test
  @DisplayName("이슈의 예시: 초안 3개에 요청한 날짜를 붙여 돌려준다 (수학 120 · 영어 30 · 놀기 게임 60)")
  void issueExample() {
    gptAnswers(
        new Raw(
            false,
            List.of(
                new Item("3", "문제집", 120, null, "study"),
                new Item("4", "단어", 30, null, "study"),
                new Item(null, "게임", 60, null, "play"))));

    PlanParseResponse response = service.parse(new PlanParseRequest(DATE, TEXT));

    assertThat(response.showHelp()).isFalse();
    assertThat(response.drafts())
        .containsExactly(
            new PlanParseResponse.Draft(DATE, "3", "문제집", 120, null, "study"),
            new PlanParseResponse.Draft(DATE, "4", "단어", 30, null, "study"),
            new PlanParseResponse.Draft(DATE, null, "게임", 60, null, "play"));
  }

  @Test
  @DisplayName("GPT 에게 내 과목 번호와 이름을 같이 주고, 목록에 없는 번호는 비운다")
  void givesMySubjectsAndKeepsOnlyThem() {
    gptAnswers(new Raw(false, List.of(new Item("99", "과학 실험", 40, null, "study"))));

    PlanParseResponse response = service.parse(new PlanParseRequest(DATE, "과학 실험 40분"));

    verify(planParseClient).parse(anyString(), contains("- 3: 수학"), any());
    assertThat(response.drafts().get(0).subjectId()).isNull();
  }

  @Test
  @DisplayName("아무것도 저장하지 않는다 (과목 목록을 읽기만 한다)")
  void savesNothing() {
    gptAnswers(new Raw(false, List.of(new Item("3", "문제집", 120, null, "study"))));

    service.parse(new PlanParseRequest(DATE, TEXT));

    // 이 Service 에는 계획을 저장하는 창구(StudyPlanRepository)가 아예 없습니다. 과목은 목록을 읽기만 합니다.
    verify(subjectRepository).findByUserIdOrderById(USER_ID);
    verifyNoMoreInteractions(subjectRepository);
  }

  @Test
  @DisplayName("문장에 위기 표현이 있으면 GPT 를 부르지 않고, 기록도 남기지 않고 showHelp 를 돌려준다")
  void crisisTextStopsBeforeGpt() {
    PlanParseResponse response = service.parse(new PlanParseRequest(DATE, "오늘은 그냥 죽고 싶다"));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.drafts()).isEmpty();
    verify(planParseClient, never()).parse(anyString(), anyString(), any());
    verifyNoInteractions(gptUsageGuard);
  }

  @Test
  @DisplayName("GPT 가 위기 표현이라고 하면 초안을 버리고 showHelp 를 돌려준다")
  void gptCrisisShowsHelp() {
    gptAnswers(new Raw(true, List.of(new Item("3", "문제집", 120, null, "study"))));

    PlanParseResponse response = service.parse(new PlanParseRequest(DATE, TEXT));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.drafts()).isEmpty();
  }

  @Test
  @DisplayName("GPT 가 꺼져 있으면 503 이다")
  void disabledGptIs503() {
    given(planParseClient.isEnabled()).willReturn(false);

    assertThatThrownBy(() -> service.parse(new PlanParseRequest(DATE, TEXT)))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    verifyNoInteractions(gptUsageGuard);
  }

  @Test
  @DisplayName("GPT 가 실패하거나 시간이 넘으면(빈 값) 빈 목록이 아니라 503 이다")
  void gptFailureIs503() {
    given(planParseClient.parse(anyString(), anyString(), any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.parse(new PlanParseRequest(DATE, TEXT)))
        .isInstanceOf(ApiException.class)
        .hasMessage("계획을 만들지 못했어요. 잠시 후 다시 시도해 주세요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  @DisplayName("범위를 벗어난 줄은 버리고 나머지만 돌려준다")
  void dropsBadLines() {
    gptAnswers(
        new Raw(
            false,
            List.of(
                new Item("3", "문제집", 0, null, "study"),
                new Item("4", "단어", 30, "25:00", "study"),
                new Item("4", "듣기", 30, "15:00", "study"))));

    PlanParseResponse response = service.parse(new PlanParseRequest(DATE, TEXT));

    assertThat(response.drafts())
        .containsExactly(new PlanParseResponse.Draft(DATE, "4", "듣기", 30, "15:00", "study"));
  }
}
