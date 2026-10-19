package com.ithopeschool.projectbe.domain.school.dto;

import java.util.List;

// 학교 종류 + 학과 목록 응답입니다. 가입 화면에서 "무슨 과야?" 를 물을 때 씁니다. (2026-10-19)
//   특성화고   { "schoolCode": "7530560", "schoolType": "특성화고", "asksMajor": true,
//               "majors": ["웹프로그래밍과", "해킹방어과", "e-비즈니스과", "디지털콘텐츠과"], "complete": true }
//   일반고     { "schoolCode": "7530075", "schoolType": "일반고", "asksMajor": false,
//               "majors": [], "complete": true }
// - schoolType: NEIS 의 고등학교 구분 ('일반고' · '특성화고' · '특목고' · '자율고'). 모르면 빈 글자
// - asksMajor: 학과를 물어볼 학교인지. 특성화고와 마이스터고(특목고 중 전문계)만 true 입니다.
// - majors: 학과 이름, 겹치지 않게. asksMajor 가 false 면 빈 목록
// - complete: false 면 서버에 NEIS 키가 없어서 뒤가 잘렸을 수 있습니다. 화면은 "목록에 없어요(직접 쓰기)" 를 둡니다.
public record SchoolMajorsResponse(
    String schoolCode,
    String schoolType,
    boolean asksMajor,
    List<String> majors,
    boolean complete) {

  public static SchoolMajorsResponse withoutMajors(String schoolCode, String schoolType) {
    return new SchoolMajorsResponse(schoolCode, schoolType, false, List.of(), true);
  }

  public static SchoolMajorsResponse withMajors(
      String schoolCode, String schoolType, List<String> majors, boolean complete) {
    return new SchoolMajorsResponse(schoolCode, schoolType, true, List.copyOf(majors), complete);
  }
}
