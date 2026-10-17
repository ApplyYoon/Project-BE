package com.ithopeschool.projectbe.domain.dailysignal;

import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalResponse;
import com.ithopeschool.projectbe.domain.dailysignal.dto.DailySignalSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 하루 신호(휴대폰·수면·걸음)를 저장하는 로직입니다. (이슈 #18, docs/API.md "하루 신호")
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 데이터는 건드리지 않습니다.
// 수면·걸음은 건강 데이터라 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class DailySignalService {

  private final CurrentUserProvider currentUserProvider;
  private final DailySignalRepository dailySignalRepository;
  private final Clock clock;

  // 하루치를 저장합니다. 그날 이미 보낸 게 있으면 새 값으로 덮어쓰고, 없으면 새로 만듭니다.
  @Transactional
  public DailySignalResponse save(DailySignalSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 오늘은 하루의 기준(새벽 4시)으로 정합니다. 아직 오지 않은 날의 데이터는 있을 수 없습니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    if (request.date().isAfter(today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날짜예요");
    }

    Instant sleptAt = request.sleptAt() == null ? null : Instant.ofEpochMilli(request.sleptAt());
    Instant wokeAt = request.wokeAt() == null ? null : Instant.ofEpochMilli(request.wokeAt());
    // 둘 다 보냈을 때만 앞뒤를 봅니다. 하나만 보내는 것도 괜찮습니다.
    if (sleptAt != null && wokeAt != null && !wokeAt.isAfter(sleptAt)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "일어난 시각은 잠든 시각보다 뒤여야 해요");
    }

    Optional<DailySignal> found =
        dailySignalRepository.findByUserIdAndSignalDate(userId, request.date());
    DailySignal signal;
    if (found.isPresent()) {
      // 같은 날을 다시 보냈으면 덮어씁니다. @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
      signal = found.get();
      signal.overwrite(
          request.sleepMinutes(), sleptAt, wokeAt, request.steps(), request.snsOverThirtyMinutes());
    } else {
      signal =
          dailySignalRepository.save(
              new DailySignal(
                  userId,
                  request.date(),
                  request.sleepMinutes(),
                  sleptAt,
                  wokeAt,
                  request.steps(),
                  request.snsOverThirtyMinutes()));
    }
    return DailySignalResponse.from(signal);
  }
}
