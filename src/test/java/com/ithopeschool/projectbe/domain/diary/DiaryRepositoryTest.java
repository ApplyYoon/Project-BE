package com.ithopeschool.projectbe.domain.diary;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 일기를 실제 DB(H2)에 넣고 꺼내고 지워 봅니다. 딸린 표(감정·원인·키워드)가 같이 지워지는지 직접 셉니다.
@DataJpaTest
class DiaryRepositoryTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-24");

  @Autowired private DiaryRepository diaryRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 분석까지 마친 일기 하나를 저장합니다.
  private Diary saveAnalyzed(Long userId, LocalDate date, Instant writtenAt) {
    Diary diary = new Diary(userId, date, "폰을 보다가 수학을 못 했다", writtenAt);
    diary.recordAnalysis(
        EnumSet.of(EmotionTag.TIRED, EmotionTag.ANNOYED),
        EnumSet.of(MissReason.PHONE),
        new LinkedHashSet<>(List.of("수학", "폰")),
        writtenAt);
    return diaryRepository.saveAndFlush(diary);
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
  }

  @Test
  @DisplayName("저장한 일기와 뽑은 감정·원인·키워드를 그대로 꺼낸다")
  void savesAndReads() {
    Diary saved = saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T09:00:00Z"));
    entityManager.clear();

    Diary found = diaryRepository.findByIdAndUserId(saved.getId(), 1L).orElseThrow();

    assertThat(found.getContent()).isEqualTo("폰을 보다가 수학을 못 했다");
    assertThat(found.getEmotions()).containsExactlyInAnyOrder(EmotionTag.TIRED, EmotionTag.ANNOYED);
    assertThat(found.getCauses()).containsExactly(MissReason.PHONE);
    assertThat(found.getKeywords()).containsExactlyInAnyOrder("수학", "폰");
    assertThat(found.getAnalyzedAt()).isNotNull();
  }

  @Test
  @DisplayName("다른 사람의 번호로는 찾을 수 없다")
  void otherUserCannotFind() {
    Diary saved = saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T09:00:00Z"));

    assertThat(diaryRepository.findByIdAndUserId(saved.getId(), 2L)).isEmpty();
  }

  @Test
  @DisplayName("목록은 내 일기 중 기간 안의 것만, 최근에 쓴 것부터")
  void listsMineInRangeNewestFirst() {
    Diary morning = saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T01:00:00Z"));
    Diary evening = saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T12:00:00Z"));
    saveAnalyzed(1L, DAY.minusDays(10), Instant.parse("2026-10-14T09:00:00Z"));
    saveAnalyzed(2L, DAY, Instant.parse("2026-10-24T05:00:00Z"));

    List<Diary> found =
        diaryRepository.findByUserIdAndDiaryDateBetweenOrderByWrittenAtDesc(
            1L, DAY.minusDays(1), DAY);

    assertThat(found).extracting(Diary::getId).containsExactly(evening.getId(), morning.getId());
  }

  @Test
  @DisplayName("일기를 지우면 뽑은 감정·원인·키워드 줄도 같이 지워진다")
  void deleteRemovesExtractedData() {
    Diary mine = saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T09:00:00Z"));
    saveAnalyzed(2L, DAY, Instant.parse("2026-10-24T09:00:00Z"));

    diaryRepository.delete(mine);
    diaryRepository.flush();

    // 남의 일기 한 개의 줄만 남습니다. (감정 2 · 원인 1 · 키워드 2)
    assertThat(count("diaries")).isEqualTo(1);
    assertThat(count("diary_emotions")).isEqualTo(2);
    assertThat(count("diary_causes")).isEqualTo(1);
    assertThat(count("diary_keywords")).isEqualTo(2);
  }

  @Test
  @DisplayName("탈퇴처럼 userId 로 한꺼번에 지워도 딸린 줄이 같이 지워진다")
  void bulkDeleteByUserRemovesExtractedData() {
    saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T09:00:00Z"));
    saveAnalyzed(1L, DAY, Instant.parse("2026-10-24T10:00:00Z"));

    // AccountService.withdraw 와 같은 문장입니다.
    entityManager
        .createQuery("delete from Diary e where e.userId = :userId")
        .setParameter("userId", 1L)
        .executeUpdate();

    assertThat(count("diaries")).isZero();
    assertThat(count("diary_emotions")).isZero();
    assertThat(count("diary_causes")).isZero();
    assertThat(count("diary_keywords")).isZero();
  }
}
