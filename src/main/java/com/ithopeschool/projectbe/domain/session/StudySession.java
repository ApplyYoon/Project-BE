package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.PlayTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.ShortfallReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.WithWhat;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

// 공부 기록 테이블. 한 줄 = 타이머로 한 번 잰 기록 하나(시작 ~ 정지). (FE 의 StudySession)
// "내 기록을 기간으로 찾는" 일이 많아서 (user_id, started_at) 에 색인(찾기 쉽게 만든 목차)을 만들었습니다.
//
// 공부 기록(STUDY)은 과목이나 카드 중 하나가 꼭 있어야 하고, 노는 기록(PLAY)만 둘 다 비어도 됩니다. (이슈 #13, #224)
// 카드로 켠 세션은 과목이 없을 수 있습니다. 카드는 과목 대신 목표로 이어지기 때문입니다. (#222)
// 이 규칙은 Service 에서 먼저 검사하고, 혹시 빠뜨려도 DB 가 한 번 더 막도록 검사 규칙(check)으로도 걸어 두었습니다.
//
// 카드 기준으로 넓힌 칸(#224, 최종 기획서 「4.4 타이머」)은 옛 기록에는 비어 있습니다. 여러 개인 값은 딸린 테이블에 담습니다.
//   session_pauses        : 일시정지마다 한 줄 (started_at, ended_at, tag, note)
//   session_checked_items : 체크한 항목마다 한 줄 (item_key, checked_at)
//   session_good_reasons  : 고른 이유마다 한 줄 (good_reason)
//   session_memos         : 메모마다 한 줄 (written_at, text)
// 네 테이블 모두 study_session_id 로 어느 기록의 것인지 가리킵니다.
@Entity
@Table(
    name = "study_sessions",
    indexes =
        @Index(name = "idx_study_sessions_user_id_started_at", columnList = "user_id, started_at"),
    check =
        @CheckConstraint(
            name = "ck_study_sessions_study_has_subject_or_card",
            constraint = "kind = 'PLAY' or subject_id is not null or card_id is not null"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {

  // 기록을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 기록의 주인(users.id). 다른 사람의 기록이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어떤 과목을 공부했는지(subjects.id). 이 과목도 같은 사용자의 것이어야 합니다.
  // 노는 기록이면 비어 있고, 카드로 켠 공부 기록도 비어 있을 수 있습니다.
  private Long subjectId;

  // 공부를 시작한 시각 (FE 는 Date.now() 숫자로 보내고, 서버가 Instant 로 바꿉니다)
  @Column(nullable = false)
  private Instant startedAt;

  // 공부를 끝낸 시각. 시작보다 뒤여야 합니다.
  @Column(nullable = false)
  private Instant endedAt;

  // 이 기록 동안 일시정지를 누른 횟수. 안 눌렀으면 0 입니다. (기획서 v2 의 집중 지속 분석에 씁니다)
  @Column(nullable = false)
  private int pauseCount;

  // 시작 ~ 끝 사이에 일시정지해 있던 시간의 합(밀리초). 없으면 0 입니다. 순공시간에서 이만큼 뺍니다. (StudyTime)
  @Column(nullable = false)
  private long pausedMs;

  // 어느 계획을 하려고 켠 타이머인지(study_plans.id). 계획 없이 켰으면 비어 있습니다.
  private Long planId;

  // 시작할 때 약속한 목표 시간(분). 목표 없이 켰으면 비어 있습니다. 나중에 계획을 고쳐도 이 값은 그대로입니다.
  private Integer targetMinutes;

  // 목표 전에 그만뒀을 때 고른 이유. 체크인의 "못 채운 이유" 7가지와 같습니다. 안 골랐으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason stopReason;

  // 공부 기록(STUDY)인지 노는 기록(PLAY)인지. 정해진 두 값만 들어갑니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PlanKind kind;

  // ---- 카드 기준으로 넓힌 칸 (이슈 #224). 옛 기록에는 모두 비어 있습니다 ----

  // 어느 카드의 세션인지(cards.id). 이 카드도 같은 사용자의 것이어야 합니다. 옛 기록은 planId 로 찾습니다.
  private Long cardId;

  // 세션을 잰 방법. 타이머(NORMAL) · 오프라인(OFFLINE) · 사후 입력(SELF_REPORT). 비어 있으면 타이머로 봅니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 15)
  private SessionMode mode;

  // 예정 종료 전에 [끝내기] 를 눌렀으면 true (중도 종료). 옛 기록은 비어 있습니다.
  private Boolean endedEarly;

  // 일시정지 한 번 한 번 (이유 태그 포함). 있으면 pauseCount · pausedMs 는 이 목록에서 계산한 값입니다.
  // @BatchSize: 기록 여러 개를 한 번에 꺼낼 때, 딸린 줄을 기록마다 따로 묻지 않고 50개 기록씩 묶어서 읽습니다.
  @ElementCollection
  @CollectionTable(name = "session_pauses", joinColumns = @JoinColumn(name = "study_session_id"))
  @OrderColumn(name = "position")
  @BatchSize(size = 50)
  private List<SessionPause> pauses = new ArrayList<>();

  // [종료 팝업] 종료 팝업에 답했는지. 옛 기록과 팝업을 닫아 버린 기록은 비어 있거나 false 입니다.
  private Boolean endReported;

  // [종료 팝업] 체크한 체크리스트 항목 (카드 체크리스트 항목의 id → 체크한 시각)
  @ElementCollection
  @CollectionTable(
      name = "session_checked_items",
      joinColumns = @JoinColumn(name = "study_session_id"))
  @MapKeyColumn(name = "item_key", length = 40)
  @Column(name = "checked_at", nullable = false)
  @BatchSize(size = 50)
  private Map<String, Instant> checkedItems = new LinkedHashMap<>();

  // [종료 팝업] 체크리스트를 다 못 했을 때 고른 이유
  @Enumerated(EnumType.STRING)
  @Column(length = 15)
  private ShortfallReason shortfallReason;

  // [종료 팝업] 같이 한 것 (음악 · 영상 틀어둠 · 없음)
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private WithWhat withWhat;

  // [종료 팝업] 산출물 증빙 링크 (300자까지)
  @Column(length = 300)
  private String proofLink;

  // [종료 팝업] 산출물 증빙 2~3줄 요약 (300자까지)
  @Column(length = 300)
  private String proofSummary;

  // 관리 4단계라서 증빙이 꼭 필요했던 세션인지 (이슈 #234). 증빙 없이 끝내면 실행으로 인정하지 않습니다. (CardResultJudge)
  // 저장하는 순간의 단계로 정하고 나중에 바꾸지 않습니다. 비어 있는 옛 기록은 "필요 없었음" 으로 봅니다.
  private Boolean proofRequired;

  // [종료 팝업] 잘 된 이유 (물었을 때만)
  @ElementCollection
  @CollectionTable(
      name = "session_good_reasons",
      joinColumns = @JoinColumn(name = "study_session_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "good_reason", nullable = false, length = 15)
  @BatchSize(size = 50)
  private Set<GoodReason> goodReasons = EnumSet.noneOf(GoodReason.class);

  // 딴생각 주차장에 쓴 한 줄 메모
  @ElementCollection
  @CollectionTable(name = "session_memos", joinColumns = @JoinColumn(name = "study_session_id"))
  @OrderColumn(name = "position")
  @BatchSize(size = 50)
  private List<SessionMemo> memos = new ArrayList<>();

  // 노는 기록일 때 무엇을 했는지 (유튜브 · 게임 · SNS · 웹툰 · 쉬기 · 기타). 공부 기록이면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private PlayTag playTag;

  // 노는 기록일 때, 놀기가 끝난 뒤 [카드 시작] · [놀기 끝] 을 누를 때까지 더 쉰 시간(밀리초). 공부 기록이면 비어 있습니다.
  private Long overRestMs;

  // 일시정지 시간·계획·목표·이유가 없는 공부 기록을 만듭니다. (테스트와 예전 코드가 쓰는 짧은 모양)
  public StudySession(
      Long userId, Long subjectId, Instant startedAt, Instant endedAt, int pauseCount) {
    this(userId, subjectId, startedAt, endedAt, pauseCount, 0, null, null, null, PlanKind.STUDY);
  }

  public StudySession(
      Long userId,
      Long subjectId,
      Instant startedAt,
      Instant endedAt,
      int pauseCount,
      long pausedMs,
      Long planId,
      Integer targetMinutes,
      MissReason stopReason,
      PlanKind kind) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.pauseCount = pauseCount;
    this.pausedMs = pausedMs;
    this.planId = planId;
    this.targetMinutes = targetMinutes;
    this.stopReason = stopReason;
    this.kind = kind;
  }

  // ---- 카드 기준으로 넓힌 칸 채우기 (Service 가 저장하기 전에 검사를 마친 값만 넘깁니다) ----

  // 어느 카드의 세션인지, 어떻게 쟀는지, 중도 종료했는지를 적습니다.
  public void recordCard(Long cardId, SessionMode mode, boolean endedEarly) {
    this.cardId = cardId;
    this.mode = mode;
    this.endedEarly = endedEarly;
  }

  // 일시정지 목록을 적습니다. (pauseCount · pausedMs 는 Service 가 이 목록으로 계산해서 만들 때 넣습니다)
  public void recordPauses(List<SessionPause> pauses) {
    this.pauses.clear();
    this.pauses.addAll(pauses);
  }

  // 종료 팝업에서 받은 것을 적습니다.
  public void recordEndReport(
      Map<String, Instant> checkedItems,
      ShortfallReason shortfallReason,
      WithWhat withWhat,
      String proofLink,
      String proofSummary,
      Set<GoodReason> goodReasons) {
    this.endReported = true;
    this.checkedItems.clear();
    this.checkedItems.putAll(checkedItems);
    this.shortfallReason = shortfallReason;
    this.withWhat = withWhat;
    this.proofLink = proofLink;
    this.proofSummary = proofSummary;
    this.goodReasons.clear();
    this.goodReasons.addAll(goodReasons);
  }

  // 딴생각 메모를 적습니다.
  public void recordMemos(List<SessionMemo> memos) {
    this.memos.clear();
    this.memos.addAll(memos);
  }

  // 노는 기록의 종류와 초과 휴식 시간을 적습니다.
  public void recordPlay(PlayTag playTag, Long overRestMs) {
    this.playTag = playTag;
    this.overRestMs = overRestMs;
  }

  // 관리 4단계라서 증빙이 꼭 필요한 세션으로 표시합니다. (Service 가 저장하기 전에 부릅니다)
  public void requireProof() {
    this.proofRequired = true;
  }

  // 증빙이 꼭 필요했던 세션인지. 비어 있는 옛 기록은 false 입니다.
  public boolean isProofRequired() {
    return Boolean.TRUE.equals(proofRequired);
  }

  // 증빙이 있는지. 링크나 요약 중 하나라도 글자가 있으면 있는 것입니다. (FE 의 hasProof 와 같음)
  public boolean hasProof() {
    return hasText(proofLink) || hasText(proofSummary);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  // 세션을 잰 방법. 비어 있는 옛 기록은 타이머(NORMAL)로 봅니다.
  public SessionMode modeOrNormal() {
    return mode == null ? SessionMode.NORMAL : mode;
  }

  // 일시정지 목록이 빠짐없이 적혀 있는지. 줄 수가 일시정지 횟수와 같으면 다 적힌 것입니다.
  // 옛 기록(횟수만 있고 줄이 없음)은 false 라서, 응답에 "모름"(null) 으로 보냅니다.
  public boolean hasPauseDetails() {
    return pauses.size() == pauseCount;
  }
}
