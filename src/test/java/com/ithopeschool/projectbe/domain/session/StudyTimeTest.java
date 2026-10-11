package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.AchievementRate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

// "순공시간 = 끝 − 시작 − 일시정지한 시간" 규칙(이슈 #14, #15)을 확인합니다.
// feExample 로 시작하는 5개는 FE 함수(getStudyMs) 주석의 예시를 그대로 옮긴 것입니다.
// (FE 예6 "노는 기록은 0" 은 종류를 보고 미리 빼는 일이라, 시각만 받는 이 계산기의 테스트에는 없습니다)
class StudyTimeTest {

  private static final long MINUTE = 60_000;

  // 한국 시간 2026년 9월 28일의 "시:분" 을 순간(Instant)으로 바꾸는 도우미입니다.
  private static Instant at(int hour, int minute) {
    return ZonedDateTime.of(2026, 9, 28, hour, minute, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
  }

  @Test
  void feExample1PausedTimeIsRemoved() {
    // FE 예1) 19:00 시작 → 19:20 일시정지 → 20:00 다시 시작 → 20:10 정지
    //         끝 − 시작 = 70분, 일시정지 40분 → 30분
    assertThat(StudyTime.studyMillis(at(19, 0), at(20, 10), 40 * MINUTE)).isEqualTo(30 * MINUTE);
  }

  @Test
  void feExample2NoPause() {
    // FE 예2) 일시정지 기록이 없는 10:00 ~ 10:50 → 50분
    assertThat(StudyTime.studyMillis(at(10, 0), at(10, 50), 0)).isEqualTo(50 * MINUTE);
  }

  @Test
  void feExample3NegativePauseIsIgnored() {
    // FE 예3) 일시정지가 -5분 (이상한 값), 10:00 ~ 10:50 → 50분 (빼지 않음)
    assertThat(StudyTime.studyMillis(at(10, 0), at(10, 50), -5 * MINUTE)).isEqualTo(50 * MINUTE);
  }

  @Test
  void feExample4PauseLongerThanTheRecordIsIgnored() {
    // FE 예4) 일시정지가 90분인데 기록은 10:00 ~ 10:50 (50분짜리) → 50분 (빼지 않음)
    assertThat(StudyTime.studyMillis(at(10, 0), at(10, 50), 90 * MINUTE)).isEqualTo(50 * MINUTE);
  }

  @Test
  void feExample5EndBeforeStartIsZero() {
    // FE 예5) 끝이 시작보다 빠른 기록 → 0
    assertThat(StudyTime.studyMillis(at(11, 0), at(10, 0), 0)).isZero();
  }

  @Test
  void sameStartAndEndIsZero() {
    // 시작과 끝이 같은 기록도 0 입니다.
    assertThat(StudyTime.studyMillis(at(10, 0), at(10, 0), 0)).isZero();
  }

  @Test
  void pausedTheWholeTimeIsZero() {
    // 처음부터 끝까지 일시정지였으면(일시정지 = 끝 − 시작) 0 입니다. 이 값까지는 믿을 수 있는 값으로 봅니다.
    assertThat(StudyTime.studyMillis(at(10, 0), at(10, 50), 50 * MINUTE)).isZero();
  }

  @Test
  void toMinutesRoundsHalfUp() {
    // 30초 미만은 버리고, 30초부터는 올립니다.
    assertThat(StudyTime.toMinutes(0)).isZero();
    assertThat(StudyTime.toMinutes(29_999)).isZero();
    assertThat(StudyTime.toMinutes(30_000)).isEqualTo(1);
    assertThat(StudyTime.toMinutes(90_000)).isEqualTo(2);
    assertThat(StudyTime.toMinutes(190 * MINUTE)).isEqualTo(190);
  }

  @Test
  void addMillisFirstThenRoundOnce() {
    // 20분 24초(= 20.4분)짜리 기록 3개. 밀리초로 더한 뒤 한 번만 반올림하면 61분입니다.
    long oneRecord = 20 * MINUTE + 24_000;
    assertThat(StudyTime.toMinutes(oneRecord * 3)).isEqualTo(61);
    // 기록마다 반올림해서 더하면 20 + 20 + 20 = 60분이 되어 1분이 사라집니다. 이렇게 쓰면 안 됩니다.
    assertThat(StudyTime.toMinutes(oneRecord) * 3).isEqualTo(60);
  }

  @Test
  void matchesTheDaySummaryExample() {
    // FE 하루 요약(getDaySummary) 예4) 10:00~11:00 과 14:00~16:10 → 순공 60 + 130 = 190분
    long total =
        StudyTime.studyMillis(at(10, 0), at(11, 0), 0)
            + StudyTime.studyMillis(at(14, 0), at(16, 10), 0);
    assertThat(StudyTime.toMinutes(total)).isEqualTo(190);
    // 그날 계획이 240분이었다면 달성률은 79% 입니다. (docs/API.md 5장의 예시와 이어집니다)
    assertThat(AchievementRate.percentOf(190, 240)).isEqualTo(79);
  }
}
