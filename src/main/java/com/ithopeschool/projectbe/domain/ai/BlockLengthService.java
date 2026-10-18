package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswerRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "내가 한 번에 공부하는 길이" 를 서버 DB 의 내 기록에서 읽어 정합니다. 규칙은 BlockLength 에 있습니다. (이슈 #237)
// 읽는 것: 최근 14일(오늘 포함)의 공부 기록(노는 기록 빼고), 가장 최근 처음 설문, 가장 최근 심리검사.
// 부르는 쪽(말로 카드 만들기 등)이 userId 를 넘깁니다. userId 는 그쪽이 CurrentUserProvider 로 얻은 값입니다.
@Service
@RequiredArgsConstructor
public class BlockLengthService {

  // 공부 기록을 보는 기간 (오늘 포함 14일)
  static final int LOOKBACK_DAYS = 14;

  private final StudySessionRepository studySessionRepository;
  private final OnboardingAnswerRepository onboardingAnswerRepository;
  private final PsychTestRepository psychTestRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public BlockLength.Choice choose(Long userId) {
    LocalDate today = DayRule.dateOf(clock.instant());
    List<Integer> sessionMinutes = new ArrayList<>();
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId,
            DayRule.startOf(today.minusDays(LOOKBACK_DAYS - 1)),
            DayRule.startOf(today.plusDays(1)))) {
      if (session.getKind() != PlanKind.STUDY) {
        continue;
      }
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      sessionMinutes.add(StudyTime.toMinutes(millis));
    }

    Integer selfFocus =
        onboardingAnswerRepository
            .findFirstByUserIdOrderByAnsweredAtDescIdDesc(userId)
            .map(OnboardingAnswer::getMaxFocusMinutes)
            .orElse(null);

    boolean distractionHigh =
        psychTestRepository
            .findFirstByUserIdOrderByIdDesc(userId)
            // 답이 거의 다 같아서 믿기 어려운 심리검사는 없는 것으로 봅니다. (PsychTest.isReliable)
            .filter(PsychTest::isReliable)
            .map(
                test ->
                    PsychScore.levelOf(
                            PsychScore.scoresOf(test.getAnswers()).get(PsychScale.DISTRACTION))
                        == PsychScore.Level.HIGH)
            .orElse(false);

    return BlockLength.of(sessionMinutes, selfFocus, distractionHigh);
  }
}
