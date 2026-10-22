package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudyTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// "확인 통화" 의 문 규칙입니다. (2026-10-18)
// 사용자: "공부를 하게 하고, 완료한 조건으로 통화할 수 있게"
// AI 음성 통화는 오늘 공부를 하나 끝내야 열립니다. 통화에서는 방금 공부한 것을 학생이 말로 설명하고,
// 코치가 질문 2~3개로 이해했는지 확인합니다. (CallScript.studiedSection)
// 채팅은 이 문이 없습니다. 힘들 때 말할 곳은 언제나 열려 있어야 하기 때문입니다.
//
// ■ 오늘 끝낸 공부로 치는 것
//   - 공부 기록(STUDY)만. 노는 기록(PLAY)은 빼요.
//   - 타이머(NORMAL) · 오프라인(OFFLINE)만. 끝난 뒤 사후 입력(SELF_REPORT)은 빼요. (앱이 잰 것이 아니라서)
//     옛 기록은 mode 가 비어 있고 타이머로 봅니다. (SessionMode 설명)
//   - 순공시간(StudyTime)이 10분 이상. 1~2분 켰다 끈 것으로 통화를 여는 것을 막으려는 숫자입니다.
//   - "오늘" 은 새벽 4시 기준으로 오늘 시작한 기록입니다. (DayRule, 찾는 것은 CheckCallService)
//
// ■ 예외: 최근 7일 안에 위기 표현이 감지됐거나 보호 조치 중인 학생은 문 없이 열립니다.
//   그 통화는 공부 확인이 아니라 위로하는 통화입니다. (CallService.CRISIS_CARE_DAYS)
//
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
public final class CheckCall {

  // 이만큼(분) 넘게 공부한 기록이 있어야 열립니다.
  public static final int MIN_STUDY_MINUTES = 10;
  // 문이 닫혔을 때 FE 에 보여 줄 문장
  public static final String LOCKED_MESSAGE = "오늘 공부 하나를 끝내면 확인 통화가 열려요";
  // 문이 닫힌 이유 (FE 가 잠금 화면에서 봅니다)
  public static final String NO_STUDY_TODAY = "NO_STUDY_TODAY";

  private static final long MILLIS_PER_MINUTE = 60_000;

  private CheckCall() {}

  // 방금 공부한 것 하나. title 은 카드 제목이고, 카드 없이 켠 옛 기록이면 null 입니다.
  // memo 는 종료 팝업에 학생이 쓴 2~3줄 요약(proofSummary)입니다. 안 썼으면 null 입니다. (통화 수정 2)
  public record Studied(
      String title, List<String> checklist, int minutes, Instant endedAt, String memo) {}

  // 문 열림 여부
  //   open    : 통화를 걸 수 있으면 true
  //   studied : 오늘 가장 최근에 끝낸 공부. 위로 통화로 열렸으면 null 일 수 있습니다.
  //   reason  : 닫혔을 때 이유 (NO_STUDY_TODAY). 열렸으면 null
  public record Gate(boolean open, Studied studied, String reason) {

    // 오늘 공부를 끝내서 열림
    public static Gate open(Studied studied) {
      return new Gate(true, studied, null);
    }

    // 위로하는 통화라서 공부 없이도 열림
    public static Gate care() {
      return new Gate(true, null, null);
    }

    // 닫힘
    public static Gate locked() {
      return new Gate(false, null, NO_STUDY_TODAY);
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
