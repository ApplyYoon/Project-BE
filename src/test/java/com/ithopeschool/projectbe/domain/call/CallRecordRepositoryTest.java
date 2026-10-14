package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.care.MissReason;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// CallRecordRepository 의 찾기 메서드와 통화 기록 테이블을 실제 DB(H2)에 넣고 꺼내 보며 확인합니다.
@DataJpaTest
class CallRecordRepositoryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

  @Autowired private CallRecordRepository callRecordRepository;

  // 그날 몇 시(UTC)에 시작한 통화 하나를 저장합니다.
  private CallRecord saveCall(Long userId, LocalDate date, int hour) {
    Instant startedAt = date.atTime(hour, 0).toInstant(ZoneOffset.UTC);
    return callRecordRepository.save(new CallRecord(userId, date, startedAt, null));
  }

  private List<Long> idsOf(List<CallRecord> calls) {
    return calls.stream().map(CallRecord::getId).toList();
  }

  @Test
  @DisplayName("from 날짜부터의 내 통화만, 늦게 시작한 것부터 돌려준다")
  void recentCallsNewestFirst() {
    CallRecord old = saveCall(1L, TODAY.minusDays(14), 10);
    CallRecord yesterday = saveCall(1L, TODAY.minusDays(1), 10);
    CallRecord todayMorning = saveCall(1L, TODAY, 1);
    CallRecord todayEvening = saveCall(1L, TODAY, 11);
    saveCall(2L, TODAY, 12);

    List<CallRecord> found =
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            1L, TODAY.minusDays(13));

    assertThat(idsOf(found))
        .containsExactly(todayEvening.getId(), todayMorning.getId(), yesterday.getId())
        .doesNotContain(old.getId());
  }

  @Test
  @DisplayName("시작 시각이 같으면 나중에 만든 것(번호가 큰 것)이 먼저다")
  void sameStartTimeUsesTheNewerId() {
    CallRecord first = saveCall(1L, TODAY, 10);
    CallRecord second = saveCall(1L, TODAY, 10);

    assertThat(
            idsOf(
                callRecordRepository
                    .findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(1L, TODAY)))
        .containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("번호로 찾을 때 남의 통화는 찾지 못한다")
  void findsOnlyMyCallById() {
    CallRecord mine = saveCall(1L, TODAY, 10);

    assertThat(callRecordRepository.findByIdAndUserId(mine.getId(), 1L)).isPresent();
    assertThat(callRecordRepository.findByIdAndUserId(mine.getId(), 2L)).isEmpty();
  }

  @Test
  @DisplayName("확인하려던 습관과 마무리하며 채운 값이 그대로 저장되고 꺼내진다")
  void savesEveryColumn() {
    Instant startedAt = Instant.parse("2026-10-14T10:00:00Z");
    CallRecord call =
        new CallRecord(1L, TODAY, startedAt, new BiasQuestion(CognitiveBias.PLANNING_FALLACY, 1));
    call.finish(
        startedAt.plusSeconds(180),
        "계획이 많았다고 함. 오늘은 수학 하나만 하기로 함.",
        CognitiveBias.PLANNING_FALLACY,
        "이 정도는 금방 한다고 함",
        "처음에 몇 분 걸릴 거라고 생각했어?",
        MissReason.TOO_MUCH,
        "학원에서 오면",
        "수학 한 단원",
        false);
    Long id = callRecordRepository.saveAndFlush(call).getId();

    CallRecord found = callRecordRepository.findById(id).orElseThrow();

    assertThat(found.getAskedBias()).isEqualTo(CognitiveBias.PLANNING_FALLACY);
    assertThat(found.getAskedQuestionKey()).isEqualTo("PLANNING_FALLACY-1");
    assertThat(found.isFinished()).isTrue();
    assertThat(found.getFoundBias()).isEqualTo(CognitiveBias.PLANNING_FALLACY);
    assertThat(found.getFoundEvidence()).isEqualTo("이 정도는 금방 한다고 함");
    assertThat(found.getMissReason()).isEqualTo(MissReason.TOO_MUCH);
    assertThat(found.getPromiseIf()).isEqualTo("학원에서 오면");
    assertThat(found.getPromiseKept()).isFalse();
    assertThat(found.isCrisisDetected()).isFalse();
  }

  @Test
  @DisplayName("위기 표현이 감지된 통화는 표시만 남고 내용 칸은 비어 있다")
  void crisisCallKeepsNoContent() {
    CallRecord call = saveCall(1L, TODAY, 10);
    call.finishWithCrisis(Instant.parse("2026-10-14T10:03:00Z"));
    callRecordRepository.saveAndFlush(call);

    CallRecord found = callRecordRepository.findById(call.getId()).orElseThrow();

    assertThat(found.isCrisisDetected()).isTrue();
    assertThat(found.isFinished()).isTrue();
    assertThat(found.getSummary()).isNull();
    assertThat(found.getFoundBias()).isNull();
    assertThat(found.getPromiseIf()).isNull();
  }
}
