package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.care.dto.CareStageResponse;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 관리 4단계 장치를 켜고 푸는 흐름을 확인합니다. (이슈 #234 완료 조건: 켜기·해제 테스트, 절대 전환한 카드가 해제 때 되돌아감)
// 판정한 줄(DailyCareStage)은 진짜 객체를 쓰고, DB · 설정 · 보호 조치만 가짜로 둡니다.
class Stage4ServiceTest {

  private static final Long ME = 1L;
  private static final Long FORCED_CARD = 7L;
  // 지금 = 10/17 18:00 (한국 시간). 새벽 4시 기준으로도 10/17 입니다.
  private static final Instant NOW =
      ZonedDateTime.of(2026, 10, 17, 18, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private DailyCareStageRepository dailyCareStageRepository;
  private CareSettingRepository careSettingRepository;
  private SafetyService safetyService;
  private Stage4Service stage4Service;
  private DailyCareStage todayStage;

  @BeforeEach
  void setUp() {
    dailyCareStageRepository = mock(DailyCareStageRepository.class);
    careSettingRepository = mock(CareSettingRepository.class);
    safetyService = mock(SafetyService.class);
    stage4Service =
        new Stage4Service(
            dailyCareStageRepository,
            careSettingRepository,
            safetyService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // 오늘 4단계로 판정했고, 반복 카드 7번의 오늘 것을 절대 카드로 정해 두었습니다.
    todayStage =
        new DailyCareStage(
            ME,
            TODAY,
            new CareStage.Result(4, CareStage.Rule.UP, false, "폰 때문이라 강제적 채찍 모드야."),
            NOW.minusSeconds(3600));
    todayStage.forceAbsolute(FORCED_CARD, TODAY);
    given(dailyCareStageRepository.findByUserIdAndStageDate(ME, TODAY))
        .willReturn(Optional.of(todayStage));
    // 제재 한도 4 (어제 올려서 오늘부터 적용)
    setMaxCareLevel(4);
  }

  private void setMaxCareLevel(int level) {
    CareSetting setting = CareSetting.withDefaults(ME);
    setting.changeMaxCareLevel(level, TODAY.minusDays(1));
    given(careSettingRepository.findByUserId(ME)).willReturn(Optional.of(setting));
  }

  @Test
  @DisplayName("켜기: 오늘 4단계 · 한도 4 · 보호 조치 없음이면 켜지고, 응답에 절대 카드가 보인다")
  void on() {
    assertThat(stage4Service.isOn(ME)).isTrue();

    CareStageResponse response = CareStageResponse.from(todayStage);
    assertThat(response.stage()).isEqualTo(4);
    assertThat(response.released()).isFalse();
    assertThat(response.forcedCard())
        .isEqualTo(new CareStageResponse.ForcedCard(FORCED_CARD, TODAY));
  }

  @Test
  @DisplayName("켜지지 않음: 한도를 4 밑으로 낮췄거나 · 보호 조치 중이거나 · 오늘 판정이 없으면 꺼져 있다")
  void off() {
    setMaxCareLevel(3);
    assertThat(stage4Service.isOn(ME)).isFalse();

    setMaxCareLevel(4);
    given(safetyService.isActive(ME)).willReturn(true);
    assertThat(stage4Service.isOn(ME)).isFalse();
    // 보호 조치 중에는 증빙이 있어도 "풀기" 를 하지 않습니다. (애초에 켜져 있지 않음)
    assertThat(stage4Service.releaseIfProven(ME, true)).isFalse();
    assertThat(todayStage.isReleased()).isFalse();

    given(dailyCareStageRepository.findByUserIdAndStageDate(ME, TODAY))
        .willReturn(Optional.empty());
    assertThat(stage4Service.isOn(ME)).isFalse();
  }

  @Test
  @DisplayName("해제: 증빙 없는 실행으로는 안 풀리고, 증빙이 있는 실행 1회면 3단계로 내려가고 절대 카드가 되돌아간다")
  void release() {
    assertThat(stage4Service.releaseIfProven(ME, false)).isFalse();
    assertThat(stage4Service.isOn(ME)).isTrue();

    assertThat(stage4Service.releaseIfProven(ME, true)).isTrue();

    assertThat(todayStage.isReleased()).isTrue();
    assertThat(todayStage.getReleasedAt()).isEqualTo(NOW);
    assertThat(todayStage.currentStage()).isEqualTo(3);
    // 판정한 값은 그대로 남습니다. (당근 주 vs 채찍 주 비교용)
    assertThat(todayStage.getStage()).isEqualTo(4);
    // 절대로 바꿨던 카드가 원래대로 돌아갑니다.
    assertThat(todayStage.getForcedCardId()).isNull();
    assertThat(todayStage.getForcedCardDate()).isNull();
    assertThat(stage4Service.isOn(ME)).isFalse();

    CareStageResponse response = CareStageResponse.from(todayStage);
    assertThat(response.stage()).isEqualTo(3);
    assertThat(response.name()).isEqualTo(CareLevelInfo.of(3).name());
    assertThat(response.reason()).isEqualTo(Stage4.RELEASE_REASON);
    assertThat(response.released()).isTrue();
    assertThat(response.forcedCard()).isNull();

    // 한 번 풀면 다시 증빙을 내도 그대로입니다.
    assertThat(stage4Service.releaseIfProven(ME, true)).isFalse();
  }
}
