package com.ithopeschool.projectbe.domain.subject;

// 과목 태그에 칠할 수 있는 색. 이 다섯 가지 말고는 DB 에 들어갈 수 없습니다.
// FE 의 SubjectColor('red' | 'orange' | 'green' | 'blue' | 'purple') 와 같은 다섯 개입니다.
// (FE 와 주고받을 때 소문자로 바꾸는 방법은 API 규칙 이슈 #5 에서 정합니다)
public enum SubjectColor {
  RED,
  ORANGE,
  GREEN,
  BLUE,
  PURPLE
}
