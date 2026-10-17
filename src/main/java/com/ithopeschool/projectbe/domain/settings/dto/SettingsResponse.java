package com.ithopeschool.projectbe.domain.settings.dto;

import com.ithopeschool.projectbe.domain.settings.CareSetting;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 관리 설정을 FE 에 알려 주는 모양입니다. 이름은 FE 의 MessageSettings · CareLimits 와 같습니다.
//   { "callTime": "21:00", "dailyCount": 3, "quietStart": "23:00", "quietEnd": "07:00",
//     "maxHarshness": 3, "allowFocusMode": false,
//     "maxCareLevel": 2, "pendingMaxCareLevel": 4, "pendingEffectiveDate": "2026-10-18",
//     "lockApps": ["유튜브"] }
// - maxCareLevel 은 "오늘 적용되는" 한도입니다. 올린 한도가 아직 적용 전이면 pendingMaxCareLevel · pendingEffectiveDate 에
// 있습니다.
// - 바뀔 한도가 없으면 pendingMaxCareLevel · pendingEffectiveDate 는 null 입니다.
// - 누구의 설정인지(userId)는 담지 않습니다.
public record SettingsResponse(
    String callTime,
    int dailyCount,
    String quietStart,
    String quietEnd,
    int maxHarshness,
    boolean allowFocusMode,
    int maxCareLevel,
    Integer pendingMaxCareLevel,
    LocalDate pendingEffectiveDate,
    List<String> lockApps) {

  // LocalTime 을 그냥 글자로 바꾸면 "21:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  // 설정 한 줄과 잠금 대상 앱 이름들로, 그날(today) 기준의 응답을 만듭니다.
  public static SettingsResponse of(CareSetting setting, List<String> lockApps, LocalDate today) {
    boolean hasPending = setting.hasPendingOn(today);
    return new SettingsResponse(
        format(setting.getCallTime()),
        setting.getDailyMessageCount(),
        format(setting.getQuietStart()),
        format(setting.getQuietEnd()),
        setting.getMaxHarshness(),
        setting.isAllowAppLock(),
        setting.maxCareLevelOn(today),
        hasPending ? setting.getPendingMaxCareLevel() : null,
        hasPending ? setting.getPendingEffectiveDate() : null,
        lockApps);
  }

  // "21:00" 모양의 글자로 바꿉니다. (Service 가 "바꾼 기록" 을 적을 때도 같은 모양을 씁니다)
  public static String format(LocalTime time) {
    return time.format(HOUR_MINUTE);
  }
}
