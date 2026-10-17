package com.ithopeschool.projectbe.domain.settings.dto;

import com.ithopeschool.projectbe.domain.care.CareLevel;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.FocusLockApp;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// "관리 설정 바꾸기" 요청의 모양입니다. **바꿀 칸만** 보냅니다. 보내지 않은 칸은 그대로입니다.
//   { "maxHarshness": 5 }
//   { "quietStart": "22:30", "quietEnd": "07:30", "lockApps": ["유튜브", "인스타그램"] }
// 이름은 FE 의 MessageSettings · CareLimits 와 같습니다. (dailyCount, quietStart, maxHarshness ...)
// - lockApps 는 목록을 **통째로** 바꿉니다. 빈 목록 [] 을 보내면 잠금 대상 앱이 모두 지워집니다.
// - 같은 앱을 두 번 보냈는지는 Service 에서 검사합니다.
public record SettingsUpdateRequest(
    // "21:00" 처럼 두 자리:두 자리. 00:00 ~ 23:59 만 됩니다.
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "전화 시간은 21:00 처럼 보내 주세요")
        String callTime,
    @Min(value = CareSetting.MIN_DAILY_MESSAGE_COUNT, message = "하루 메시지 횟수는 0~5 사이로 보내 주세요")
        @Max(value = CareSetting.MAX_DAILY_MESSAGE_COUNT, message = "하루 메시지 횟수는 0~5 사이로 보내 주세요")
        Integer dailyCount,
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "연락 금지 시각은 23:00 처럼 보내 주세요")
        String quietStart,
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "연락 금지 시각은 23:00 처럼 보내 주세요")
        String quietEnd,
    @Min(value = CareSetting.MIN_HARSHNESS, message = "독설 수위는 1~5 사이로 보내 주세요")
        @Max(value = CareSetting.MAX_HARSHNESS, message = "독설 수위는 1~5 사이로 보내 주세요")
        Integer maxHarshness,
    // 앱 잠금(집중 모드)을 허용하는지. FE 의 CareLimits.allowFocusMode 와 같은 이름입니다.
    Boolean allowFocusMode,
    @Min(value = CareLevel.MIN_LEVEL, message = "제재 한도는 0~4 사이로 보내 주세요")
        @Max(value = CareLevel.MAX_LEVEL, message = "제재 한도는 0~4 사이로 보내 주세요")
        Integer maxCareLevel,
    // 20개까지, 앱 이름은 하나에 30자까지
    @Size(max = 20, message = "잠금 대상 앱은 20개까지 고를 수 있어요")
        List<
                @NotBlank(message = "앱 이름을 입력해 주세요")
                @Size(max = FocusLockApp.MAX_APP_NAME_LENGTH, message = "앱 이름은 30자 이하로 적어 주세요")
                String>
            lockApps) {

  // 값이 들어오는 순간에 앱 이름의 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public SettingsUpdateRequest {
    lockApps =
        lockApps == null
            ? null
            : lockApps.stream().map(name -> name == null ? null : name.trim()).toList();
  }

  // 아무 칸도 보내지 않았으면 true
  public boolean isEmpty() {
    return callTime == null
        && dailyCount == null
        && quietStart == null
        && quietEnd == null
        && maxHarshness == null
        && allowFocusMode == null
        && maxCareLevel == null
        && lockApps == null;
  }
}
