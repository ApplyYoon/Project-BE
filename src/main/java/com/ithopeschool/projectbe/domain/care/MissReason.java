package com.ithopeschool.projectbe.domain.care;

// 계획을 못 채운 주된 이유입니다. 학생이 체크인에서 버튼으로 고르는 7개와 같습니다.
// FE 의 MissReason('tired' | 'event' | 'phone' | 'tooMuch' | 'unwilling' | 'sick' | 'etc') 과 같은
// 순서·같은 뜻입니다.
//
// 체크인을 저장하는 기능(이슈 #24)이 생기면 그쪽에서도 이 값을 같이 씁니다.
public enum MissReason {
  // 피곤
  TIRED,
  // 약속·행사
  EVENT,
  // 폰·SNS
  PHONE,
  // 계획 과다
  TOO_MUCH,
  // 하기 싫음
  UNWILLING,
  // 아픔
  SICK,
  // 기타
  ETC
}
