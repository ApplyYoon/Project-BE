package com.ithopeschool.projectbe.domain.insight;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 14일 차 첫 리포트까지 남은 날을 셉니다. (이슈 #35 "카운트다운", FE #85)
//
// 정해 둔 규칙 — FE 홈 화면(HomePage.tsx 의 FIRST_REPORT_DAYS · countDays)과 같습니다.
//   1. 시작하는 날 = 첫 기록 날짜. 공부 계획의 날짜와 공부 기록을 시작한 날(새벽 4시 기준) 중에서 가장 이른 날입니다.
//   2. 지난 날(daysPassed) = 첫 기록 날짜부터 오늘까지, 두 날 모두 포함해서 센 날 수. 첫날이 1일입니다.
//      14일을 넘어도 14로 멈춥니다. 첫 기록 날짜가 오늘보다 뒤(미리 세운 계획)여도 0 아래로 내려가지 않습니다.
//   3. 남은 날(daysLeft) = 14 - 지난 날. 0 이면 첫 리포트를 볼 수 있습니다(ready).
//   4. 기록이 하나도 없으면 첫 기록 날짜는 null, 지난 날 0, 남은 날 14 입니다.
//
// 예시 (오늘 10/17)
//   첫 기록 10/17  →  지난 1일 · 남은 13일
//   첫 기록 10/04  →  지난 14일 · 남은 0일 · ready
//   기록 없음      →  지난 0일 · 남은 14일
// FE 와 한쪽만 고치면 홈 화면과 서버의 숫자가 어긋납니다. 고칠 때는 같이 고쳐 주세요.
public final class FirstReportCountdown {

  // 며칠 동안 기록이 쌓여야 첫 리포트를 보여줄지
  public static final int TOTAL_DAYS = 14;

  // firstRecordDate: 첫 기록 날짜 (없으면 null) / daysPassed: 지난 날 / daysLeft: 남은 날 / ready: 첫 리포트를 볼 수 있으면
  // true
  public record Status(LocalDate firstRecordDate, int daysPassed, int daysLeft, boolean ready) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private FirstReportCountdown() {}

  public static Status of(LocalDate firstRecordDate, LocalDate today) {
    if (firstRecordDate == null) {
      return new Status(null, 0, TOTAL_DAYS, false);
    }
    // between 은 끝 날을 빼고 세서, 첫날을 1일로 치려고 1 을 더합니다.
    long counted = ChronoUnit.DAYS.between(firstRecordDate, today) + 1;
    int daysPassed = (int) Math.max(0, Math.min(TOTAL_DAYS, counted));
    int daysLeft = TOTAL_DAYS - daysPassed;
    return new Status(firstRecordDate, daysPassed, daysLeft, daysLeft == 0);
  }
}
