package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.safety.QuietHours;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 한 사람의 연락 금지 시간을 진짜 DB(H2)의 관리 설정에서 읽어 오는지 확인합니다. (이슈 #57 "관리 설정으로 바꿀 수 있다")
@DataJpaTest
class QuietHoursSettingsTest {

  @Autowired private CareSettingRepository careSettingRepository;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("설정을 바꾼 사람은 그 사람이 정한 시간, 안 바꾼 사람은 기본값(23:00 ~ 07:00)")
  void readsEachUsersWindow() {
    CareSetting mine = CareSetting.withDefaults(1L);
    mine.changeQuietStart(LocalTime.of(22, 30));
    mine.changeQuietEnd(LocalTime.of(7, 30));
    em.persistAndFlush(mine);
    em.clear();

    QuietHoursSettings settings = new QuietHoursSettings(careSettingRepository);

    assertThat(settings.windowOf(1L))
        .isEqualTo(new QuietHours.Window(LocalTime.of(22, 30), LocalTime.of(7, 30)));
    assertThat(settings.windowOf(999L)).isEqualTo(QuietHours.Window.DEFAULT);
  }
}
