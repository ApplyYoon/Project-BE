package com.ithopeschool.projectbe.domain.curriculum;

import java.util.List;

// 한 학생이 지금 배우는 것: 학년 · 교육과정 버전 · 오늘 시간표 과목. (CurriculumService.contextOf)
// 과목 쌤 채팅을 열 때 한 번 만들어 채팅이 끝날 때까지 기억해 둡니다. (ChatScriptStore, 서버 메모리)
//   grade        : 1 ~ 3
//   version      : "2022" 또는 "2015" (CurriculumVersion)
//   todaySubjects: 오늘 시간표 과목 (반을 모르면 빈 목록)
public record CurriculumContext(int grade, String version, List<String> todaySubjects) {

  public CurriculumContext {
    todaySubjects = todaySubjects == null ? List.of() : List.copyOf(todaySubjects);
  }
}
