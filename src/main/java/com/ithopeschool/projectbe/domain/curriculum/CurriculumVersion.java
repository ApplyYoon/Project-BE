package com.ithopeschool.projectbe.domain.curriculum;

import java.time.LocalDate;
import java.util.Optional;

// 학생이 어느 교육과정으로 배우는지 정합니다. (2026-10-18 과목 쌤 · 확인 문제)
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
//
// ■ 규칙 (교육부 고시 제2022-33호 부칙: 2022 개정 교육과정을 학년마다 차례로 시행)
//   고1 은 2025학년도부터, 고2 는 2026학년도부터, 고3 은 2027학년도부터 2022 개정이고, 그 전에는 2015 개정입니다.
//   학년도는 3월 1일에 바뀝니다. (2027년 2월은 아직 2026학년도)
//
//   of(2, 2026-10-18)  →  "2022"   (2026학년도 고2)
//   of(3, 2026-10-18)  →  "2015"   (고3 은 2027학년도부터)
//   of(3, 2027-03-02)  →  "2022"
//   of(null, …)        →  빈 값    (학년을 모름 · N수 · 대학생)
public final class CurriculumVersion {

  public static final String V2022 = "2022";
  public static final String V2015 = "2015";

  // 2022 개정이 고1 에 처음 들어온 학년도. 고2 는 1년 뒤, 고3 은 2년 뒤입니다.
  static final int FIRST_YEAR_FOR_GRADE_1 = 2025;

  private CurriculumVersion() {}

  // 학년(1~3)과 날짜로 교육과정 버전. 학년이 없거나 1~3 이 아니면 빈 값입니다.
  public static Optional<String> of(Integer grade, LocalDate date) {
    if (grade == null || grade < 1 || grade > 3) {
      return Optional.empty();
    }
    int firstYear = FIRST_YEAR_FOR_GRADE_1 + (grade - 1);
    return Optional.of(schoolYearOf(date) >= firstYear ? V2022 : V2015);
  }

  // 날짜가 속한 학년도. 3월부터 그해, 1 · 2월은 지난해입니다.
  static int schoolYearOf(LocalDate date) {
    return date.getMonthValue() >= 3 ? date.getYear() : date.getYear() - 1;
  }

  // 대본에 쓰는 이름. 예) "2022 개정 교육과정"
  public static String label(String version) {
    return version + " 개정 교육과정";
  }
}
