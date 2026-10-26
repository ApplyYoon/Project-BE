package com.ithopeschool.projectbe.domain.answercheck;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 채점 기록과 문제를 실제 DB(H2)에 넣고 꺼내 봅니다. 내 것만 · 그날 것만 나오는지 확인합니다.
@DataJpaTest
class AnswerCheckRepositoryTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-24");
  private static final Long ME = 1L;
  private static final Long OTHER = 2L;

  @Autowired private AnswerCheckRepository answerCheckRepository;
  @Autowired private AnswerCheckItemRepository answerCheckItemRepository;
  @Autowired private EntityManager entityManager;

  private AnswerCheck saveCheck(Long userId, LocalDate date, String createdAt) {
    return answerCheckRepository.saveAndFlush(
        new AnswerCheck(userId, date, AnswerCheckMode.MARKED, "수학", Instant.parse(createdAt)));
  }

  private AnswerCheckItem saveItem(AnswerCheck check, int sortOrder) {
    return answerCheckItemRepository.saveAndFlush(
        new AnswerCheckItem(
            check.getId(),
            check.getUserId(),
            sortOrder,
            String.valueOf(sortOrder + 1),
            "x + 3 = 8 일 때 x 의 값은?",
            "문자와 식",
            "x = 5",
            null,
            MarkRead.O,
            GradeResult.CORRECT));
  }

  @Test
  @DisplayName("그날의 내 기록만, 최근에 올린 것부터 나온다 (남의 기록 · 다른 날 기록은 안 나온다)")
  void findsMyChecksOfTheDayNewestFirst() {
    AnswerCheck morning = saveCheck(ME, DAY, "2026-10-24T01:00:00Z");
    AnswerCheck evening = saveCheck(ME, DAY, "2026-10-24T11:00:00Z");
    saveCheck(ME, DAY.minusDays(1), "2026-10-23T11:00:00Z");
    saveCheck(OTHER, DAY, "2026-10-24T12:00:00Z");

    List<AnswerCheck> found =
        answerCheckRepository.findByUserIdAndCheckDateOrderByCreatedAtDescIdDesc(ME, DAY);

    assertThat(found)
        .extracting(AnswerCheck::getId)
        .containsExactly(evening.getId(), morning.getId());
  }

  @Test
  @DisplayName("기간의 내 기록만, 최근 날짜부터 · 같은 날은 최근에 올린 것부터 나온다 (양 끝 날짜 포함)")
  void findsMyChecksBetweenNewestFirst() {
    LocalDate from = DAY.minusDays(6);
    // 기간 안 (양 끝 포함)
    AnswerCheck firstDay = saveCheck(ME, from, "2026-10-18T05:00:00Z");
    AnswerCheck midMorning = saveCheck(ME, DAY.minusDays(3), "2026-10-21T01:00:00Z");
    AnswerCheck midEvening = saveCheck(ME, DAY.minusDays(3), "2026-10-21T11:00:00Z");
    AnswerCheck lastDay = saveCheck(ME, DAY, "2026-10-24T01:00:00Z");
    // 기간 밖 · 남의 기록
    saveCheck(ME, from.minusDays(1), "2026-10-17T05:00:00Z");
    saveCheck(ME, DAY.plusDays(1), "2026-10-25T05:00:00Z");
    saveCheck(OTHER, DAY.minusDays(3), "2026-10-21T12:00:00Z");

    List<AnswerCheck> found =
        answerCheckRepository
            .findByUserIdAndCheckDateBetweenOrderByCheckDateDescCreatedAtDescIdDesc(ME, from, DAY);

    assertThat(found)
        .extracting(AnswerCheck::getId)
        .containsExactly(lastDay.getId(), midEvening.getId(), midMorning.getId(), firstDay.getId());
  }

  @Test
  @DisplayName("남의 기록 번호로는 기록을 찾을 수 없다")
  void cannotFindOthersCheck() {
    AnswerCheck others = saveCheck(OTHER, DAY, "2026-10-24T01:00:00Z");

    assertThat(answerCheckRepository.findByIdAndUserId(others.getId(), ME)).isEmpty();
    assertThat(answerCheckRepository.findByIdAndUserId(others.getId(), OTHER)).isPresent();
  }

  @Test
  @DisplayName("여러 기록의 문제를 한 번에 꺼내면 기록 번호 · 사진 속 순서대로, 내 것만 나온다")
  void findsItemsOfManyChecksInOrder() {
    AnswerCheck first = saveCheck(ME, DAY, "2026-10-24T01:00:00Z");
    AnswerCheck second = saveCheck(ME, DAY, "2026-10-24T02:00:00Z");
    AnswerCheck others = saveCheck(OTHER, DAY, "2026-10-24T03:00:00Z");
    AnswerCheckItem b = saveItem(first, 1);
    AnswerCheckItem a = saveItem(first, 0);
    AnswerCheckItem c = saveItem(second, 0);
    saveItem(others, 0);
    entityManager.clear();

    List<AnswerCheckItem> found =
        answerCheckItemRepository.findByUserIdAndAnswerCheckIdInOrderByAnswerCheckIdAscSortOrderAsc(
            ME, List.of(first.getId(), second.getId(), others.getId()));

    assertThat(found)
        .extracting(AnswerCheckItem::getId)
        .containsExactly(a.getId(), b.getId(), c.getId());
  }

  @Test
  @DisplayName("문제 하나는 그 기록 번호와 내 번호가 모두 맞아야 찾는다")
  void findsItemOnlyInItsCheckAndOwner() {
    AnswerCheck mine = saveCheck(ME, DAY, "2026-10-24T01:00:00Z");
    AnswerCheck another = saveCheck(ME, DAY, "2026-10-24T02:00:00Z");
    AnswerCheckItem item = saveItem(mine, 0);

    assertThat(
            answerCheckItemRepository.findByIdAndAnswerCheckIdAndUserId(
                item.getId(), mine.getId(), ME))
        .isPresent();
    assertThat(
            answerCheckItemRepository.findByIdAndAnswerCheckIdAndUserId(
                item.getId(), another.getId(), ME))
        .isEmpty();
    assertThat(
            answerCheckItemRepository.findByIdAndAnswerCheckIdAndUserId(
                item.getId(), mine.getId(), OTHER))
        .isEmpty();
  }

  @Test
  @DisplayName("판정을 고치면 지금 판정 · 정답 · 고친 시각이 저장되고 AI 처음 판정은 그대로다")
  void editKeepsAiResult() {
    AnswerCheckItem item = saveItem(saveCheck(ME, DAY, "2026-10-24T01:00:00Z"), 0);
    item.edit(GradeResult.WRONG, "x = 7", Instant.parse("2026-10-24T02:00:00Z"));
    answerCheckItemRepository.saveAndFlush(item);
    entityManager.clear();

    AnswerCheckItem found = answerCheckItemRepository.findById(item.getId()).orElseThrow();

    assertThat(found.getAiResult()).isEqualTo(GradeResult.CORRECT);
    assertThat(found.getResult()).isEqualTo(GradeResult.WRONG);
    assertThat(found.getCorrectAnswer()).isEqualTo("x = 7");
    assertThat(found.getEditedAt()).isEqualTo(Instant.parse("2026-10-24T02:00:00Z"));
    assertThat(found.getMarkRead()).isEqualTo(MarkRead.O);
  }
}
