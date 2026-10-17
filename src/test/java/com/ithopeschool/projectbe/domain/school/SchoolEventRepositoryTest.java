package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 직접 쓴 쿼리 두 개(학교 번호 모으기 · 기간 지우기)가 진짜 DB(H2)에서 맞게 도는지 봅니다.
@DataJpaTest
class SchoolEventRepositoryTest {

  @Autowired private AccountRepository accountRepository;
  @Autowired private SchoolEventRepository schoolEventRepository;

  private static Account account(long userId, String schoolCode) {
    return new Account(
        userId, "u" + userId + "@test.com", null, null, schoolCode, "학교", false, Instant.EPOCH);
  }

  @Test
  @DisplayName("학생들이 고른 학교 번호를 겹치지 않게 모은다 (안 고른 계정은 뺌)")
  void distinctSchoolCodes() {
    accountRepository.save(account(1, "7530560"));
    accountRepository.save(account(2, "7530560"));
    accountRepository.save(account(3, "1234567"));
    accountRepository.save(account(4, null));

    assertThat(accountRepository.findDistinctSchoolCodes())
        .containsExactlyInAnyOrder("7530560", "1234567");
  }

  @Test
  @DisplayName("한 학교의 기간 일정만 지운다 (다른 학교 · 기간 밖은 남음)")
  void deleteRange() {
    schoolEventRepository.save(new SchoolEvent("A", LocalDate.of(2026, 10, 1), "정기시험", null, ""));
    schoolEventRepository.save(new SchoolEvent("A", LocalDate.of(2026, 12, 9), "정기시험", null, ""));
    schoolEventRepository.save(new SchoolEvent("B", LocalDate.of(2026, 10, 1), "정기시험", null, ""));

    schoolEventRepository.deleteRange("A", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));

    assertThat(schoolEventRepository.findAll())
        .extracting(SchoolEvent::getSchoolCode, SchoolEvent::getEventDate)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("A", LocalDate.of(2026, 12, 9)),
            org.assertj.core.groups.Tuple.tuple("B", LocalDate.of(2026, 10, 1)));
  }
}
