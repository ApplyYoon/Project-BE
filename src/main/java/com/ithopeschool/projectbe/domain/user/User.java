package com.ithopeschool.projectbe.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자 테이블. 한 줄 = 서비스를 쓰는 사람 한 명. (FE 의 AuthUser)
// 테이블 이름이 user 가 아니라 users 인 이유: user 는 DB 가 이미 쓰는 단어(예약어)라 에러가 납니다.
// 어떤 SNS 로 가입했는지 같은 로그인 정보는 #19 에서 필요한 만큼만 추가합니다.
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  // 사용자를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 화면에 보이는 이름. 실명이 아니라 별명입니다. (FE 의 AuthUser.name)
  @Column(nullable = false, length = 50)
  private String nickname;

  // 가입한 시각. 이 서비스를 쓴 지 몇 주 됐는지 계산할 때 씁니다.
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public User(String nickname) {
    this.nickname = nickname;
    this.createdAt = Instant.now();
  }

  // 별명을 바꿉니다. (@Setter 를 쓰지 않고, 바꿀 수 있는 값만 이름을 지어 메서드로 엽니다)
  public void changeNickname(String nickname) {
    this.nickname = nickname;
  }
}
