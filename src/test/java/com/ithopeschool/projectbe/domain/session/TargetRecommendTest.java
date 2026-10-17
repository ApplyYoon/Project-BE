package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.session.TargetRecommend.Kind;
import com.ithopeschool.projectbe.domain.session.TargetRecommend.TimedSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// 타이머 목표 시간 추천 규칙(이슈 #63)을 확인합니다.
// feExample 로 시작하는 7개는 FE 함수(getTargetRecommendation) 주석의 예시를 그대로 옮긴 것입니다.
class TargetRecommendTest {

  // 기록들의 시작 시각을 만들 때 쓰는 기준 시각입니다.
  private static final Instant BASE = Instant.parse("2026-10-01T10:00:00Z");

  // 기록 하나를 만듭니다. daysAfter 가 클수록 최근 기록입니다.
  // 예: session(3, 50, 30) → 기준일 3일 뒤에 목표 50분으로 시작해서 30분 공부한 기록
  private static TimedSession session(int daysAfter, int targetMinutes, int studyMinutes) {
    return new TimedSession(
        BASE.plusSeconds(daysAfter * 86_400L), targetMinutes, studyMinutes * 60_000L);
  }

  // ─── FE 예시 ───

  @Test
  void feExample1RaiseAfterThreeFinishedSessions() {
    // FE 예1) 올리기: 목표 50분을 3번 모두 완주, 지금 목표 50분 → 60분, '3번 연속 완주! 60분 어때?'
    List<TimedSession> sessions =
        List.of(session(1, 50, 50), session(2, 50, 52), session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50))
        .isEqualTo(new TargetRecommend(60, Kind.RAISE, "3번 연속 완주! 60분 어때?"));
  }

  @Test
  void feExample2LowerAfterStoppingTwice() {
    // FE 예2) 낮추기: 목표 50분에 30분·34분에서 멈추고 1번은 완주, 지금 목표 50분
    //         → 35분, '최근엔 32분쯤에서 멈췄어. 35분부터 해볼까?'  ((30 + 34) ÷ 2 = 32 → 35)
    List<TimedSession> sessions =
        List.of(session(1, 50, 30), session(2, 50, 34), session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50))
        .isEqualTo(new TargetRecommend(35, Kind.LOWER, "최근엔 32분쯤에서 멈췄어. 35분부터 해볼까?"));
  }

  @Test
  void feExample3NoRecommendationWhenOnlyOneStopped() {
    // FE 예3) 추천 없음: 3번 중 2번 완주, 1번만 멈춤 → null
    List<TimedSession> sessions =
        List.of(session(1, 50, 50), session(2, 50, 20), session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50)).isNull();
  }

  @Test
  void feExample4NotEnoughSessions() {
    // FE 예4) 기록 부족: 목표가 있는 기록이 2개뿐 → null
    List<TimedSession> sessions = List.of(session(1, 50, 50), session(2, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50)).isNull();
  }

  @Test
  void feExample5AlreadyAppliedRecommendationIsNotRepeated() {
    // FE 예5) 이미 반영함: 목표 50분을 3번 완주했는데 지금 목표가 60분 → null (새로 완주해야 70분을 권합니다)
    List<TimedSession> sessions =
        List.of(session(1, 50, 50), session(2, 50, 50), session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 60)).isNull();
  }

  @Test
  void feExample6NeverRecommendsLessThanFifteenMinutes() {
    // FE 예6) 최소 15분: 목표 25분에 4분·6분에서 멈춤, 지금 목표 25분
    //         → 15분, '최근엔 5분쯤에서 멈췄어. 15분부터 해볼까?'
    List<TimedSession> sessions = List.of(session(1, 25, 4), session(2, 25, 6), session(3, 25, 25));

    assertThat(TargetRecommend.of(sessions, 25))
        .isEqualTo(new TargetRecommend(15, Kind.LOWER, "최근엔 5분쯤에서 멈췄어. 15분부터 해볼까?"));
  }

  @Test
  void feExample7SessionsWithoutTargetAreNotCounted() {
    // FE 예7) 목표 없는 기록은 세지 않습니다. (다른 과목·노는 기록은 서버에서는 부르는 쪽이 미리 뺍니다)
    // 목표 없는 기록(0분)이 가장 최근에 3개 있어도, 그 앞의 "목표가 있는 3개" 를 봅니다.
    List<TimedSession> sessions =
        List.of(
            session(1, 50, 50),
            session(2, 50, 50),
            session(3, 50, 50),
            session(4, 0, 10),
            session(5, 0, 10),
            session(6, 0, 10));

    assertThat(TargetRecommend.of(sessions, 50))
        .isEqualTo(new TargetRecommend(60, Kind.RAISE, "3번 연속 완주! 60분 어때?"));

    // 목표가 있는 기록이 2개뿐이면, 목표 없는 기록이 아무리 많아도 추천하지 않습니다.
    assertThat(TargetRecommend.of(sessions.subList(1, 6), 50)).isNull();
  }

  // ─── 최근 3개만 봅니다 ───

  @Test
  void looksAtTheThreeMostRecentSessionsOnly() {
    // 예전에 자주 멈췄어도, 가장 최근 3개를 모두 완주했으면 올리기를 권합니다.
    List<TimedSession> sessions =
        List.of(
            session(1, 50, 10),
            session(2, 50, 10),
            session(3, 50, 50),
            session(4, 50, 50),
            session(5, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50).kind()).isEqualTo(Kind.RAISE);
  }

  @Test
  void orderOfTheListDoesNotMatter() {
    // 목록의 순서가 뒤섞여 있어도 시작 시각으로 최근 3개를 고릅니다.
    List<TimedSession> shuffled =
        List.of(
            session(5, 50, 50),
            session(1, 50, 10),
            session(4, 50, 50),
            session(2, 50, 10),
            session(3, 50, 50));

    assertThat(TargetRecommend.of(shuffled, 50).kind()).isEqualTo(Kind.RAISE);
  }

  @Test
  void doesNotChangeTheGivenList() {
    // 줄을 세우느라 받은 목록의 순서를 바꾸지 않습니다.
    List<TimedSession> sessions =
        new ArrayList<>(List.of(session(1, 50, 50), session(3, 50, 50), session(2, 50, 50)));
    List<TimedSession> before = List.copyOf(sessions);

    TargetRecommend.of(sessions, 50);

    assertThat(sessions).isEqualTo(before);
  }

  // ─── 올리기 ───

  @Test
  void raiseIsBasedOnTheLongestFinishedTarget() {
    // "지금 고른 목표 + 10분" 이 아니라 "해낸 목표 중 가장 긴 것 + 10분" 입니다.
    // 30분·50분·40분을 모두 완주 → 60분. 지금 목표가 20분이어도 30분이 아니라 60분을 권합니다.
    List<TimedSession> sessions =
        List.of(session(1, 30, 30), session(2, 50, 50), session(3, 40, 45));

    assertThat(TargetRecommend.of(sessions, 20).minutes()).isEqualTo(60);
  }

  @Test
  void finishedMeansStudyTimeReachedTheTarget() {
    // 경계값: 순공시간이 목표와 같으면 완주, 1초라도 모자라면 멈춘 것입니다.
    TimedSession oneSecondShort =
        new TimedSession(BASE.plusSeconds(86_400), 50, 50 * 60_000L - 1_000);
    List<TimedSession> sessions = List.of(oneSecondShort, session(2, 50, 50), session(3, 50, 50));

    // 3개 중 1개가 멈춘 기록이 되어서 올리기를 권하지 않습니다.
    assertThat(TargetRecommend.of(sessions, 50)).isNull();
  }

  @Test
  void raiseNeverGoesOverTheMaximum() {
    // 최대 600분을 넘지 않습니다. 595분을 3번 완주 → 605분이 아니라 600분
    List<TimedSession> sessions =
        List.of(session(1, 595, 595), session(2, 595, 595), session(3, 595, 595));

    assertThat(TargetRecommend.of(sessions, 595).minutes()).isEqualTo(600);
    // 이미 600분을 골랐으면 더 권할 것이 없습니다.
    assertThat(TargetRecommend.of(sessions, 600)).isNull();
  }

  // ─── 낮추기 ───

  @Test
  void lowerRoundsUpToFiveMinutes() {
    // 멈춘 지점의 평균을 5분 단위로 올림합니다. 평균이 딱 5분 단위면 그대로입니다.
    // (30 + 40) ÷ 2 = 35 → 35분
    List<TimedSession> exact = List.of(session(1, 50, 30), session(2, 50, 40), session(3, 50, 50));
    // (30 + 42) ÷ 2 = 36 → 40분
    List<TimedSession> above = List.of(session(1, 50, 30), session(2, 50, 42), session(3, 50, 50));

    assertThat(TargetRecommend.of(exact, 50).minutes()).isEqualTo(35);
    assertThat(TargetRecommend.of(above, 50).minutes()).isEqualTo(40);
  }

  @Test
  void lowerUsesOnlyTheStoppedSessions() {
    // 평균은 멈춘 기록만으로 냅니다. 완주한 기록(50분)은 평균에 넣지 않습니다.
    // 3개 모두 멈춤: (20 + 30 + 40) ÷ 3 = 30 → 30분
    List<TimedSession> allStopped =
        List.of(session(1, 50, 20), session(2, 50, 30), session(3, 50, 40));

    assertThat(TargetRecommend.of(allStopped, 50))
        .isEqualTo(new TargetRecommend(30, Kind.LOWER, "최근엔 30분쯤에서 멈췄어. 30분부터 해볼까?"));
  }

  @Test
  void noLowerRecommendationWhenCurrentTargetIsAlreadyShort() {
    // 지금 고른 목표가 이미 추천값 이하이면 추천하지 않습니다. (35분을 권할 상황)
    List<TimedSession> sessions =
        List.of(session(1, 50, 30), session(2, 50, 34), session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 35)).isNull();
    assertThat(TargetRecommend.of(sessions, 30)).isNull();
    // 36분이면 1분이라도 길어서 권합니다.
    assertThat(TargetRecommend.of(sessions, 36).minutes()).isEqualTo(35);
  }

  @Test
  void averageIsRoundedToTheNearestMinute() {
    // 멈춘 지점의 평균은 분으로 반올림합니다. 31분 30초 → 32분
    TimedSession first = new TimedSession(BASE.plusSeconds(86_400), 50, 31 * 60_000L);
    TimedSession second = new TimedSession(BASE.plusSeconds(2 * 86_400L), 50, 32 * 60_000L);
    List<TimedSession> sessions = List.of(first, second, session(3, 50, 50));

    assertThat(TargetRecommend.of(sessions, 50).reason()).isEqualTo("최근엔 32분쯤에서 멈췄어. 35분부터 해볼까?");
  }

  // ─── 빈 입력 ───

  @Test
  void noSessionsMeansNoRecommendation() {
    // 기록이 하나도 없으면 추천하지 않습니다.
    assertThat(TargetRecommend.of(List.of(), 50)).isNull();
  }
}
