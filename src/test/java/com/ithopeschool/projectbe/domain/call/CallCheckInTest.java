package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 통화 정리 → 체크인 규칙(CallCheckIn 주석의 표)을 확인합니다.
class CallCheckInTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate DAY = LocalDate.of(2026, 10, 17);

  // 통화에서: 기분 2, 폰 때문에 못 채움, 오늘 90분, 약속 "학원에서 오면 → 폰을 서랍에 넣기"
  private static final CallReview REVIEW =
      new CallReview(
          false, "요약", null, null, null, MissReason.PHONE, "학원에서 오면", "폰을 서랍에 넣기", null, 2, 90);

  private static CheckIn existing(
      CheckInSource source,
      Integer mood,
      MissReason missReason,
      String note,
      Integer expectedMinutes,
      String promiseIf,
      String promiseThen) {
    return new CheckIn(
        USER_ID,
        DAY,
        source,
        4,
        mood,
        missReason,
        note,
        expectedMinutes,
        promiseIf,
        promiseThen,
        true);
  }

  @Test
  @DisplayName("그날 체크인이 없으면 통화 값으로 새로 만들고 source 는 call")
  void newCheckIn() {
    CheckIn checkIn = CallCheckIn.newCheckIn(USER_ID, DAY, REVIEW);

    assertThat(checkIn.getUserId()).isEqualTo(USER_ID);
    assertThat(checkIn.getCheckInDate()).isEqualTo(DAY);
    assertThat(checkIn.getSource()).isEqualTo(CheckInSource.CALL);
    assertThat(checkIn.getMood()).isEqualTo(2);
    assertThat(checkIn.getMissReason()).isEqualTo(MissReason.PHONE);
    assertThat(checkIn.getExpectedMinutes()).isEqualTo(90);
    assertThat(checkIn.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(checkIn.getPromiseThen()).isEqualTo("폰을 서랍에 넣기");
    // 통화에서 정리하지 않는 칸은 비어 있습니다
    assertThat(checkIn.getCondition()).isNull();
    assertThat(checkIn.getMissReasonNote()).isNull();
    assertThat(checkIn.getSuggestionKept()).isNull();
  }

  @Test
  @DisplayName("직접 입력한 체크인은 값을 바꾸지 않고 빈 칸만 채운다 (source 도 manual 그대로)")
  void manualKeepsWhatTheStudentChose() {
    CheckIn checkIn = existing(CheckInSource.MANUAL, 4, MissReason.ETC, "학교 행사", null, null, null);

    CallCheckIn.fillBlanks(checkIn, REVIEW);

    assertThat(checkIn.getSource()).isEqualTo(CheckInSource.MANUAL);
    // 학생이 고른 값은 그대로
    assertThat(checkIn.getMood()).isEqualTo(4);
    assertThat(checkIn.getMissReason()).isEqualTo(MissReason.ETC);
    assertThat(checkIn.getMissReasonNote()).isEqualTo("학교 행사");
    assertThat(checkIn.getCondition()).isEqualTo(4);
    assertThat(checkIn.getSuggestionKept()).isTrue();
    // 빈 칸만 통화 값으로
    assertThat(checkIn.getExpectedMinutes()).isEqualTo(90);
    assertThat(checkIn.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(checkIn.getPromiseThen()).isEqualTo("폰을 서랍에 넣기");
  }

  @Test
  @DisplayName("약속은 두 칸이 한 문장이라, 이미 약속이 있으면 통화의 약속으로 바꾸지 않는다")
  void promiseIsKeptAsAPair() {
    CheckIn checkIn = existing(CheckInSource.MANUAL, null, null, null, 60, "저녁 먹고 나면", "바로 책상에 앉기");

    CallCheckIn.fillBlanks(checkIn, REVIEW);

    assertThat(checkIn.getPromiseIf()).isEqualTo("저녁 먹고 나면");
    assertThat(checkIn.getPromiseThen()).isEqualTo("바로 책상에 앉기");
    assertThat(checkIn.getExpectedMinutes()).isEqualTo(60);
    assertThat(checkIn.getMood()).isEqualTo(2);
    assertThat(checkIn.getMissReason()).isEqualTo(MissReason.PHONE);
  }

  @Test
  @DisplayName("1탭(quick) 체크인: 통화가 기분 말고 다른 칸을 채우면 call, 채운 게 없으면 quick 그대로")
  void quickBecomesCallOnlyWhenMoreIsFilled() {
    CheckIn quick = existing(CheckInSource.QUICK, 3, null, null, null, null, null);
    CallCheckIn.fillBlanks(quick, REVIEW);
    assertThat(quick.getSource()).isEqualTo(CheckInSource.CALL);
    // 1탭으로 고른 기분은 그대로
    assertThat(quick.getMood()).isEqualTo(3);
    assertThat(quick.getMissReason()).isEqualTo(MissReason.PHONE);

    CheckIn quickOnly = existing(CheckInSource.QUICK, 3, null, null, null, null, null);
    CallReview moodOnly =
        new CallReview(false, "요약", null, null, null, null, null, null, null, 2, null);
    CallCheckIn.fillBlanks(quickOnly, moodOnly);
    assertThat(quickOnly.getSource()).isEqualTo(CheckInSource.QUICK);
    assertThat(quickOnly.getMood()).isEqualTo(3);
  }

  @Test
  @DisplayName("옮길 값(기분·이유·예상 순공·약속)이 하나도 없으면 체크인을 만들지 않는다")
  void nothingToMove() {
    assertThat(CallCheckIn.hasAnyValue(CallReview.empty())).isFalse();
    assertThat(CallCheckIn.hasAnyValue(REVIEW)).isTrue();
    // 요약·습관만 있는 통화
    CallReview summaryOnly =
        new CallReview(false, "요약", null, null, null, null, null, null, true, null, null);
    assertThat(CallCheckIn.hasAnyValue(summaryOnly)).isFalse();
  }
}
