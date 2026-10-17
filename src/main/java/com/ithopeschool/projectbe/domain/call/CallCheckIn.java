package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import java.time.LocalDate;

// 통화에서 정리한 값을 그날의 체크인으로 옮깁니다. (이슈 #29 "뽑은 체크인은 #24 에 source: call 로 저장된다")
// 통화로 답한 것을 체크인 화면에서 또 입력하게 하면 안 되기 때문입니다. (기획서 2장)
//
// ■ 옮기는 칸 (뜻이 같은 칸끼리)
//   통화 정리(CallReview)        체크인(CheckIn)
//   mood            지금 기분     →  mood            오늘 기분 (1 ~ 5, 1 이 가장 처짐)
//   missReason      어제 못 채운 이유 → missReason  어제 계획을 못 채운 이유
//   expectedMinutes 오늘 할 시간  →  expectedMinutes 오늘 예상 순공시간(분)
//   promiseIf·Then  오늘의 약속   →  promiseIf·Then  오늘의 약속
//   컨디션·기타 글·개선안 실천은 통화에서 정리하지 않아서 옮기지 않습니다.
//
// ■ 그날 체크인이 이미 있으면 "빈 칸만" 채웁니다. 학생이 직접 고른 값을 통화가 바꾸지 않습니다.
//     체크인 없음                →  통화 값으로 새로 만들고 source 는 CALL
//     직접 입력(MANUAL)이 있음    →  빈 칸만 채움, source 는 MANUAL 그대로
//     1탭(QUICK)이 있음          →  빈 칸만 채움. 다른 칸을 채웠으면 source 는 CALL (더는 "기분만 누른 것" 이 아니라서)
//     통화(CALL)가 있음          →  빈 칸만 채움, source 는 CALL 그대로
//   약속은 두 칸이 한 문장이라, 두 칸이 모두 비어 있을 때만 통화의 약속으로 채웁니다.
//
// 학생이 만든 체크인의 규칙(약속은 두 칸 같이, 기타 글은 '기타' 일 때만)을 그대로 지킵니다.
// 통화 정리는 기타 글을 만들지 않으므로, '기타' 이유라도 글 칸은 비어 있습니다.
public final class CallCheckIn {

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CallCheckIn() {}

  // 체크인으로 옮길 값이 하나라도 있으면 true. 없으면 체크인을 만들지 않습니다. (학생이 말을 안 한 통화 등)
  public static boolean hasAnyValue(CallReview review) {
    return review.mood() != null
        || review.missReason() != null
        || review.expectedMinutes() != null
        || review.promiseIf() != null;
  }

  // 그날 체크인이 없을 때, 통화 값으로 새로 만듭니다.
  public static CheckIn newCheckIn(Long userId, LocalDate date, CallReview review) {
    return new CheckIn(
        userId,
        date,
        CheckInSource.CALL,
        null,
        review.mood(),
        review.missReason(),
        null,
        review.expectedMinutes(),
        review.promiseIf(),
        review.promiseThen(),
        null);
  }

  // 그날 체크인이 이미 있을 때, 비어 있는 칸만 통화 값으로 채웁니다. (위 주석의 표)
  public static void fillBlanks(CheckIn checkIn, CallReview review) {
    Integer mood = checkIn.getMood() != null ? checkIn.getMood() : review.mood();
    // 이유를 채울 때 기타 글은 그대로 둡니다. (이유가 비어 있었으면 글도 비어 있습니다)
    MissReason missReason =
        checkIn.getMissReason() != null ? checkIn.getMissReason() : review.missReason();
    Integer expectedMinutes =
        checkIn.getExpectedMinutes() != null
            ? checkIn.getExpectedMinutes()
            : review.expectedMinutes();
    boolean hasPromise = checkIn.getPromiseIf() != null || checkIn.getPromiseThen() != null;
    String promiseIf = hasPromise ? checkIn.getPromiseIf() : review.promiseIf();
    String promiseThen = hasPromise ? checkIn.getPromiseThen() : review.promiseThen();

    // 1탭 체크인은 기분만 있는 체크인입니다. 통화가 다른 칸을 채웠으면 통화 체크인이 됩니다.
    boolean filledMoreThanMood =
        (checkIn.getMissReason() == null && missReason != null)
            || (checkIn.getExpectedMinutes() == null && expectedMinutes != null)
            || (!hasPromise && promiseIf != null);
    CheckInSource source =
        checkIn.getSource() == CheckInSource.QUICK && filledMoreThanMood
            ? CheckInSource.CALL
            : checkIn.getSource();

    checkIn.overwrite(
        source,
        checkIn.getCondition(),
        mood,
        missReason,
        checkIn.getMissReasonNote(),
        expectedMinutes,
        promiseIf,
        promiseThen,
        checkIn.getSuggestionKept());
  }
}
