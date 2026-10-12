package com.ithopeschool.projectbe.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 계획 로직의 규칙을 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에서 내 것만 나오는지는 StudyPlanRepositoryTest 가 확인합니다.
class StudyPlanServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;
  private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

  private CurrentUserProvider currentUserProvider;
  private StudyPlanRepository studyPlanRepository;
  private StudyPlanService studyPlanService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    studyPlanRepository = mock(StudyPlanRepository.class);
    studyPlanService = new StudyPlanService(currentUserProvider, studyPlanRepository);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  // DB 가 번호를 매겨 준 것처럼 id 를 넣은 계획을 만듭니다.
  private static StudyPlan plan(long id, long subjectId, String content, int targetMinutes) {
    StudyPlan plan = new StudyPlan(ME, subjectId, OCT_5, content, targetMinutes);
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }

  @Test
  @DisplayName("로그인한 내 번호와 보낸 날짜로 묻고, FE 모양으로 바꿔 돌려준다")
  void getPlans() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, OCT_5))
        .willReturn(List.of(plan(7, 3, "수학 문제집 3단원 풀기", 90), plan(8, 4, "영단어 50개", 30)));

    List<StudyPlanResponse> result = studyPlanService.getPlans(OCT_5);

    verify(studyPlanRepository).findByUserIdAndPlanDateOrderById(ME, OCT_5);
    assertThat(result)
        .containsExactly(
            new StudyPlanResponse("7", OCT_5, "3", "수학 문제집 3단원 풀기", 90, false, null, "study", null),
            new StudyPlanResponse("8", OCT_5, "4", "영단어 50개", 30, false, null, "study", null));
  }

  @Test
  @DisplayName("계획이 없는 날은 빈 목록")
  void emptyDay() {
    given(studyPlanRepository.findByUserIdAndPlanDateOrderById(ME, OCT_5)).willReturn(List.of());

    assertThat(studyPlanService.getPlans(OCT_5)).isEmpty();
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이고, DB 는 건드리지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> studyPlanService.getPlans(OCT_5))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verifyNoInteractions(studyPlanRepository);
  }
}
