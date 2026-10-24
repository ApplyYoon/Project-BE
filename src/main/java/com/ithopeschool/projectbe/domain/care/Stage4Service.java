package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리 4단계 장치를 켤지 · 풀지를 DB 를 보고 정합니다. (이슈 #234, 규칙은 Stage4)
//   - 미루기(CardDayService)와 공부 기록 저장(StudySessionService)이 isOn 을 물어봅니다.
//   - 증빙이 있는 실행을 저장하면 releaseIfProven 이 4단계를 풀어 3단계로 내립니다.
//
// userId 는 부르는 쪽이 currentUserProvider.getCurrentUserId() 로 얻은 값을 넘깁니다. (요청에서 받지 않음)
// 오늘 판정한 줄이 없으면 4단계가 아닌 것으로 봅니다. (판정은 CareStageService 가 합니다)
@Service
@RequiredArgsConstructor
public class Stage4Service {

  private final DailyCareStageRepository dailyCareStageRepository;
  private final CareSettingRepository careSettingRepository;
  // 보호 조치(위기 표현 뒤 7일) 중이면 어떤 강제도 켜지 않습니다.
  private final SafetyService safetyService;
  private final Clock clock;

  // 지금 4단계 장치(미루기 잠금 · 증빙 강제)가 켜져 있는지
  @Transactional(readOnly = true)
  public boolean isOn(Long userId) {
    LocalDate today = DayRule.dateOf(clock.instant());
    Optional<DailyCareStage> stage = todayStage(userId, today);
    return stage.isPresent() && isOn(userId, today, stage.get());
  }

  // 증빙이 있는 실행을 저장한 뒤 부릅니다. 4단계였으면 풀고(→ 3단계) true, 아니면 아무것도 안 하고 false 입니다.
  // 풀면 그날 절대로 본 카드도 원래대로 돌아갑니다. (DailyCareStage.release)
  @Transactional
  public boolean releaseIfProven(Long userId, boolean hasProof) {
    if (!hasProof) {
      return false;
    }
    LocalDate today = DayRule.dateOf(clock.instant());
    Optional<DailyCareStage> stage = todayStage(userId, today);
    if (stage.isEmpty() || !isOn(userId, today, stage.get())) {
      return false;
    }
    stage.get().release(clock.instant());
    return true;
  }

  private Optional<DailyCareStage> todayStage(Long userId, LocalDate today) {
    return dailyCareStageRepository.findByUserIdAndStageDate(userId, today);
  }

  // 판정한 줄 + 지금의 제재 한도 · 보호 조치로 Stage4.isOn 을 부릅니다.
  private boolean isOn(Long userId, LocalDate today, DailyCareStage stage) {
    int maxLevel =
        careSettingRepository
            .findByUserId(userId)
            .map(setting -> setting.maxCareLevelOn(today))
            .orElse(CareSetting.DEFAULT_MAX_CARE_LEVEL);
    return Stage4.isOn(
        stage.getStage(), stage.isReleased(), maxLevel, safetyService.isActive(userId));
  }
}
