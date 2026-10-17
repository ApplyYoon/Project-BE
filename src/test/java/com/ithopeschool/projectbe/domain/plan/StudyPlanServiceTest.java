package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanBatchCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanCreateRequest;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanUpdateRequest;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// 계획 로직의 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에서 내 것만 나오는지는 StudyPlanRepositoryTest 가 확인합니다.
class StudyPlanServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;
  // 내 과목 번호 (가짜 DB 에 "내 것" 으로 들어 있음)
  private static final Long MY_SUBJECT = 3L;
  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

  private CurrentUserProvider currentUserProvider;
  private StudyPlanRepository studyPlanRepository;
  private SubjectRepository subjectRepository;
  private StudyPlanService studyPlanService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    studyPlanRepository = mock(StudyPlanRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    studyPlanService =
        new StudyPlanService(
            currentUserProvider,
            studyPlanRepository,
            subjectRepository,
            mock(com.ithopeschool.projectbe.domain.routine.RoutineService.class));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 3번은 내 과목이고, 그 밖의 번호는 없는 과목이거나 남의 과목입니다.
    given(subjectRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(subjectRepository.findByIdAndUserId(MY_SUBJECT, ME))
        .willReturn(Optional.of(new Subject(ME, "수학", SubjectColor.BLUE)));
    // 저장하면 DB 가 번호를 매겨 준 것처럼 돌려줍니다.
    given(studyPlanRepository.save(any()))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 100L));
    given(studyPlanRepository.saveAll(anyList()))
        .willAnswer(
            invocation -> {
              List<StudyPlan> plans = invocation.getArgument(0);
              for (int i = 0; i < plans.size(); i++) {
                withId(plans.get(i), 100L + i);
              }
              return plans;
            });
  }

  private static StudyPlan withId(StudyPlan plan, long id) {
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }

  // DB 에 이미 있는 내 공부 계획 (시간 미정)
  private static StudyPlan plan(long id, long subjectId, String content, int targetMinutes) {
    return withId(new StudyPlan(ME, subjectId, OCT_5, content, targetMinutes), id);
  }

  private static StudyPlanCreateRequest create(
      String subjectId, String content, String startTime, String kind) {
    return new StudyPlanCreateRequest(OCT_5, subjectId, content, 60, startTime, kind);
  }

  private static StudyPlanUpdateRequest update(
      String subjectId,
      String content,
      Integer targetMinutes,
      Boolean done,
      String startTime,
      String kind) {
    return new StudyPlanUpdateRequest(subjectId, content, targetMinutes, done, startTime, kind);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인하지 않았으면 다섯 가지 모두 401 이고, DB 는 건드리지 않는다")
  void everythingRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    String message = "로그인이 필요해요";

    assertError(() -> studyPlanService.getPlans(OCT_5), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () -> studyPlanService.create(create("3", "수학", null, null)),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(
        () ->
            studyPlanService.createAll(
                new StudyPlanBatchCreateRequest(List.of(create("3", "수학", null, null)))),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(
        () -> studyPlanService.update(7L, update(null, null, null, true, null, null)),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(() -> studyPlanService.delete(7L), ErrorCode.UNAUTHORIZED, message);
    verifyNoInteractions(studyPlanRepository);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("로그인한 내 번호와 보낸 날짜로 묻고, FE 모양으로 바꿔 돌려준다")
  void getPlans() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, OCT_5))
        .willReturn(List.of(plan(7, 3, "수학 문제집 3단원 풀기", 90)));

    List<StudyPlanResponse> result = studyPlanService.getPlans(OCT_5);

    verify(studyPlanRepository).findByUserIdAndPlanDateOrderById(ME, OCT_5);
    assertThat(result)
        .containsExactly(
            new StudyPlanResponse(
                "7", OCT_5, "3", "수학 문제집 3단원 풀기", 90, false, null, "study", null));
  }

  @Test
  @DisplayName("목록은 시작 시각이 있는 것 먼저 시각 순, 시간 미정은 뒤에 만든 순서")
  void getPlansOrder() {
    StudyPlan noTimeFirst = plan(1, 3, "시간 미정 1", 30);
    StudyPlan evening = plan(2, 3, "저녁 7시", 30);
    evening.changeStartTime(LocalTime.of(19, 0));
    StudyPlan noTimeSecond = plan(3, 3, "시간 미정 2", 30);
    StudyPlan morning = plan(4, 3, "아침 9시", 30);
    morning.changeStartTime(LocalTime.of(9, 0));
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, OCT_5))
        .willReturn(List.of(noTimeFirst, evening, noTimeSecond, morning));

    assertThat(studyPlanService.getPlans(OCT_5))
        .extracting(StudyPlanResponse::content)
        .containsExactly("아침 9시", "저녁 7시", "시간 미정 1", "시간 미정 2");
  }

  @Test
  @DisplayName("계획이 없는 날은 빈 목록")
  void emptyDay() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, OCT_5)).willReturn(List.of());

    assertThat(studyPlanService.getPlans(OCT_5)).isEmpty();
  }

  // ---- 추가 ----

  @Test
  @DisplayName("공부 계획을 추가하면 내 번호로 저장하고, 시작 시각·종류를 FE 모양으로 돌려준다")
  void createStudyPlan() {
    StudyPlanResponse result = studyPlanService.create(create("3", "수학 3단원", "19:00", "study"));

    ArgumentCaptor<StudyPlan> saved = ArgumentCaptor.forClass(StudyPlan.class);
    verify(studyPlanRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(ME);
    assertThat(saved.getValue().getSubjectId()).isEqualTo(MY_SUBJECT);
    assertThat(saved.getValue().getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(result)
        .isEqualTo(
            new StudyPlanResponse("100", OCT_5, "3", "수학 3단원", 60, false, "19:00", "study", null));
  }

  @Test
  @DisplayName("종류와 시작 시각을 보내지 않으면 공부 계획 · 시간 미정")
  void createDefaults() {
    StudyPlanResponse result = studyPlanService.create(create("3", "수학", null, null));

    assertThat(result.kind()).isEqualTo("study");
    assertThat(result.startTime()).isNull();
  }

  @Test
  @DisplayName("노는 계획은 과목 없이 추가되고, 과목을 보내도 비워서 저장한다")
  void createPlayPlan() {
    StudyPlanResponse withoutSubject = studyPlanService.create(create("", "농구", null, "play"));
    StudyPlanResponse withSubject = studyPlanService.create(create("3", "게임", null, "play"));

    assertThat(withoutSubject.subjectId()).isEmpty();
    assertThat(withoutSubject.kind()).isEqualTo("play");
    assertThat(withSubject.subjectId()).isEmpty();
    // 노는 계획은 과목을 확인하지 않습니다.
    verifyNoInteractions(subjectRepository);
  }

  @Test
  @DisplayName("공부 계획인데 과목이 없으면 400 이고 저장하지 않는다")
  void studyPlanNeedsSubject() {
    assertError(
        () -> studyPlanService.create(create(null, "수학", null, "study")),
        ErrorCode.VALIDATION_ERROR,
        "공부 계획은 과목을 골라 주세요");
    assertError(
        () -> studyPlanService.create(create("", "수학", null, null)),
        ErrorCode.VALIDATION_ERROR,
        "공부 계획은 과목을 골라 주세요");
    verify(studyPlanRepository, never()).save(any());
  }

  @Test
  @DisplayName("내 과목이 아닌 과목 번호(남의 것·없는 것)는 404 이고 저장하지 않는다")
  void rejectsOthersSubject() {
    assertError(
        () -> studyPlanService.create(create("99", "수학", null, null)),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    verify(subjectRepository).findByIdAndUserId(99L, ME);
    verify(studyPlanRepository, never()).save(any());
  }

  @Test
  @DisplayName("종류가 study·play 가 아니면 400")
  void rejectsWrongKind() {
    assertError(
        () -> studyPlanService.create(create("3", "수학", null, "STUDY")),
        ErrorCode.VALIDATION_ERROR,
        "계획 종류는 study, play 중에서 골라 주세요");
  }

  // ---- 여러 개 추가 ----

  @Test
  @DisplayName("여러 개를 보낸 순서대로 한 번에 저장한다")
  void createAll() {
    List<StudyPlanResponse> result =
        studyPlanService.createAll(
            new StudyPlanBatchCreateRequest(
                List.of(create("3", "수학", null, null), create(null, "농구", "21:00", "play"))));

    assertThat(result).extracting(StudyPlanResponse::content).containsExactly("수학", "농구");
    assertThat(result).extracting(StudyPlanResponse::id).containsExactly("100", "101");
  }

  @Test
  @DisplayName("여러 개 중 하나라도 틀리면 하나도 저장하지 않는다")
  void createAllIsAllOrNothing() {
    StudyPlanBatchCreateRequest request =
        new StudyPlanBatchCreateRequest(
            List.of(create("3", "수학", null, null), create("99", "남의 과목", null, null)));

    assertError(() -> studyPlanService.createAll(request), ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    verify(studyPlanRepository, never()).saveAll(anyList());
  }

  // ---- 수정 ----

  @Test
  @DisplayName("보낸 것만 바뀌고, 보내지 않은 것은 그대로")
  void updateOnlySentValues() {
    StudyPlan mine = plan(7, 3, "수학 3단원", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    StudyPlanResponse result =
        studyPlanService.update(7L, update(null, "수학 4단원", 90, null, null, null));

    assertThat(result.content()).isEqualTo("수학 4단원");
    assertThat(result.targetMinutes()).isEqualTo(90);
    assertThat(result.done()).isFalse();
    assertThat(result.subjectId()).isEqualTo("3");
  }

  @Test
  @DisplayName("완료 체크를 하고 풀 수 있다")
  void toggleDone() {
    StudyPlan mine = plan(7, 3, "수학", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    assertThat(studyPlanService.update(7L, update(null, null, null, true, null, null)).done())
        .isTrue();
    assertThat(studyPlanService.update(7L, update(null, null, null, false, null, null)).done())
        .isFalse();
  }

  @Test
  @DisplayName("시작 시각을 정하고, 빈 글자로 보내면 지운다")
  void changeAndClearStartTime() {
    StudyPlan mine = plan(7, 3, "수학", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    assertThat(
            studyPlanService.update(7L, update(null, null, null, null, "07:30", null)).startTime())
        .isEqualTo("07:30");
    assertThat(studyPlanService.update(7L, update(null, null, null, null, "", null)).startTime())
        .isNull();
  }

  @Test
  @DisplayName("노는 계획으로 바꾸면 과목이 비워지고, 공부 계획으로 되돌리려면 과목이 필요하다")
  void changeKind() {
    StudyPlan mine = plan(7, 3, "수학", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    StudyPlanResponse play =
        studyPlanService.update(7L, update(null, null, null, null, null, "play"));
    assertThat(play.kind()).isEqualTo("play");
    assertThat(play.subjectId()).isEmpty();

    assertError(
        () -> studyPlanService.update(7L, update(null, null, null, null, null, "study")),
        ErrorCode.VALIDATION_ERROR,
        "공부 계획은 과목을 골라 주세요");

    StudyPlanResponse study =
        studyPlanService.update(7L, update("3", null, null, null, null, "study"));
    assertThat(study.kind()).isEqualTo("study");
    assertThat(study.subjectId()).isEqualTo("3");
  }

  @Test
  @DisplayName("과목을 남의 과목으로 바꾸려 하면 404")
  void updateRejectsOthersSubject() {
    StudyPlan mine = plan(7, 3, "수학", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    assertError(
        () -> studyPlanService.update(7L, update("99", null, null, null, null, null)),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    assertThat(mine.getSubjectId()).isEqualTo(MY_SUBJECT);
  }

  @Test
  @DisplayName("아무것도 보내지 않으면 400")
  void updateNothing() {
    given(studyPlanRepository.findByIdAndUserId(7L, ME))
        .willReturn(Optional.of(plan(7, 3, "수학", 60)));

    assertError(
        () -> studyPlanService.update(7L, update(null, null, null, null, null, null)),
        ErrorCode.VALIDATION_ERROR,
        "바꿀 값을 보내 주세요");
  }

  @Test
  @DisplayName("남의 계획이나 없는 계획은 수정·삭제할 수 없다 (404)")
  void othersPlanIsNotFound() {
    // 가짜 DB 는 "내 번호 + 그 계획 번호" 로 물으면 아무것도 돌려주지 않습니다. (남의 계획)
    given(studyPlanRepository.findByIdAndUserId(8L, ME)).willReturn(Optional.empty());

    assertError(
        () -> studyPlanService.update(8L, update(null, null, null, true, null, null)),
        ErrorCode.NOT_FOUND,
        "계획을 찾을 수 없어요");
    assertError(() -> studyPlanService.delete(8L), ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요");
    verify(studyPlanRepository, never()).delete(any());
  }

  // ---- 삭제 ----

  @Test
  @DisplayName("내 계획은 지워진다")
  void delete() {
    StudyPlan mine = plan(7, 3, "수학", 60);
    given(studyPlanRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(mine));

    studyPlanService.delete(7L);

    verify(studyPlanRepository).delete(mine);
  }
}
