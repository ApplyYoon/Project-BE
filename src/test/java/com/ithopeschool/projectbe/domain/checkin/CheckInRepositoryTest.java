package com.ithopeschool.projectbe.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// 체크인을 실제 DB(H2)에 넣고 꺼내 봅니다.
@DataJpaTest
class CheckInRepositoryTest {

  @Autowired private CheckInRepository checkInRepository;

  private CheckIn save(Long userId, String date, Integer mood) {
    return checkInRepository.saveAndFlush(
        new CheckIn(
            userId,
            LocalDate.parse(date),
            CheckInSource.QUICK,
            null,
            mood,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  @Test
  @DisplayName("모든 칸이 다 차 있어도, source 말고 다 비어 있어도 저장되고 그대로 꺼내진다")
  void savesFullAndEmptyDays() {
    checkInRepository.saveAndFlush(
        new CheckIn(
            1L,
            LocalDate.parse("2026-10-17"),
            CheckInSource.MANUAL,
            4,
            3,
            MissReason.ETC,
            "동아리 발표",
            120,
            "폰을 보고 싶으면",
            "물 한 잔 마신다",
            true));
    save(1L, "2026-10-16", null);

    CheckIn full =
        checkInRepository
            .findByUserIdAndCheckInDate(1L, LocalDate.parse("2026-10-17"))
            .orElseThrow();
    assertThat(full.getSource()).isEqualTo(CheckInSource.MANUAL);
    assertThat(full.getCondition()).isEqualTo(4);
    assertThat(full.getMood()).isEqualTo(3);
    assertThat(full.getMissReason()).isEqualTo(MissReason.ETC);
    assertThat(full.getMissReasonNote()).isEqualTo("동아리 발표");
    assertThat(full.getExpectedMinutes()).isEqualTo(120);
    assertThat(full.getPromiseIf()).isEqualTo("폰을 보고 싶으면");
    assertThat(full.getPromiseThen()).isEqualTo("물 한 잔 마신다");
    assertThat(full.getSuggestionKept()).isTrue();

    CheckIn empty =
        checkInRepository
            .findByUserIdAndCheckInDate(1L, LocalDate.parse("2026-10-16"))
            .orElseThrow();
    assertThat(empty.getSource()).isEqualTo(CheckInSource.QUICK);
    assertThat(empty.getMood()).isNull();
    assertThat(empty.getSuggestionKept()).isNull();
  }

  @Test
  @DisplayName("한 사람이 같은 날짜로 두 줄을 만들 수는 없다 (다른 사람이면 괜찮다)")
  void oneRowPerUserPerDay() {
    save(1L, "2026-10-17", 3);
    save(2L, "2026-10-17", 4);

    assertThatThrownBy(() -> save(1L, "2026-10-17", 5))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("기간으로 찾으면 체크인한 날만, 내 것만, 날짜 순서로 나온다")
  void findsOnlyMyDaysInOrder() {
    save(1L, "2026-10-15", 3);
    save(1L, "2026-10-13", 1);
    save(1L, "2026-10-20", 5);
    save(2L, "2026-10-14", 2);

    List<CheckIn> found =
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            1L, LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-16"));

    // 10/14·10/16 은 체크인이 없어서 목록에 없습니다. 10/20 은 기간 밖, 2번 사용자 것은 남의 것입니다.
    assertThat(found)
        .extracting(CheckIn::getCheckInDate)
        .containsExactly(LocalDate.parse("2026-10-13"), LocalDate.parse("2026-10-15"));
  }

  @Test
  @DisplayName("감정 태그가 딸린 테이블에 저장되고 그대로 꺼내진다. 태그가 없던 옛 체크인은 빈 목록이다")
  void savesAndLoadsEmotions() {
    CheckIn withTags = save(1L, "2026-10-16", 3);
    withTags.replaceEmotions(EnumSet.of(EmotionTag.MOTIVATED, EmotionTag.CALM));
    checkInRepository.saveAndFlush(withTags);
    save(1L, "2026-10-15", 4);

    List<CheckIn> found =
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

    assertThat(found.get(0).getEmotions()).isEmpty();
    assertThat(found.get(1).getEmotions())
        .containsExactlyInAnyOrder(EmotionTag.CALM, EmotionTag.MOTIVATED);
  }

  @Test
  @DisplayName("모두 지우기는 내 체크인만 지우고, 지운 줄 수를 돌려준다. 남의 체크인은 그대로다")
  void deletesOnlyMine() {
    save(1L, "2026-10-15", 3);
    save(1L, "2026-10-16", 4);
    save(2L, "2026-10-16", 5);

    assertThat(checkInRepository.deleteAllOfUser(1L)).isEqualTo(2);

    assertThat(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        .isEmpty();
    assertThat(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                2L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        .hasSize(1);
    // 지울 것이 없어도 에러 없이 0 입니다.
    assertThat(checkInRepository.deleteAllOfUser(1L)).isZero();
  }

  @Test
  @DisplayName("모두 지우기는 감정 태그(딸린 테이블)가 붙은 체크인도 지운다 (외래 키에 걸리지 않음)")
  void deleteAllAlsoRemovesEmotions() {
    CheckIn withTags = save(1L, "2026-10-16", 3);
    withTags.replaceEmotions(EnumSet.of(EmotionTag.STUCK, EmotionTag.TIRED));
    checkInRepository.saveAndFlush(withTags);
    CheckIn others = save(2L, "2026-10-16", 4);
    others.replaceEmotions(EnumSet.of(EmotionTag.CALM));
    checkInRepository.saveAndFlush(others);

    assertThat(checkInRepository.deleteAllOfUser(1L)).isEqualTo(1);

    assertThat(
            checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                2L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
        .singleElement()
        .satisfies(checkIn -> assertThat(checkIn.getEmotions()).containsExactly(EmotionTag.CALM));
  }
}
