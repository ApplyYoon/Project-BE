package com.ithopeschool.projectbe.domain.exam;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 시험 날짜로 하는 계산을 모은 곳입니다. (이슈 #16 시험 점수 저장, #45 의 D-day)
//
//   시험까지 며칠 남았나        daysUntil      오늘이면 0, 내일이면 1, 어제면 -1
//   D-day 글자                 text           12 → "D-12" / 0 → "D-day" / -3 → "D+3"
//   실제 점수를 넣어도 되나     canEnterActualScore   시험 다음 날부터
//   다가오는 시험 · 지난 시험   split
//
// 정해 둔 규칙
//   1. 시각이 아니라 날짜끼리 비교합니다. 밤 11시에 봐도 "내일 시험" 은 1 입니다.
//   2. 오늘은 하루의 기준(새벽 4시)으로 정합니다. 새벽 2시에는 아직 어제라서 "오늘 시험" 도 1 입니다. (DayRule)
//   3. 실제 점수는 시험 날짜가 지난 뒤(다음 날부터) 넣을 수 있습니다. 시험 당일에는 아직 넣을 수 없습니다.
//      (시험 전에 적은 예상 점수를, 결과를 보고 나서 고치지 못하게 하려는 것입니다)
//   4. 다가오는 시험은 오늘 포함, 가까운 날짜가 앞에 옵니다. 지난 시험은 어제 이전, 최근 날짜가 앞에 옵니다.
//      날짜가 같은 시험끼리는 받은 순서를 지킵니다.
//   5. 점수는 0 ~ 100 사이의 정수입니다.
//
// FE 의 getDaysUntil · formatDday · splitExams · parseScore (src/lib/exams.ts),
// getFirstMe 의 "지난 시험은 남은 날이 없다" (src/lib/firstMe.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 D-day 가 어긋나지 않습니다.
public final class ExamDday {

  // 점수의 범위입니다. (FE 의 parseScore 와 같습니다)
  public static final int MIN_SCORE = 0;
  public static final int MAX_SCORE = 100;

  // 시험 하나에서 필요한 값만 모은 것입니다.
  // id: 시험 번호 / date: 시험 날짜
  // (시험 테이블은 아직 없어서, 엔티티가 아니라 이 계산에 필요한 두 값만 받습니다)
  public record ExamDate(Long id, LocalDate date) {}

  // 시험을 둘로 나눈 결과입니다.
  // upcoming: 다가오는 시험(오늘 포함). 가까운 날짜가 앞 / past: 지난 시험. 최근 날짜가 앞
  public record Split(List<ExamDate> upcoming, List<ExamDate> past) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private ExamDday() {}

  // 지금(now)부터 시험 날짜까지 며칠 남았는지 셉니다. (규칙 1, 2)
  //
  //   시험 10/17, 지금 10/16 밤 11시      →  1
  //   시험 10/17, 지금 10/17 새벽 2시     →  1   (새벽 4시 전이라 아직 10/16)
  //   시험 10/17, 지금 10/17 오전 9시     →  0
  public static int daysUntil(LocalDate examDate, Instant now) {
    return daysUntil(examDate, DayRule.dateOf(now));
  }

  // 오늘(today)부터 시험 날짜까지 며칠 남았는지 셉니다. 오늘이면 0, 내일이면 1, 어제면 -1 입니다.
  public static int daysUntil(LocalDate examDate, LocalDate today) {
    return (int) ChronoUnit.DAYS.between(today, examDate);
  }

  // 시험까지 남은 날을 돌려줍니다. 오늘이면 0 이고, 이미 지난 시험은 "남은 날" 이 없어서 null 입니다.
  // 홈의 "처음의 나" 카드와 월간 리포트의 D-day 처럼, 지난 시험을 보여주지 않는 자리에 씁니다.
  //
  //   오늘 10/10, 시험 11/18  →  39
  //   오늘 10/10, 시험 10/01  →  null
  public static Integer daysLeft(LocalDate examDate, LocalDate today) {
    int days = daysUntil(examDate, today);
    return days >= 0 ? days : null;
  }

  // D-day 글자를 만듭니다.
  //
  //   12 → "D-12"   /   0 → "D-day"   /   -3 → "D+3" (지난 시험)
  public static String text(int daysUntil) {
    if (daysUntil == 0) {
      return "D-day";
    }
    return daysUntil > 0 ? "D-" + daysUntil : "D+" + -daysUntil;
  }

  // 이 시험의 실제 점수를 넣어도 되면 true 입니다. 시험 날짜가 지난 뒤(다음 날부터)입니다. (규칙 3)
  //
  //   시험 10/17  →  10/17 까지는 false, 10/18 부터 true
  public static boolean canEnterActualScore(LocalDate examDate, LocalDate today) {
    return daysUntil(examDate, today) < 0;
  }

  // 시험을 "다가오는 시험" 과 "지난 시험" 으로 나눕니다. (규칙 4)
  public static Split split(List<ExamDate> exams, LocalDate today) {
    List<ExamDate> upcoming = new ArrayList<>();
    List<ExamDate> past = new ArrayList<>();
    for (ExamDate exam : exams) {
      if (daysUntil(exam.date(), today) >= 0) {
        upcoming.add(exam);
      } else {
        past.add(exam);
      }
    }
    // sort 는 값이 같으면 원래 순서를 지킵니다. 그래서 같은 날짜의 시험은 받은 순서 그대로입니다.
    upcoming.sort(Comparator.comparing(ExamDate::date));
    past.sort(Comparator.comparing(ExamDate::date).reversed());
    return new Split(upcoming, past);
  }

  // 점수가 0 ~ 100 사이면 true 입니다. 안 적은 점수(null)는 false 입니다. (규칙 5)
  //
  //   85 → true   /   0 → true   /   100 → true   /   101 → false   /   -1 → false
  public static boolean isValidScore(Integer score) {
    return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
  }
}
