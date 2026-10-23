package com.ithopeschool.projectbe.domain.school.dto;

import java.util.List;

// 내 학교 한 학년의 반 번호 목록 응답입니다. 설정의 "몇 학년 몇 반" 고르기 칸이 씁니다. (2026-10-23)
//   { "grade": 2, "classNos": [1, 2, 3, 4, 5, 6], "complete": true }
// - classNos: 작은 번호부터. 학교를 안 골랐거나 그 학년에 반이 없으면 빈 목록
// - complete: false 면 서버에 NEIS 키가 없어서, NEIS 가 알려 준 전체 반 개수로 1반부터 채운 목록입니다.
public record SchoolClassesResponse(int grade, List<Integer> classNos, boolean complete) {

  public static SchoolClassesResponse noSchool(int grade) {
    return new SchoolClassesResponse(grade, List.of(), true);
  }

  public static SchoolClassesResponse of(int grade, List<Integer> classNos, boolean complete) {
    return new SchoolClassesResponse(grade, List.copyOf(classNos), complete);
  }
}
