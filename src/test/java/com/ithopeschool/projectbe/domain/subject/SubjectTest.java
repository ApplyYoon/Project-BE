package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// subjects 테이블을 실제 DB(H2)에 만들고, 넣고 꺼내 보며 설계한 규칙이 지켜지는지 확인합니다.
@DataJpaTest
class SubjectTest {

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("저장하면 번호가 생기고, DB 에서 다시 꺼내도 값이 같다")
  void saveAndFind() {
    Subject saved = em.persistFlushFind(new Subject(1L, "수학", SubjectColor.BLUE));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getName()).isEqualTo("수학");
    assertThat(saved.getColor()).isEqualTo(SubjectColor.BLUE);
  }

  @ParameterizedTest
  @EnumSource(SubjectColor.class)
  @DisplayName("정해 둔 색 다섯 가지는 모두 저장하고 꺼낼 수 있다")
  void everyColorCanBeSaved(SubjectColor color) {
    Subject saved = em.persistFlushFind(new Subject(1L, "과목-" + color, color));

    assertThat(saved.getColor()).isEqualTo(color);
  }

  @Test
  @DisplayName("색은 숫자가 아니라 글자(BLUE)로 저장된다")
  void colorIsStoredAsText() {
    Subject saved = em.persistFlushFind(new Subject(1L, "영어", SubjectColor.BLUE));

    Object stored =
        em.getEntityManager()
            .createNativeQuery("select cast(color as varchar) from subjects where id = :id")
            .setParameter("id", saved.getId())
            .getSingleResult();
    assertThat(stored).isEqualTo("BLUE");
  }

  @Test
  @DisplayName("정해진 색 밖의 값은 SQL 로 직접 넣어도 DB 가 거절한다")
  void dbRejectsUnknownColor() {
    assertThatThrownBy(
            () ->
                em.getEntityManager()
                    .createNativeQuery(
                        "insert into subjects (user_id, name, color) values (1, '수학', 'PINK')")
                    .executeUpdate())
        .isInstanceOf(DataException.class)
        .hasMessageContaining("PINK");
  }

  @Test
  @DisplayName("같은 사용자가 같은 이름의 과목을 두 번 만들면 DB 가 거절한다")
  void sameUserSameNameRejected() {
    em.persistAndFlush(new Subject(1L, "수학", SubjectColor.RED));

    Subject duplicate = new Subject(1L, "수학", SubjectColor.BLUE);

    assertThatThrownBy(() -> em.persistAndFlush(duplicate))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("UK_SUBJECTS_USER_ID_NAME");
  }

  @Test
  @DisplayName("사용자가 다르면 같은 이름의 과목을 각자 만들 수 있다")
  void differentUsersCanUseSameName() {
    Subject mine = em.persistAndFlush(new Subject(1L, "수학", SubjectColor.RED));
    Subject others = em.persistAndFlush(new Subject(2L, "수학", SubjectColor.RED));

    assertThat(mine.getId()).isNotEqualTo(others.getId());
  }

  @Test
  @DisplayName("한 사용자는 이름이 다른 과목을 여러 개 만들 수 있다")
  void sameUserDifferentNamesAllowed() {
    em.persistAndFlush(new Subject(1L, "수학", SubjectColor.RED));
    em.persistAndFlush(new Subject(1L, "영어", SubjectColor.RED));

    assertThat(findByUserId(1L)).extracting(Subject::getName).containsExactly("수학", "영어");
  }

  @Test
  @DisplayName("과목 이름은 30자까지 저장된다")
  void nameMaxLength() {
    Subject saved = em.persistFlushFind(new Subject(1L, "가".repeat(30), SubjectColor.GREEN));

    assertThat(saved.getName()).hasSize(30);
  }

  @Test
  @DisplayName("과목 이름이 31자면 DB 가 거절한다")
  void nameTooLong() {
    Subject tooLong = new Subject(1L, "가".repeat(31), SubjectColor.GREEN);

    assertThatThrownBy(() -> em.persistAndFlush(tooLong))
        .isInstanceOf(DataException.class)
        .hasMessageContaining("NAME");
  }

  @Test
  @DisplayName("이름이 비어 있으면(null) 저장되지 않는다")
  void nameRequired() {
    Subject noName = new Subject(1L, null, SubjectColor.RED);

    assertThatThrownBy(() -> em.persistAndFlush(noName))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("NAME");
  }

  @Test
  @DisplayName("색이 비어 있으면(null) 저장되지 않는다")
  void colorRequired() {
    Subject noColor = new Subject(1L, "수학", null);

    assertThatThrownBy(() -> em.persistAndFlush(noColor))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("COLOR");
  }

  @Test
  @DisplayName("주인(userId)이 없는 과목은 저장되지 않는다")
  void userIdRequired() {
    Subject noOwner = new Subject(null, "수학", SubjectColor.RED);

    assertThatThrownBy(() -> em.persistAndFlush(noOwner))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("USER_ID");
  }

  @Test
  @DisplayName("userId 로 찾으면 내 과목만 나오고, 남의 과목은 섞이지 않는다")
  void findsOnlyMySubjects() {
    em.persistAndFlush(new Subject(1L, "수학", SubjectColor.RED));
    em.persistAndFlush(new Subject(2L, "국어", SubjectColor.BLUE));
    em.persistAndFlush(new Subject(1L, "영어", SubjectColor.GREEN));
    em.clear();

    assertThat(findByUserId(1L)).extracting(Subject::getName).containsExactly("수학", "영어");
    assertThat(findByUserId(2L)).extracting(Subject::getName).containsExactly("국어");
    assertThat(findByUserId(999L)).isEmpty();
  }

  // 나중에 Repository 가 만들 쿼리와 같은 모양입니다: where user_id = ?
  private List<Subject> findByUserId(Long userId) {
    return em.getEntityManager()
        .createQuery(
            "select s from Subject s where s.userId = :userId order by s.id", Subject.class)
        .setParameter("userId", userId)
        .getResultList();
  }
}
