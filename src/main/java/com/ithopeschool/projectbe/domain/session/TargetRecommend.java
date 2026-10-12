package com.ithopeschool.projectbe.domain.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 타이머를 시작할 때 "알맞은 목표 시간" 을 추천합니다. (이슈 #63 의 2026-10-10 에 더해진 부분)
// 이 앱의 보상은 선물이 아니라 "딱 맞는 난이도" 입니다. 계속 완주하면 조금 길게,
// 자꾸 중간에 멈추면 "더 열심히" 가 아니라 "더 짧게" 를 권합니다.
// 추천은 보여주기만 하고 고르는 것은 사용자입니다. 무시해도 아무 일도 일어나지 않습니다.
// (하루 목표 전체를 올리고 내리는 제안은 domain/care/GoalAdjust 입니다. 여기는 타이머 "한 번" 의 길이입니다)
//
// 규칙
//   1. 그 과목의 공부 기록 중 "목표가 있는 것"(목표 시간이 0 보다 큼)만 봅니다.
//   2. 그중 가장 최근에 시작한 3개만 봅니다. 3개가 안 되면 추천하지 않습니다.
//   3. 완주 = 순공시간이 그 기록의 목표 이상.
//   4. 3개 모두 완주 → (3개의 목표 중 가장 긴 것 + 10분) 을 추천합니다.
//      "지금 고른 목표 + 10분" 이 아니라 "해낸 목표 + 10분" 인 이유: 추천을 받아들인 뒤에도 같은 기록으로
//      60 → 70 → 80분 … 을 계속 권하면 안 되기 때문입니다. 새로 완주해야 다음 단계가 열립니다.
//      지금 고른 목표가 이미 그 이상이면 추천하지 않습니다. 최대(600분)를 넘지 않습니다.
//   5. 2개 이상 중간에 멈춤 → 멈춘 기록들의 순공시간 평균을 5분 단위로 올림한 값을 추천합니다. 최소 15분입니다.
//      (평균 32분 → 35분) 지금 고른 목표가 이미 그 이하이면 추천하지 않습니다.
//   6. 그 밖(3개 중 1개만 멈춤)에는 추천하지 않습니다.
//
// 예시 (입력 → 결과)
//   목표 50분을 3번 모두 완주, 지금 목표 50분
//     →  60분, 길게, "3번 연속 완주! 60분 어때?"
//   목표 50분에 30분·34분에서 멈추고 1번은 완주, 지금 목표 50분
//     →  35분, 짧게, "최근엔 32분쯤에서 멈췄어. 35분부터 해볼까?"   ((30 + 34) ÷ 2 = 32 → 35)
//   3번 중 2번 완주, 1번만 멈춤                       →  null
//   목표가 있는 기록이 2개뿐                           →  null
//   목표 50분을 3번 완주했는데 지금 목표가 60분          →  null  (새로 완주해야 70분을 권합니다)
//   목표 25분에 4분·6분에서 멈춤, 지금 목표 25분
//     →  15분, 짧게, "최근엔 5분쯤에서 멈췄어. 15분부터 해볼까?"   (최소 15분)
//
// FE 의 getTargetRecommendation (src/lib/targetRecommend.ts) 과 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 추천이 어긋나지 않습니다.
//
// minutes: 추천하는 목표 시간(분) / kind: 지금보다 길게(RAISE)인지 짧게(LOWER)인지 / reason: 화면에 보여줄 이유 한 줄
public record TargetRecommend(int minutes, Kind kind, String reason) {

  // 추천의 방향입니다.
  public enum Kind {
    // 지금보다 길게
    RAISE,
    // 지금보다 짧게
    LOWER
  }

  // 과목마다 "목표가 있는 최근 기록" 을 몇 개 볼지
  public static final int RECENT_SESSION_COUNT = 3;

  // 최근 기록을 모두 완주했을 때 올리는 시간(분)
  public static final int RAISE_STEP_MINUTES = 10;

  // 최근 기록 중 이 횟수 이상 중간에 멈췄으면 낮춥니다.
  public static final int STOPPED_COUNT_TO_LOWER = 2;

  // 낮출 때 맞추는 단위(분). 32분 → 35분
  public static final int LOWER_UNIT_MINUTES = 5;

  // 추천하는 가장 짧은 목표(분)
  public static final int MIN_RECOMMEND_MINUTES = 15;

  // 타이머 목표로 정할 수 있는 가장 긴 시간(분). FE 의 MAX_TARGET_MINUTES 와 같은 값입니다.
  public static final int MAX_TARGET_MINUTES = 600;

  private static final long MILLIS_PER_MINUTE = 60_000;

  // 타이머로 잰 공부 기록 하나에서 추천에 쓰는 값만 모은 것입니다.
  // startedAt: 시작한 시각 / targetMinutes: 그때 정한 목표(분). 목표 없이 했으면 0
  // studyMillis: 순공시간(밀리초). StudyTime.studyMillis 의 결과를 넣습니다.
  // (공부 기록 테이블에 목표 시간이 아직 없어서, 이 계산에 필요한 세 값만 받습니다)
  public record TimedSession(Instant startedAt, int targetMinutes, long studyMillis) {}

  // 최근 기록을 보고 목표 시간을 추천합니다. 추천할 것이 없으면 null 입니다.
  //
  // sessions: "한 과목" 의 공부 기록들. 순서는 상관없습니다.
  //   다른 과목의 기록과 노는 기록은 부르는 쪽에서 빼고 넘깁니다. (FE 는 함수 안에서 걸러냅니다)
  // currentTargetMinutes: 지금 타이머에 골라 둔 목표(분)
  public static TargetRecommend of(List<TimedSession> sessions, int currentTargetMinutes) {
    // 규칙 1: 목표가 있는 기록만 남깁니다.
    List<TimedSession> withTarget = new ArrayList<>();
    for (TimedSession session : sessions) {
      if (session.targetMinutes() > 0) {
        withTarget.add(session);
      }
    }

    // 규칙 2: 시작 시각이 늦은 것이 앞으로 오게 줄 세우고, 앞의 3개만 봅니다.
    if (withTarget.size() < RECENT_SESSION_COUNT) {
      return null;
    }
    withTarget.sort(Comparator.comparing(TimedSession::startedAt).reversed());
    List<TimedSession> recentSessions = withTarget.subList(0, RECENT_SESSION_COUNT);

    // 중간에 멈춘 기록들의 순공시간(밀리초) 합계·개수와, 본 기록들의 목표 중 가장 긴 것을 구합니다.
    long stoppedMillisSum = 0;
    int stoppedCount = 0;
    int longestTargetMinutes = 0;
    for (TimedSession session : recentSessions) {
      longestTargetMinutes = Math.max(longestTargetMinutes, session.targetMinutes());

      // 규칙 3: 순공시간이 목표보다 짧으면 중간에 멈춘 기록입니다.
      if (session.studyMillis() < session.targetMinutes() * MILLIS_PER_MINUTE) {
        stoppedMillisSum += session.studyMillis();
        stoppedCount++;
      }
    }

    // 규칙 4: 모두 완주했으면 조금 길게.
    if (stoppedCount == 0) {
      int minutes = Math.min(longestTargetMinutes + RAISE_STEP_MINUTES, MAX_TARGET_MINUTES);
      if (currentTargetMinutes >= minutes) {
        return null;
      }
      return new TargetRecommend(
          minutes, Kind.RAISE, RECENT_SESSION_COUNT + "번 연속 완주! " + minutes + "분 어때?");
    }

    // 규칙 5: 자꾸 멈췄으면 멈춘 지점쯤으로 짧게.
    if (stoppedCount >= STOPPED_COUNT_TO_LOWER) {
      // FE 와 같은 순서(평균을 낸 뒤 분으로 바꾸고 반올림)로 계산합니다.
      int averageMinutes =
          (int) Math.round((double) stoppedMillisSum / stoppedCount / MILLIS_PER_MINUTE);

      // 5분 단위로 올림 (32 → 35, 35 → 35), 그리고 최소 15분.
      int roundedUp = (int) Math.ceil((double) averageMinutes / LOWER_UNIT_MINUTES);
      int minutes = Math.max(roundedUp * LOWER_UNIT_MINUTES, MIN_RECOMMEND_MINUTES);
      if (currentTargetMinutes <= minutes) {
        return null;
      }
      return new TargetRecommend(
          minutes, Kind.LOWER, "최근엔 " + averageMinutes + "분쯤에서 멈췄어. " + minutes + "분부터 해볼까?");
    }

    // 규칙 6
    return null;
  }
}
