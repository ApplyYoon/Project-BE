package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 저장된 내 기록으로 추천만 만듭니다. 조회만으로 계획이나 타이머 목표를 바꾸지 않습니다.
@Service
@RequiredArgsConstructor
public class TargetRecommendationService {
  private final CurrentUserProvider currentUserProvider;
  private final SubjectRepository subjectRepository;
  private final StudySessionRepository sessionRepository;

  @Transactional(readOnly = true)
  public Response recommend(Long subjectId, int currentTargetMinutes) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (currentTargetMinutes < 1 || currentTargetMinutes > TargetRecommend.MAX_TARGET_MINUTES) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표 시간은 1~600분으로 보내 주세요");
    }
    subjectRepository
        .findByIdAndUserId(subjectId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없습니다"));
    var sessions =
        sessionRepository
            .findTop3ByUserIdAndSubjectIdAndKindAndTargetMinutesGreaterThanOrderByStartedAtDescIdDesc(
                userId, subjectId, PlanKind.STUDY, 0);
    var inputs =
        sessions.stream()
            .map(
                session ->
                    new TargetRecommend.TimedSession(
                        session.getStartedAt(),
                        session.getTargetMinutes(),
                        StudyTime.studyMillis(
                            session.getStartedAt(), session.getEndedAt(), session.getPausedMs())))
            .toList();
    return new Response(TargetRecommend.of(inputs, currentTargetMinutes));
  }

  // 추천이 없을 때도 JSON 본문을 유지합니다: {"recommendation":null}.
  public record Response(TargetRecommend recommendation) {}
}
