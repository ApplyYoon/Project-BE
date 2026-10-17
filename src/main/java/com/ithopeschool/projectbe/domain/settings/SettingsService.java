package com.ithopeschool.projectbe.domain.settings;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리 설정 보기·바꾸기 로직입니다. (이슈 #22, docs/API.md "관리 설정")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 내 설정만 보고 바꿉니다. (누구의 설정인지를 요청에서 받지 않습니다)
//
// 아직 한 번도 바꾼 적이 없는 사람은 DB 에 줄이 없고, 처음 값(CareSetting.withDefaults)을 보여 줍니다.
// 처음 바꿀 때 처음 값으로 줄을 만든 뒤 바꿉니다.
@Service
@RequiredArgsConstructor
public class SettingsService {

  private final CurrentUserProvider currentUserProvider;
  private final CareSettingRepository careSettingRepository;
  private final FocusLockAppRepository focusLockAppRepository;
  private final SettingChangeRepository settingChangeRepository;
  // "지금" 과 "오늘" 을 알 때 씁니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 내 관리 설정. 한 번도 바꾼 적이 없으면 처음 값입니다. (이때 DB 에 저장하지는 않습니다)
  @Transactional(readOnly = true)
  public SettingsResponse getSettings() {
    Long userId = currentUserProvider.getCurrentUserId();
    CareSetting setting =
        careSettingRepository
            .findByUserId(userId)
            .orElseGet(() -> CareSetting.withDefaults(userId));
    return SettingsResponse.of(setting, lockAppNamesOf(userId), today());
  }

  // 관리 설정 바꾸기: 보낸 칸만 바꾸고, 실제로 값이 달라진 칸마다 "바꾼 기록" 을 한 줄씩 남깁니다.
  @Transactional
  public SettingsResponse update(SettingsUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (request.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요");
    }
    // 바꾸기 전에 먼저 검사합니다. 여기서 에러가 나면 아무것도 바뀌지 않습니다.
    if (request.lockApps() != null) {
      checkNoDuplicateApp(request.lockApps());
    }

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    CareSetting setting =
        careSettingRepository
            .findByUserId(userId)
            .orElseGet(() -> careSettingRepository.save(CareSetting.withDefaults(userId)));
    // 적용 날짜가 된 "바뀔 한도" 를 먼저 지금 한도로 옮겨 둡니다. (그래야 이전 값이 정확합니다)
    setting.applyDuePending(today);

    ChangeLog log = new ChangeLog(userId, now);

    if (request.callTime() != null) {
      LocalTime callTime = LocalTime.parse(request.callTime());
      log.add("callTime", format(setting.getCallTime()), format(callTime));
      setting.changeCallTime(callTime);
    }
    if (request.dailyCount() != null) {
      log.add("dailyCount", setting.getDailyMessageCount(), request.dailyCount());
      setting.changeDailyMessageCount(request.dailyCount());
    }
    if (request.quietStart() != null) {
      LocalTime quietStart = LocalTime.parse(request.quietStart());
      log.add("quietStart", format(setting.getQuietStart()), format(quietStart));
      setting.changeQuietStart(quietStart);
    }
    if (request.quietEnd() != null) {
      LocalTime quietEnd = LocalTime.parse(request.quietEnd());
      log.add("quietEnd", format(setting.getQuietEnd()), format(quietEnd));
      setting.changeQuietEnd(quietEnd);
    }
    if (request.maxHarshness() != null) {
      log.add("maxHarshness", setting.getMaxHarshness(), request.maxHarshness());
      setting.changeMaxHarshness(request.maxHarshness());
    }
    if (request.allowFocusMode() != null) {
      log.add("allowFocusMode", setting.isAllowAppLock(), request.allowFocusMode());
      setting.changeAllowAppLock(request.allowFocusMode());
    }
    if (request.maxCareLevel() != null) {
      // 이전 값은 "사용자가 마지막으로 고른 한도" 입니다. (올리고 아직 적용 전이면 그 올린 값)
      log.add("maxCareLevel", setting.requestedMaxCareLevel(), request.maxCareLevel());
      // 낮추면 바로, 높이면 다음 날부터 적용됩니다. (CareSetting.changeMaxCareLevel)
      setting.changeMaxCareLevel(request.maxCareLevel(), today);
    }
    if (request.lockApps() != null) {
      List<String> before = lockAppNamesOf(userId);
      if (!before.equals(request.lockApps())) {
        log.add("lockApps", String.join(", ", before), String.join(", ", request.lockApps()));
        replaceLockApps(userId, request.lockApps());
      }
    }

    settingChangeRepository.saveAll(log.changes);
    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    return SettingsResponse.of(setting, lockAppNamesOf(userId), today);
  }

  // 잠금 대상 앱 목록을 통째로 바꿉니다. (모두 지우고, 보낸 순서대로 새로 넣습니다)
  private void replaceLockApps(Long userId, List<String> appNames) {
    focusLockAppRepository.deleteAllOfUser(userId);
    List<FocusLockApp> apps = new ArrayList<>();
    for (String appName : appNames) {
      apps.add(new FocusLockApp(userId, appName));
    }
    focusLockAppRepository.saveAll(apps);
  }

  // 같은 앱을 두 번 보내면 400 입니다. (DB 의 "같은 앱은 한 번만" 규칙에 걸리기 전에 알려 줍니다)
  private void checkNoDuplicateApp(List<String> appNames) {
    Set<String> seen = new HashSet<>();
    for (String appName : appNames) {
      if (!seen.add(appName)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 앱이 두 번 들어 있어요");
      }
    }
  }

  private List<String> lockAppNamesOf(Long userId) {
    return focusLockAppRepository.findByUserIdOrderById(userId).stream()
        .map(FocusLockApp::getAppName)
        .toList();
  }

  private LocalDate today() {
    return DayRule.dateOf(clock.instant());
  }

  private static String format(LocalTime time) {
    return SettingsResponse.format(time);
  }

  // 한 번의 요청에서 생긴 "바꾼 기록" 을 모읍니다. 값이 실제로 달라진 칸만 적습니다. (같은 값을 보내면 기록 안 함)
  private static class ChangeLog {
    private final Long userId;
    private final Instant changedAt;
    private final List<SettingChange> changes = new ArrayList<>();

    ChangeLog(Long userId, Instant changedAt) {
      this.userId = userId;
      this.changedAt = changedAt;
    }

    void add(String field, Object oldValue, Object newValue) {
      String oldText = String.valueOf(oldValue);
      String newText = String.valueOf(newValue);
      if (!oldText.equals(newText)) {
        changes.add(new SettingChange(userId, changedAt, field, oldText, newText));
      }
    }
  }
}
