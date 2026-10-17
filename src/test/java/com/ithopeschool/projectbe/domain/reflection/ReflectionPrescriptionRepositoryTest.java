package com.ithopeschool.projectbe.domain.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 성찰일기 처방을 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class ReflectionPrescriptionRepositoryTest {

  @Autowired private ReflectionPrescriptionRepository prescriptionRepository;

  private ReflectionPrescription save(Long userId, LocalDate startDate, MissReason reason) {
    return prescriptionRepository.saveAndFlush(
        new ReflectionPrescription(
            userId, startDate, startDate.plusDays(4), reason, 3, ReflectionQuestions.of(reason)));
  }

  @Test
  @DisplayName("기간 · 이유 · 질문 3개가 그대로 저장되고 꺼내진다")
  void savesPeriodReasonAndQuestions() {
    Long id = save(1L, LocalDate.of(2026, 10, 17), MissReason.TOO_MUCH).getId();

    ReflectionPrescription found = prescriptionRepository.findById(id).orElseThrow();

    assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 17));
    assertThat(found.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(found.getDays()).isEqualTo(5);
    assertThat(found.getMissReason()).isEqualTo(MissReason.TOO_MUCH);
    assertThat(found.getQuestions()).isEqualTo(ReflectionQuestions.of(MissReason.TOO_MUCH));
  }

  @Test
  @DisplayName("가장 최근 처방은 내 것 중 번호가 가장 큰 것이고, 남의 처방은 보이지 않는다")
  void latestIsMyNewest() {
    save(1L, LocalDate.of(2026, 10, 1), MissReason.PHONE);
    ReflectionPrescription second = save(1L, LocalDate.of(2026, 10, 14), MissReason.TIRED);
    save(2L, LocalDate.of(2026, 10, 16), MissReason.ETC);

    assertThat(prescriptionRepository.findFirstByUserIdOrderByIdDesc(1L).orElseThrow().getId())
        .isEqualTo(second.getId());
    assertThat(prescriptionRepository.findFirstByUserIdOrderByIdDesc(3L)).isEmpty();
  }
}
