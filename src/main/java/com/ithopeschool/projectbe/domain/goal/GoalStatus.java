package com.ithopeschool.projectbe.domain.goal;

// 목표의 상태. (이슈 #221) FE 의 GoalStatus('active' | 'done' | 'givenUp') 와 같은 세 개입니다.
//   ACTIVE: 진행 중 (동시에 3개까지)
//   DONE: 완료
//   GIVEN_UP: 포기 (포기하는 이유를 꼭 적습니다)
//
// FE 글자는 camelCase('givenUp')라서 이름을 소문자로 바꾸는 것만으로는 안 맞습니다. 그래서 글자를 따로 적어 둡니다.
//   GIVEN_UP.toValue()               -> "givenUp"
//   GoalStatus.fromValue("active")   -> ACTIVE
public enum GoalStatus {
  ACTIVE("active"),
  DONE("done"),
  GIVEN_UP("givenUp");

  // FE 와 주고받는 글자
  private final String value;

  GoalStatus(String value) {
    this.value = value;
  }

  // FE 와 주고받는 글자로 바꿉니다.
  public String toValue() {
    return value;
  }

  // FE 가 보낸 글자를 상태로 바꿉니다. 세 가지 중 하나가 아니면 null 입니다.
  public static GoalStatus fromValue(String value) {
    for (GoalStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    return null;
  }
}
