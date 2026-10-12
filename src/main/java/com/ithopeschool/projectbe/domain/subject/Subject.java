package com.ithopeschool.projectbe.domain.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 과목 테이블. 한 줄 = 한 사용자가 만든 과목 하나. (FE 의 Subject)
// 같은 사용자 안에서는 같은 이름의 과목을 두 번 만들 수 없습니다. (이름이 같아도 사용자가 다르면 괜찮습니다)
@Entity
@Table(
    name = "subjects",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_subjects_user_id_name",
            columnNames = {"user_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject {

  // 과목을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 과목의 주인(users.id). 다른 사람의 과목이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 화면에 보이는 과목 이름. 예: "수학"
  @Column(nullable = false, length = 30)
  private String name;

  // 과목 태그에 칠할 색. EnumType.STRING 이라 "BLUE" 같은 글자로 저장됩니다. (숫자로 저장하면 순서를 바꿀 때 데이터가 꼬입니다)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SubjectColor color;

  public Subject(Long userId, String name, SubjectColor color) {
    this.userId = userId;
    this.name = name;
    this.color = color;
  }

  // 이름과 색을 바꿉니다. (@Setter 를 쓰지 않고, 바꿀 수 있는 값만 이름을 지어 메서드로 엽니다)
  public void changeName(String name) {
    this.name = name;
  }

  public void changeColor(SubjectColor color) {
    this.color = color;
  }
}
