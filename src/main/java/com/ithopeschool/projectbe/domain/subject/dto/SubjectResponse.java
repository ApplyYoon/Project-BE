package com.ithopeschool.projectbe.domain.subject.dto;

import com.ithopeschool.projectbe.domain.subject.Subject;

// 과목 하나를 FE 에 알려 주는 모양입니다. FE 의 Subject({ id, name, color })와 같습니다.
//   { "id": "3", "name": "수학", "color": "blue" }
// - id 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - color 는 DB 에 저장된 BLUE 가 아니라 FE 가 쓰는 소문자(blue)로 보냅니다.
// - 누구의 과목인지(userId)는 담지 않습니다.
public record SubjectResponse(String id, String name, String color) {

  public static SubjectResponse from(Subject subject) {
    return new SubjectResponse(
        String.valueOf(subject.getId()), subject.getName(), subject.getColor().toValue());
  }
}
