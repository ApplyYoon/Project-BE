package com.ithopeschool.projectbe.domain.curriculum;

// 성취기준 한 줄입니다. 교육부 고시 원문을 그대로 옮긴 파일(src/main/resources/curriculum/*.json)에서 읽습니다.
//   curriculum : 교육과정 버전. "2022" 는 2022 개정 교육과정 (CurriculumVersion)
//   subject    : 과목 이름 (예: 미적분Ⅰ)
//   code       : 성취기준 코드 (예: 12미적Ⅰ-01-02). 10 으로 시작하면 고1 공통 과목, 12 면 고2 · 고3 선택 과목
//   area       : 영역 · 단원 (예: 함수의 극한과 연속). 원문에 영역 이름이 따로 없는 과목은 빈 글자
//   text       : 성취기준 문장 (예: 함수의 극한에 대한 성질을 이해하고, 함수의 극한값을 구할 수 있다.)
public record AchievementStandard(
    String curriculum, String subject, String code, String area, String text) {}
