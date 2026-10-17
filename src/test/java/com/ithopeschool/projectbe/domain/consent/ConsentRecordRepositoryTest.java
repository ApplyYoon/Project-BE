package com.ithopeschool.projectbe.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 동의 기록을 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class ConsentRecordRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  @Autowired private ConsentRecordRepository consentRecordRepository;

  @Test
  @DisplayName("동의 시각 · 문구 버전 · 만 14세 확인이 그대로 저장되고 꺼내진다")
  void savesEveryColumn() {
    ConsentRecord saved =
        consentRecordRepository.saveAndFlush(new ConsentRecord(1L, NOW, "v1", true));

    ConsentRecord found = consentRecordRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getUserId()).isEqualTo(1L);
    assertThat(found.getAgreedAt()).isEqualTo(NOW);
    assertThat(found.getTextVersion()).isEqualTo("v1");
    assertThat(found.getAgeConfirmedOver14()).isTrue();
  }

  @Test
  @DisplayName("만 14세 확인은 비어(null) 있어도 저장된다 (확인을 받기 전에 가입한 계정의 옛 동의)")
  void ageConfirmationMayBeEmpty() {
    ConsentRecord saved =
        consentRecordRepository.saveAndFlush(new ConsentRecord(1L, NOW, "v1", null));

    assertThat(
            consentRecordRepository.findById(saved.getId()).orElseThrow().getAgeConfirmedOver14())
        .isNull();
  }

  @Test
  @DisplayName("다시 동의하면 새 줄이 쌓이고, 가장 최근 것은 내 줄 중 번호가 가장 큰 것이다 (남의 줄은 섞이지 않음)")
  void historyPilesUpAndLatestIsMine() {
    consentRecordRepository.save(new ConsentRecord(1L, NOW, "v1", true));
    ConsentRecord second =
        consentRecordRepository.save(new ConsentRecord(1L, NOW.plusSeconds(60), "v2", true));
    consentRecordRepository.save(new ConsentRecord(2L, NOW.plusSeconds(120), "v3", true));

    assertThat(consentRecordRepository.findFirstByUserIdOrderByIdDesc(1L))
        .get()
        .extracting(ConsentRecord::getId)
        .isEqualTo(second.getId());
    assertThat(consentRecordRepository.findAll()).hasSize(3);
    assertThat(consentRecordRepository.findFirstByUserIdOrderByIdDesc(3L)).isEmpty();
  }
}
