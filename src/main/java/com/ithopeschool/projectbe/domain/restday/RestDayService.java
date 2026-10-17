package com.ithopeschool.projectbe.domain.restday;

import com.ithopeschool.projectbe.domain.restday.dto.RestDayResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 휴식 처리 로직입니다. (이슈 #225, docs/API.md "휴식 처리")
// 한도(한 달 4번)와 기한(지난 날 · 오늘만)은 RestDayRule 이 계산하고, 여기서는 세서 넘기고 에러를 던집니다.
@Service
@RequiredArgsConstructor
public class RestDayService {

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. (공부 기록 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;

  private final CurrentUserProvider currentUserProvider;
  private final RestDayRepository restDayRepository;
  // "오늘" 이 언제인지 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다.
  private final Clock clock;

  // 그 날을 휴식으로 처리합니다.
  // 검사 순서: 아직 오지 않은 날 · 7일보다 오래된 날(400) → 이미 처리한 날(409) → 이번 달 한도(409)
  @Transactional
  public RestDayResponse rest(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();

    LocalDate today = DayRule.dateOf(clock.instant());
    if (!RestDayRule.isAllowedDate(date, today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "휴식 처리는 오늘부터 7일 전까지만 할 수 있어요");
    }
    if (restDayRepository.existsByUserIdAndRestDate(userId, date)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 휴식으로 처리한 날이에요");
    }
    long usedThisMonth =
        restDayRepository.countByUserIdAndRestDateBetween(
            userId, date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
    if (RestDayRule.daysLeft(usedThisMonth) == 0) {
      throw new ApiException(
          ErrorCode.CONFLICT, "휴식 처리는 한 달에 " + RestDayRule.MONTHLY_LIMIT + "번까지예요");
    }

    return RestDayResponse.from(restDayRepository.save(new RestDay(userId, date, clock.instant())));
  }

  // from ~ to (두 날짜 모두 포함) 의 내 휴식 날 목록. 31일까지. 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<RestDayResponse> getRestDays(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }
    List<RestDayResponse> restDays = new ArrayList<>();
    for (RestDay restDay :
        restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(userId, from, to)) {
      restDays.add(RestDayResponse.from(restDay));
    }
    return restDays;
  }
}
