package com.ithopeschool.projectbe.domain.settings;

import com.ithopeschool.projectbe.domain.safety.QuietHours;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 한 사람의 연락 금지 시간을 관리 설정(#22)에서 읽어 옵니다. (이슈 #57 "관리 설정으로 바꿀 수 있다")
// 한 번도 설정을 바꾸지 않은 사람은 DB 에 줄이 없어서 기본값(23:00 ~ 07:00)입니다.
//
// 메시지·푸시를 보내는 예약 작업이 "이 학생에게 지금 보내도 되나" 를 볼 때 씁니다. (로그인과 상관없이, 받을 사람의 번호로 찾습니다)
@Component
@RequiredArgsConstructor
public class QuietHoursSettings {

  private final CareSettingRepository careSettingRepository;

  @Transactional(readOnly = true)
  public QuietHours.Window windowOf(Long userId) {
    return careSettingRepository
        .findByUserId(userId)
        .map(setting -> new QuietHours.Window(setting.getQuietStart(), setting.getQuietEnd()))
        .orElse(QuietHours.Window.DEFAULT);
  }
}
