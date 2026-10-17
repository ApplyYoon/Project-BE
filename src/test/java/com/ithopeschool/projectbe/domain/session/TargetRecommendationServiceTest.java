package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetRecommendationServiceTest {
  private final CurrentUserProvider user = mock(CurrentUserProvider.class);
  private final SubjectRepository subjects = mock(SubjectRepository.class);
  private final StudySessionRepository sessions = mock(StudySessionRepository.class);
  private final TargetRecommendationService service =
      new TargetRecommendationService(user, subjects, sessions);

  @Test
  void ownSubjectAndPausedTimeAreUsedWithoutSaving() {
    when(user.getCurrentUserId()).thenReturn(7L);
    when(subjects.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(mock(Subject.class)));
    Instant start = Instant.parse("2026-10-01T00:00:00Z");
    // 벽시계는 50분이지만 20분을 쉬었으므로 순공 30분에서 멈춘 것으로 추천합니다.
    StudySession session =
        new StudySession(
            7L, 3L, start, start.plusSeconds(3000), 1, 1200000, null, 50, null, PlanKind.STUDY);
    when(sessions
            .findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
                7L, 3L, PlanKind.STUDY, 0))
        .thenReturn(List.of(session, session, session));
    var result = service.recommend(3L, 50).recommendation();
    assertThat(result.minutes()).isEqualTo(30);
    assertThat(result.kind()).isEqualTo(TargetRecommend.Kind.LOWER);
    verify(sessions)
        .findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
            7L, 3L, PlanKind.STUDY, 0);
    verifyNoMoreInteractions(sessions);
  }

  @Test
  void foreignSubjectCannotReadSessions() {
    when(user.getCurrentUserId()).thenReturn(7L);
    when(subjects.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.recommend(3L, 50)).isInstanceOf(ApiException.class);
    verifyNoInteractions(sessions);
  }

  @Test
  void invalidTargetDoesNotQueryData() {
    when(user.getCurrentUserId()).thenReturn(7L);
    assertThatThrownBy(() -> service.recommend(3L, 601)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.recommend(3L, 0)).isInstanceOf(ApiException.class);
    verifyNoInteractions(subjects, sessions);
  }

  @Test
  void noRecordsReturnsNoRecommendation() {
    when(user.getCurrentUserId()).thenReturn(7L);
    when(subjects.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(mock(Subject.class)));
    when(sessions
            .findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
                7L, 3L, PlanKind.STUDY, 0))
        .thenReturn(List.of());
    assertThat(service.recommend(3L, 50).recommendation()).isNull();
  }
}
