package com.ithopeschool.projectbe.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// @DataJpaTest 는 테스트용 DB(H2)에 users 테이블을 실제로 만들고, 넣고 꺼내 보는 테스트입니다.
// 각 테스트가 끝나면 넣은 데이터는 자동으로 되돌려집니다.
@DataJpaTest
class UserTest {

  // TestEntityManager 는 테스트에서 엔티티를 DB 에 직접 넣고 꺼내 보는 도구입니다.
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("저장하면 번호가 생기고, DB 에서 다시 꺼내도 값이 같다")
  void saveAndFind() {
    Instant before = Instant.now();

    // persistFlushFind: 저장 → DB 에 반영 → 메모리 비우기 → DB 에서 다시 꺼내기
    User saved = em.persistFlushFind(new User("솔민"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getNickname()).isEqualTo("솔민");
    // 가입 시각은 저장하는 순간으로 채워진다 (DB 가 마이크로초까지만 저장해서 1밀리초 여유를 둔다)
    assertThat(saved.getCreatedAt()).isBetween(before.minusMillis(1), Instant.now().plusMillis(1));
  }

  @Test
  @DisplayName("사용자마다 다른 번호를 받고, 번호는 점점 커진다")
  void idsAreDifferent() {
    User first = em.persistFlushFind(new User("하나"));
    User second = em.persistFlushFind(new User("둘"));

    assertThat(second.getId()).isGreaterThan(first.getId());
  }

  @Test
  @DisplayName("별명이 같은 사용자가 둘 있어도 된다 (별명은 중복 허용)")
  void sameNicknameAllowed() {
    em.persistAndFlush(new User("공부왕"));
    em.persistAndFlush(new User("공부왕"));

    Long count =
        em.getEntityManager()
            .createQuery("select count(u) from User u where u.nickname = '공부왕'", Long.class)
            .getSingleResult();
    assertThat(count).isEqualTo(2);
  }

  @Test
  @DisplayName("한글·이모지·특수문자 별명도 깨지지 않고 저장된다")
  void unicodeNickname() {
    String nickname = "공부하는 😀 Sol-min_01";

    User saved = em.persistFlushFind(new User(nickname));

    assertThat(saved.getNickname()).isEqualTo(nickname);
  }

  @Test
  @DisplayName("별명은 50자까지 저장된다")
  void nicknameMaxLength() {
    String fifty = "가".repeat(50);

    User saved = em.persistFlushFind(new User(fifty));

    assertThat(saved.getNickname()).hasSize(50);
  }

  @Test
  @DisplayName("별명이 51자면 DB 가 거절한다")
  void nicknameTooLong() {
    User tooLong = new User("가".repeat(51));

    // "Value too long for column NICKNAME" → DataException
    assertThatThrownBy(() -> em.persistAndFlush(tooLong))
        .isInstanceOf(DataException.class)
        .hasMessageContaining("NICKNAME");
  }

  @Test
  @DisplayName("별명이 비어 있으면(null) 저장되지 않는다")
  void nicknameRequired() {
    User noNickname = new User(null);

    // "NULL not allowed for column NICKNAME" → ConstraintViolationException
    assertThatThrownBy(() -> em.persistAndFlush(noNickname))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("NICKNAME");
  }
}
