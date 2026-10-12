package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 스프링을 띄우지 않는 단위 테스트입니다. 받아 둔 AI 문단을 언제 다시 쓰고 언제 쓰지 않는지 봅니다. (이슈 #87)
class CoachParagraphStoreTest {

  private static final Long USER_ID = 7L;
  private static final String WEEK = "2026-10-05";
  private static final String SIGNATURE = "[완주율 55% 20번]|1|3";
  private static final String PARAGRAPH = "이번 주 완주율은 55% 야.";

  private final CoachParagraphStore store = new CoachParagraphStore();

  @Test
  @DisplayName("아무것도 기억하지 않았으면 비어 있다")
  void emptyAtFirst() {
    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE)).isEmpty();
  }

  @Test
  @DisplayName("기억해 둔 문단은 같은 사람·종류·기간·숫자로 물으면 그대로 나온다")
  void savedParagraphIsFound() {
    store.save(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE, PARAGRAPH);

    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE)).contains(PARAGRAPH);
  }

  @Test
  @DisplayName("사람·종류·기간·숫자 중 하나라도 다르면 나오지 않는다")
  void anyDifferenceMeansNotFound() {
    store.save(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE, PARAGRAPH);

    assertThat(store.find(8L, CoachKind.WEEKLY, WEEK, SIGNATURE)).isEmpty();
    assertThat(store.find(USER_ID, CoachKind.MONTHLY, WEEK, SIGNATURE)).isEmpty();
    assertThat(store.find(USER_ID, CoachKind.WEEKLY, "2026-09-28", SIGNATURE)).isEmpty();
    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, "[완주율 60% 20번]|1|3")).isEmpty();
  }

  @Test
  @DisplayName("같은 기간에 새 숫자로 받은 문단을 기억하면 옛 문단은 사라진다")
  void newerParagraphReplacesTheOldOne() {
    String newSignature = "[완주율 60% 20번]|1|3";
    String newParagraph = "이번 주 완주율은 60% 야.";
    store.save(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE, PARAGRAPH);

    store.save(USER_ID, CoachKind.WEEKLY, WEEK, newSignature, newParagraph);

    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, newSignature)).contains(newParagraph);
    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE)).isEmpty();
  }

  @Test
  @DisplayName("다른 기간의 문단은 서로 지우지 않는다 (지난주와 이번 주를 따로 기억한다)")
  void differentPeriodsAreKeptSeparately() {
    store.save(USER_ID, CoachKind.WEEKLY, "2026-09-28", SIGNATURE, "지난주 문단");
    store.save(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE, "이번 주 문단");

    assertThat(store.find(USER_ID, CoachKind.WEEKLY, "2026-09-28", SIGNATURE)).contains("지난주 문단");
    assertThat(store.find(USER_ID, CoachKind.WEEKLY, WEEK, SIGNATURE)).contains("이번 주 문단");
  }

  @Test
  @DisplayName("최대 개수만큼 찬 뒤 새 문단이 오면 전부 비우고 새 문단만 기억한다 (메모리가 끝없이 늘지 않는다)")
  void clearsEverythingWhenFull() {
    for (long userId = 1; userId <= CoachParagraphStore.MAX_SIZE; userId++) {
      store.save(userId, CoachKind.WEEKLY, WEEK, SIGNATURE, PARAGRAPH);
    }
    // 꽉 찼어도 이미 있는 자리를 바꾸는 것은 비우지 않습니다.
    store.save(1L, CoachKind.WEEKLY, WEEK, SIGNATURE, "바꾼 문단");
    assertThat(store.find(2L, CoachKind.WEEKLY, WEEK, SIGNATURE)).contains(PARAGRAPH);

    long newUserId = CoachParagraphStore.MAX_SIZE + 1L;
    store.save(newUserId, CoachKind.WEEKLY, WEEK, SIGNATURE, PARAGRAPH);

    assertThat(store.find(newUserId, CoachKind.WEEKLY, WEEK, SIGNATURE)).contains(PARAGRAPH);
    assertThat(store.find(1L, CoachKind.WEEKLY, WEEK, SIGNATURE)).isEmpty();
    assertThat(store.find(2L, CoachKind.WEEKLY, WEEK, SIGNATURE)).isEmpty();
  }
}
