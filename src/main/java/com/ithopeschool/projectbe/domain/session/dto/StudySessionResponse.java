package com.ithopeschool.projectbe.domain.session.dto;

import com.ithopeschool.projectbe.domain.session.SessionMemo;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.SessionPause;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.StudySession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 공부 기록 하나를 FE 에 알려 주는 모양입니다. FE 의 StudySession 과 같은 이름을 씁니다.
//   { "id": "12", "subjectId": "3", "startedAt": 1790000000000, "endedAt": 1790003000000,
//     "pauseCount": 2, "pausedMs": 300000, "planId": "7", "targetMinutes": 50,
//     "stopReason": "phone", "kind": "study",
//     "cardId": "7", "mode": "normal", "pauses": [ … ], "endedEarly": false,
//     "endReport": { "checked": { "i1": … }, "shortfallReason": null, "withWhat": "music",
//                    "proof": null, "goodReasons": null },
//     "memos": null, "playTag": null, "overRestMs": null }
// - id, subjectId, planId, cardId 는 FE 타입이 글자(string)라서 숫자를 글자로 바꿔 보냅니다.
// - 노는 기록이나 과목 없이 카드로 켠 기록은 subjectId 가 빈 글자("")입니다. (계획과 같은 약속)
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - 값이 없으면 null 입니다. 옛 기록(#224 전)은 카드 칸이 비어 있습니다.
//   mode 는 비어 있어도 "normal", endedEarly 는 비어 있어도 false 로 보냅니다. (FE 가 "없으면" 으로 보는 값)
//   pauses 는 일시정지 줄이 빠짐없이 있을 때만 배열, 옛 기록처럼 횟수만 있으면 null(모름) 입니다.
// - 누구의 기록인지(userId)는 담지 않습니다.
public record StudySessionResponse(
    String id,
    String subjectId,
    long startedAt,
    long endedAt,
    int pauseCount,
    long pausedMs,
    String planId,
    Integer targetMinutes,
    String stopReason,
    String kind,
    String cardId,
    String mode,
    List<PauseResponse> pauses,
    boolean endedEarly,
    EndReportResponse endReport,
    List<MemoResponse> memos,
    String playTag,
    Long overRestMs) {

  // 일시정지 한 번 (FE 의 SessionPause). note 는 없으면 null
  public record PauseResponse(long startedAt, long endedAt, String tag, String note) {}

  // 종료 팝업에서 받은 것 (FE 의 SessionEndReport). proof 는 링크·요약이 둘 다 없으면 null
  public record EndReportResponse(
      Map<String, Long> checked,
      String shortfallReason,
      String withWhat,
      ProofResponse proof,
      List<String> goodReasons) {}

  // 산출물 증빙
  public record ProofResponse(String link, String summary) {}

  // 딴생각 메모 한 줄 (FE 의 SessionMemo)
  public record MemoResponse(long at, String text) {}

  // 카드 칸이 없는 옛 모양(10칸)으로 만듭니다. 카드 칸은 비어 있는 값(mode normal · 중도 종료 아님)입니다.
  // (테스트와 예전 코드가 쓰는 짧은 모양. 일시정지 줄은 횟수가 0 일 때만 빈 배열 — from 과 같은 규칙)
  public StudySessionResponse(
      String id,
      String subjectId,
      long startedAt,
      long endedAt,
      int pauseCount,
      long pausedMs,
      String planId,
      Integer targetMinutes,
      String stopReason,
      String kind) {
    this(
        id,
        subjectId,
        startedAt,
        endedAt,
        pauseCount,
        pausedMs,
        planId,
        targetMinutes,
        stopReason,
        kind,
        null,
        SessionMode.NORMAL.toValue(),
        pauseCount == 0 ? List.of() : null,
        false,
        null,
        null,
        null,
        null);
  }

  public static StudySessionResponse from(StudySession session) {
    return new StudySessionResponse(
        String.valueOf(session.getId()),
        session.getSubjectId() == null ? "" : String.valueOf(session.getSubjectId()),
        session.getStartedAt().toEpochMilli(),
        session.getEndedAt().toEpochMilli(),
        session.getPauseCount(),
        session.getPausedMs(),
        session.getPlanId() == null ? null : String.valueOf(session.getPlanId()),
        session.getTargetMinutes(),
        session.getStopReason() == null ? null : session.getStopReason().toValue(),
        session.getKind().toValue(),
        session.getCardId() == null ? null : String.valueOf(session.getCardId()),
        session.modeOrNormal().toValue(),
        pausesOf(session),
        Boolean.TRUE.equals(session.getEndedEarly()),
        endReportOf(session),
        memosOf(session),
        session.getPlayTag() == null ? null : session.getPlayTag().toValue(),
        session.getOverRestMs());
  }

  // 일시정지 줄이 빠짐없이 있으면 배열, 아니면(옛 기록) null 입니다.
  private static List<PauseResponse> pausesOf(StudySession session) {
    if (!session.hasPauseDetails()) {
      return null;
    }
    List<PauseResponse> pauses = new ArrayList<>();
    for (SessionPause pause : session.getPauses()) {
      pauses.add(
          new PauseResponse(
              pause.getStartedAt().toEpochMilli(),
              pause.getEndedAt().toEpochMilli(),
              pause.getTag().toValue(),
              pause.getNote()));
    }
    return pauses;
  }

  // 종료 팝업에 답하지 않았으면 null 입니다.
  private static EndReportResponse endReportOf(StudySession session) {
    if (!Boolean.TRUE.equals(session.getEndReported())) {
      return null;
    }
    Map<String, Long> checked = new LinkedHashMap<>();
    for (Map.Entry<String, Instant> entry : session.getCheckedItems().entrySet()) {
      checked.put(entry.getKey(), entry.getValue().toEpochMilli());
    }
    ProofResponse proof =
        session.getProofLink() == null && session.getProofSummary() == null
            ? null
            : new ProofResponse(session.getProofLink(), session.getProofSummary());
    // DB 에서 꺼낸 묶음(Set)은 순서가 정해져 있지 않아서, FE 버튼 순서(GoodReason 에 적은 순서)대로 담습니다.
    List<String> goodReasons = new ArrayList<>();
    for (GoodReason reason : GoodReason.values()) {
      if (session.getGoodReasons().contains(reason)) {
        goodReasons.add(reason.toValue());
      }
    }
    return new EndReportResponse(
        checked,
        session.getShortfallReason() == null ? null : session.getShortfallReason().toValue(),
        session.getWithWhat() == null ? null : session.getWithWhat().toValue(),
        proof,
        goodReasons.isEmpty() ? null : goodReasons);
  }

  // 메모가 없으면 null 입니다. (FE 도 메모가 있을 때만 칸을 넣습니다)
  private static List<MemoResponse> memosOf(StudySession session) {
    if (session.getMemos().isEmpty()) {
      return null;
    }
    List<MemoResponse> memos = new ArrayList<>();
    for (SessionMemo memo : session.getMemos()) {
      memos.add(new MemoResponse(memo.getWrittenAt().toEpochMilli(), memo.getText()));
    }
    return memos;
  }
}
