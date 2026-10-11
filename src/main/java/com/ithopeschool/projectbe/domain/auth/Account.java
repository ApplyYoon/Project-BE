package com.ithopeschool.projectbe.domain.auth;

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

// 계정 테이블. 한 줄 = 이메일로 가입한 계정 하나. (로그인에 쓰는 정보 + 가입할 때 받은 정보)
// 화면에 보이는 별명은 users 테이블에 있고, 이 테이블과는 userId 로 이어집니다. 사용자 한 명에 계정은 하나입니다.
//
// ⚠️ 비밀번호는 그대로 저장하지 않습니다. PasswordHasher 가 되돌릴 수 없게 바꾼 값(passwordHash)만 저장합니다.
//    이 테이블의 값(이메일·실명·학교)은 개인정보라서 로그에 찍지 않고, API 응답에도 꼭 필요한 것만 담습니다.
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

  // 계정을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 계정의 주인. users 의 어느 줄인지 가리킵니다. 사용자 한 명에 계정은 하나라서 겹칠 수 없습니다.
  @Column(nullable = false, unique = true)
  private Long userId;

  // 로그인할 때 쓰는 이메일. 소문자로 바꿔 저장하고, 같은 이메일로 두 번 가입할 수 없습니다.
  @Column(nullable = false, unique = true, length = 100)
  private String email;

  // 비밀번호를 되돌릴 수 없게 바꾼 값. PasswordHasher 가 만듭니다. (진짜 비밀번호는 어디에도 저장하지 않습니다)
  @Column(nullable = false, length = 200)
  private String passwordHash;

  // 실명. (화면에 보이는 별명은 users.nickname 입니다)
  @Column(nullable = false, length = 20)
  private String name;

  // 고른 고등학교의 번호(NEIS 학교 번호). 고르지 않았으면 비어 있습니다(null).
  @Column(length = 20)
  private String schoolCode;

  // 고른 고등학교의 이름. 고르지 않았으면 비어 있습니다(null).
  @Column(length = 100)
  private String schoolName;

  // [선택 동의] 일기·감정 기록을 분석에 써도 된다고 했으면 true
  @Column(nullable = false)
  private boolean diaryAnalysisAgreed;

  // [필수 동의] 이용약관과 개인정보 수집·이용에 동의한 시각
  @Column(nullable = false, updatable = false)
  private Instant agreedAt;

  public Account(
      Long userId,
      String email,
      String passwordHash,
      String name,
      String schoolCode,
      String schoolName,
      boolean diaryAnalysisAgreed,
      Instant agreedAt) {
    this.userId = userId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.schoolCode = schoolCode;
    this.schoolName = schoolName;
    this.diaryAnalysisAgreed = diaryAnalysisAgreed;
    this.agreedAt = agreedAt;
  }
}
