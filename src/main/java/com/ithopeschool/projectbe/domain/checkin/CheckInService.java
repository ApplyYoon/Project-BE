package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInResponse;
import com.ithopeschool.projectbe.domain.checkin.dto.CheckInSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 체크인을 저장하고 꺼내는 로직입니다. (이슈 #24, docs/API.md "체크인")
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 체크인은 건드리지 않습니다.
// 기분·약속·기타 이유는 개인적인 내용이라 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class CheckInService {

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다. (공부 기록 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;

  // 조회할 수 있는 날짜의 범위입니다. 터무니없이 먼 날짜가 DB 까지 가지 않게 막습니다. (공부 기록 조회와 같음)
  static final LocalDate EARLIEST_QUERY_DATE = LocalDate.of(1970, 1, 1);
  static final LocalDate LATEST_QUERY_DATE = LocalDate.of(9999, 12, 31);

  private final CurrentUserProvider currentUserProvider;
  private final CheckInRepository checkInRepository;
  private final Clock clock;

  // 그날의 체크인을 저장합니다. 그날 이미 했으면 새 값으로 덮어쓰고, 없으면 새로 만듭니다.
  @Transactional
  public CheckInResponse save(LocalDate date, CheckInSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 오늘은 하루의 기준(새벽 4시)으로 정합니다. 아직 오지 않은 날의 체크인은 할 수 없습니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    if (date.isAfter(today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "아직 오지 않은 날짜예요");
    }

    CheckInSource source = parseSource(request.source());
    MissReason missReason = parseMissReason(request.missReason());
    Set<EmotionTag> emotions = parseEmotions(request.emotions());

    // 빈 글자("")나 띄어쓰기만 보낸 칸은 안 보낸 것(null)으로 봅니다.
    String missReasonNote = blankToNull(request.missReasonNote());
    String promiseIf = blankToNull(request.promiseIf());
    String promiseThen = blankToNull(request.promiseThen());

    // 글은 '기타' 를 골랐을 때만 받습니다. 다른 이유는 정해진 값만으로 충분합니다.
    if (missReasonNote != null && missReason != MissReason.ETC) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "이유 글은 '기타(etc)' 를 골랐을 때만 적을 수 있어요");
    }
    // 약속은 "만약 ~하면 → ~한다" 한 문장이라, 반쪽만 있는 약속은 받지 않습니다.
    if ((promiseIf == null) != (promiseThen == null)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "약속은 '만약 ~하면' 과 '~한다' 를 둘 다 적어 주세요");
    }

    Optional<CheckIn> found = checkInRepository.findByUserIdAndCheckInDate(userId, date);
    CheckIn checkIn;
    if (found.isPresent()) {
      // 같은 날을 다시 보냈으면 덮어씁니다. @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
      checkIn = found.get();
      checkIn.overwrite(
          source,
          request.condition(),
          request.mood(),
          missReason,
          missReasonNote,
          request.expectedMinutes(),
          promiseIf,
          promiseThen,
          request.suggestionKept());
      // 감정 태그를 아예 안 보냈으면(null) 그대로 둡니다. 감정 태그를 모르는 화면(자세히 체크인 등)이
      // 다시 보내도 그날 고른 감정이 지워지지 않게 하려는 것입니다. 비우려면 빈 배열 [] 을 보냅니다.
      if (emotions != null) {
        checkIn.replaceEmotions(emotions);
      }
    } else {
      checkIn =
          new CheckIn(
              userId,
              date,
              source,
              request.condition(),
              request.mood(),
              missReason,
              missReasonNote,
              request.expectedMinutes(),
              promiseIf,
              promiseThen,
              request.suggestionKept());
      if (emotions != null) {
        checkIn.replaceEmotions(emotions);
      }
      checkIn = checkInRepository.save(checkIn);
    }
    return CheckInResponse.from(checkIn);
  }

  // 기간 안의 내 체크인 목록. from ~ to 는 둘 다 포함하는 날짜이고, 날짜 순서입니다.
  // 체크인을 안 한 날은 목록에 없고, 하나도 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<CheckInResponse> getCheckIns(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkQueryDate(from);
    checkQueryDate(to);
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    return checkInRepository
        .findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(userId, from, to)
        .stream()
        .map(CheckInResponse::from)
        .toList();
  }

  // 조회하려는 날짜가 말이 되는 범위인지 봅니다. 아니면 400 입니다.
  private void checkQueryDate(LocalDate date) {
    if (date.isBefore(EARLIEST_QUERY_DATE) || date.isAfter(LATEST_QUERY_DATE)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "날짜는 " + EARLIEST_QUERY_DATE + " 부터 " + LATEST_QUERY_DATE + " 사이로 보내 주세요");
    }
  }

  // FE 가 보낸 글자("quick")를 바꿉니다. 3가지가 아니면 400 입니다.
  private CheckInSource parseSource(String value) {
    CheckInSource source = CheckInSource.fromValue(value);
    if (source == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "source 는 manual, call, quick 중에서 골라 주세요");
    }
    return source;
  }

  // FE 가 보낸 글자("phone")를 못 채운 이유로 바꿉니다. 안 보냈으면 null, 7가지가 아니면 400 입니다.
  private MissReason parseMissReason(String value) {
    if (value == null) {
      return null;
    }
    MissReason reason = MissReason.fromValue(value);
    if (reason == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "못 채운 이유는 tired, event, phone, tooMuch, unwilling, sick, etc 중에서 골라 주세요");
    }
    return reason;
  }

  // 감정 태그들을 바꿉니다. 같은 것을 두 번 보내면 하나로 봅니다. (이슈 #220)
  // 안 보냈으면(null) null 을 돌려줍니다. ("그대로 두기". 빈 배열 [] 은 "비우기" 라서 다릅니다)
  // 정해진 6가지가 아닌 글자가 하나라도 있으면 400 입니다.
  private Set<EmotionTag> parseEmotions(List<String> values) {
    if (values == null) {
      return null;
    }
    Set<EmotionTag> emotions = EnumSet.noneOf(EmotionTag.class);
    for (String value : values) {
      EmotionTag tag = EmotionTag.fromName(value);
      if (tag == null) {
        throw new ApiException(
            ErrorCode.VALIDATION_ERROR,
            "감정 태그는 STUCK, ANXIOUS, TIRED, ANNOYED, CALM, MOTIVATED 중에서 골라 주세요");
      }
      emotions.add(tag);
    }
    return emotions;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
