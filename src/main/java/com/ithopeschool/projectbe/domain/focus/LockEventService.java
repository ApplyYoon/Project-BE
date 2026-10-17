package com.ithopeschool.projectbe.domain.focus;

import com.ithopeschool.projectbe.domain.focus.dto.*;
import com.ithopeschool.projectbe.domain.settings.*;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.*;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LockEventService {
  private final CurrentUserProvider currentUserProvider;
  private final CareSettingRepository settings;
  private final LockEventRepository events;
  private final Clock clock;

  @Transactional
  public LockEventResponse create(LockEventRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    CareSetting setting =
        settings.findByUserId(userId).orElseGet(() -> CareSetting.withDefaults(userId));
    if (!FocusMode.canUse(
        setting.isAllowAppLock(), setting.maxCareLevelOn(DayRule.dateOf(clock.instant()))))
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "집중 모드 잠금을 허용한 경우에만 기록할 수 있어요");
    if (!FocusMode.isValidUnlockReason(request.reason())
        || request.reason().matches("(?s).*[\\r\\n\\u2028\\u2029].*"))
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "해제 이유를 한 줄로 적어 주세요");
    if (request.occurredAt().isAfter(clock.instant()))
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "미래의 기록은 저장할 수 없어요");
    return LockEventResponse.of(
        events.save(
            new LockEvent(
                userId,
                request.occurredAt(),
                request.appName().strip(),
                request.reason().strip())));
  }

  @Transactional(readOnly = true)
  public LockEventStats stats(LocalDate startDate, LocalDate endDate) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) > 365)
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "조회 기간은 순서대로 366일 이내로 보내 주세요");
    List<LockEvent> found =
        events.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            userId, DayRule.startOf(startDate), DayRule.startOf(endDate.plusDays(1)));
    Map<Integer, Long> hours = new TreeMap<>();
    Map<String, Long> apps = new TreeMap<>();
    for (LockEvent event : found) {
      hours.merge(event.getOccurredAt().atZone(ZoneId.of("Asia/Seoul")).getHour(), 1L, Long::sum);
      apps.merge(event.getAppName(), 1L, Long::sum);
    }
    return new LockEventStats(found.size(), hours, apps);
  }
}
