package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// "확인 통화" 규칙입니다. (2026-10-18)
// 오늘 끝낸 공부가 있으면 통화에서 방금 공부한 것을 학생이 말로 설명하고,
// 코치가 질문 2~3개로 이해했는지 확인합니다. (CallScript.studiedSection)
//
// ■ 통화는 공부를 안 했어도 걸 수 있습니다. (2026-10-22 사용자 "AI 전화 조건 제거")
//   처음(2026-10-18)에는 "오늘 공부를 하나 끝내야 열림" 조건이 있었는데 뺐습니다.
//   지금은 통화가 늘 열리고, 아래 규칙은 "어떤 공부로 확인 통화를 할지" 고르는 데만 씁니다.
//   끝낸 공부가 없으면 보통 통화([통화 순서])로 시작합니다. 하루 통화 횟수 · 시간 한도는 2026-10-24 에 없앴습니다.
//
// ■ 오늘 끝낸 공부로 치는 것
//   - 공부 기록(STUDY)만. 노는 기록(PLAY)은 빼요.
//   - 타이머(NORMAL) · 오프라인(OFFLINE)만. 끝난 뒤 사후 입력(SELF_REPORT)은 빼요. (앱이 잰 것이 아니라서)
//     옛 기록은 mode 가 비어 있고 타이머로 봅니다. (SessionMode 설명)
//   - 순공시간(StudyTime)이 10분 이상. 1~2분 켰다 끈 것은 확인할 거리가 없어서입니다.
//   - "오늘" 은 새벽 4시 기준으로 오늘 시작한 기록입니다. (DayRule, 찾는 것은 CheckCallService)
//
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
public final class CheckCall {

  // 이만큼(분) 넘게 공부한 기록만 확인 통화의 공부로 칩니다.
  public static final int MIN_STUDY_MINUTES = 10;

  private static final long MILLIS_PER_MINUTE = 60_000;

  private CheckCall() {}

  // 방금 공부한 것 하나. title 은 카드 제목이고, 카드 없이 켠 옛 기록이면 null 입니다.
  // memo 는 종료 팝업에 학생이 쓴 2~3줄 요약(proofSummary)입니다. 안 썼으면 null 입니다. (통화 수정 2)
  public record Studied(
      String title, List<String> checklist, int minutes, Instant endedAt, String memo) {}

  // 통화 문
  //   open    : 통화를 걸 수 있으면 true. 조건을 뺀 뒤(2026-10-22)로는 늘 true 입니다.
  //   studied : 오늘 가장 최근에 끝낸 공부. 없으면 null 이고, 그러면 보통 통화입니다.
  //   reason  : 닫혔을 때 이유. 지금은 닫히지 않아서 늘 null 입니다.
  //   open · reason 은 FE 가 이미 읽는 모양이라 남겨 두었습니다. (GET /realtime/check-call)
  public record Gate(boolean open, Studied studied, String reason) {

    // 열림. studied 는 null 이어도 됩니다.
    public static Gate open(Studied studied) {
      return new Gate(true, studied, null);
    }
  }

  // 오늘 기록 중 "끝낸 공부" 로 치는 것 중에 가장 늦게 끝난 것 하나. 없으면 빈 값입니다.
  //
  //   [19:00~19:40 타이머 40분, 20:00~20:05 타이머 5분]  →  19:00~19:40 (5분은 10분이 안 돼서 빠짐)
  //   [사후 입력 60분]                                   →  없음
  public static Optional<StudySession> latestCounted(List<StudySession> todaySessions) {
    StudySession latest = null;
    for (StudySession session : todaySessions) {
      if (!counts(session)) {
        continue;
      }
      if (latest == null || session.getEndedAt().isAfter(latest.getEndedAt())) {
        latest = session;
      }
    }
    return Optional.ofNullable(latest);
  }

  // 순공시간(분, 내림)
  public static int minutesOf(StudySession session) {
    return (int)
        (StudyTime.studyMillis(session.getStartedAt(), session.getEndedAt(), session.getPausedMs())
            / MILLIS_PER_MINUTE);
  }

  // 끝낸 공부로 치는 기록인지 (위 설명의 세 줄)
  static boolean counts(StudySession session) {
    boolean study = session.getKind() == PlanKind.STUDY;
    boolean measured = session.getMode() != SessionMode.SELF_REPORT;
    return study && measured && minutesOf(session) >= MIN_STUDY_MINUTES;
  }
}
