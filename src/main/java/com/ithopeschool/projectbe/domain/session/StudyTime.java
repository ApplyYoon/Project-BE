package com.ithopeschool.projectbe.domain.session;

import java.time.Duration;
import java.time.Instant;

// "순공시간" 계산기입니다. (이슈 #14, #15)
//
//   순공시간 = 끝 시각 − 시작 시각 − 일시정지해 있던 시간
//
// 타이머 화면은 일시정지한 시간을 빼고 보여줍니다. 서버도 똑같이 빼야 화면에서 본 시간과 리포트의 시간이 같아집니다.
// 순공시간이 필요한 곳은 "끝 − 시작" 을 직접 계산하지 말고 모두 이 계산기를 씁니다.
//
//   19:00 ~ 20:10 (70분), 일시정지 40분      →  30분
//   10:00 ~ 10:50, 일시정지 0               →  50분
//   일시정지가 -5분 (이상한 값)              →  50분 (빼지 않음)
//   일시정지가 90분인데 기록은 50분짜리       →  50분 (빼지 않음)
//   끝이 시작보다 빠른 기록                  →  0
//
// FE 의 getStudyMs (src/lib/studyTime.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 숫자가 어긋나지 않습니다.
// (FE 는 노는 기록을 0 으로 칩니다. 서버에서는 노는 기록을 이 계산기에 넘기기 전에 뺍니다)
public final class StudyTime {

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private StudyTime() {}

  // 공부 기록 하나의 순공시간을 밀리초로 돌려줍니다.
  // pausedMillis: 시작 ~ 끝 사이에 일시정지해 있던 시간의 합(밀리초). 일시정지한 적이 없으면 0 을 넘깁니다.
  public static long studyMillis(Instant startedAt, Instant endedAt, long pausedMillis) {
    long totalMillis = Duration.between(startedAt, endedAt).toMillis();

    // 끝이 시작보다 빠르거나 같은 잘못된 기록은 0 입니다.
    if (totalMillis <= 0) {
      return 0;
    }
    // 일시정지 시간이 음수이거나 (끝 − 시작)보다 크면 믿을 수 없는 값이라 빼지 않습니다.
    if (pausedMillis < 0 || pausedMillis > totalMillis) {
      return totalMillis;
    }
    return totalMillis - pausedMillis;
  }

  // 밀리초를 분으로 바꿉니다. (반올림)
  // 기록 여러 개를 더할 때는 밀리초로 모두 더한 뒤 마지막에 한 번만 부릅니다.
  // 기록마다 반올림해서 더하면 합계가 화면과 어긋납니다. (20.4분 세 번 = 61분인데 20 + 20 + 20 = 60분이 됨)
  public static int toMinutes(long millis) {
    return (int) Math.round((double) millis / MILLIS_PER_MINUTE);
  }
}
