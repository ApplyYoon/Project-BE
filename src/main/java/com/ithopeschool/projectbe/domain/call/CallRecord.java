package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.bias.BiasQuestion;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 통화 기록 테이블. 한 줄 = AI 통화 한 번. (이슈 #28 · #29)
// 상담 채팅(이슈 #238)도 "글로 하는 통화" 라서 같은 테이블에 한 줄씩 남깁니다. 무엇으로 했는지는 channel 로 구분합니다.
//
// 통화를 시작할 때 한 줄을 만들고 "이번에 어떤 생각 습관을 확인하려 했는지" 를 적습니다.
// 통화가 끝나면 같은 줄에 요약·보인 생각 습관·약속을 채웁니다. (finish)
// 다음 통화는 최근 기록을 읽어서 지난 약속을 확인하고, 자주 보인 생각 습관을 다시 확인합니다. (CallService)
//
// ⚠️ 대화 원문(자막)과 녹음은 저장하지 않습니다. 요약과 정해진 분류값만 남깁니다. (CLAUDE.md 4장)
// ⚠️ 요약·근거·약속은 학생의 마음 기록이라 로그에 찍지 않고, 본인에게만 돌려줍니다.
// "내 최근 통화를 찾는" 일이 가장 많아서 (user_id, call_date) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
@Entity
@Table(
    name = "call_records",
    indexes =
        @Index(name = "idx_call_records_user_id_call_date", columnList = "user_id, call_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallRecord {

  // 통화를 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 통화를 한 사람(users.id). 다른 사람의 기록이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 통화한 날. 새벽 4시에 날짜가 바뀝니다. (DayRule)
  @Column(nullable = false)
  private LocalDate callDate;

  // 통화를 시작한 시각 (임시 키를 받은 시각, 채팅은 채팅을 연 시각)
  @Column(nullable = false, updatable = false)
  private Instant startedAt;

  // 음성 통화(VOICE)인지 상담 채팅(CHAT)인지. 이 칸이 생기기 전의 기록은 모두 음성 통화입니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CallChannel channel = CallChannel.VOICE;

  // 이번 통화에서 확인하려던 생각 습관. 위로하는 통화이거나 확인할 것이 없는 날은 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private CognitiveBias askedBias;

  // 그 습관의 기본 질문 중 어느 것을 골랐는지. 예: "PLANNING_FALLACY-1" (BiasQuestion.key)
  @Column(length = 40)
  private String askedQuestionKey;

  // ---- 아래는 통화를 마무리할 때 채웁니다 (마무리 전이면 모두 비어 있음) ----

  // 통화를 마무리한 시각
  private Instant finishedAt;

  // 통화 요약 2~3문장. 대화 원문은 저장하지 않습니다.
  @Column(length = 300)
  private String summary;

  // 대답에서 보인 생각 습관. 뚜렷하게 보이지 않았으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private CognitiveBias foundBias;

  // 그 습관이 보인 근거 한 줄 (학생이 한 말의 요지). 예: "이 정도는 금방 한다고 함"
  @Column(length = 100)
  private String foundEvidence;

  // AI 가 실제로 한 생각 습관 질문. 어떤 질문이 도움이 됐는지 나중에 비교하려고 남깁니다.
  @Column(length = 200)
  private String actualQuestion;

  // 어제 계획을 못 채운 이유. 대답에 없었으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason missReason;

  // 오늘의 약속 "만약 ~하면" 과 "~한다". 정하지 않았으면 비어 있습니다.
  @Column(length = 50)
  private String promiseIf;

  @Column(length = 50)
  private String promiseThen;

  // 지난 약속을 지켰다고 했는지. 이야기하지 않았으면 비어 있습니다.
  private Boolean promiseKept;

  // 위기 표현이 감지됐는지. 감지되면 내용(요약·습관·약속)은 남기지 않고 이 표시만 남깁니다.
  @Column(nullable = false)
  private boolean crisisDetected;

  // 음성 통화를 시작할 때 만듭니다. askedQuestion 은 오늘 확인할 질문이고, 묻지 않는 날이면 null 입니다.
  public CallRecord(
      Long userId, LocalDate callDate, Instant startedAt, BiasQuestion askedQuestion) {
    this(userId, callDate, startedAt, askedQuestion, CallChannel.VOICE);
  }

  // 음성 통화 또는 상담 채팅을 시작할 때 만듭니다.
  public CallRecord(
      Long userId,
      LocalDate callDate,
      Instant startedAt,
      BiasQuestion askedQuestion,
      CallChannel channel) {
    this.channel = channel;
    this.userId = userId;
    this.callDate = callDate;
    this.startedAt = startedAt;
    this.askedBias = askedQuestion == null ? null : askedQuestion.bias();
    this.askedQuestionKey = askedQuestion == null ? null : askedQuestion.key();
  }

  // 통화를 마무리하며 정리한 값을 채웁니다. 모르는 값은 null 로 넘깁니다.
  public void finish(
      Instant finishedAt,
      String summary,
      CognitiveBias foundBias,
      String foundEvidence,
      String actualQuestion,
      MissReason missReason,
      String promiseIf,
      String promiseThen,
      Boolean promiseKept) {
    this.finishedAt = finishedAt;
    this.summary = summary;
    this.foundBias = foundBias;
    this.foundEvidence = foundEvidence;
    this.actualQuestion = actualQuestion;
    this.missReason = missReason;
    this.promiseIf = promiseIf;
    this.promiseThen = promiseThen;
    this.promiseKept = promiseKept;
  }

  // 위기 표현이 감지된 통화를 마무리합니다. 내용은 남기지 않고 "감지됐다" 는 표시와 시각만 남깁니다.
  public void finishWithCrisis(Instant finishedAt) {
    this.finishedAt = finishedAt;
    this.crisisDetected = true;
  }

  // 상담 채팅에서 위기 표현이 감지됐을 때, 채팅은 끝내지 않고 "감지됐다" 는 표시만 남깁니다. (2026-10-18)
  // 이 표시가 있으면 마무리할 때 보호 조치를 다시 하지 않습니다. (CallRecordService.finishWithCrisis)
  public void markCrisis() {
    this.crisisDetected = true;
  }

  // 마무리했으면 true
  public boolean isFinished() {
    return finishedAt != null;
  }
}
