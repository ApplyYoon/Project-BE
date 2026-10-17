package com.ithopeschool.projectbe.domain.message;

import com.ithopeschool.projectbe.domain.safety.QuietHours;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

// "지금 보낼 이벤트 메시지가 있는지" 를 정합니다. (이슈 #31, 기획서 v2 2장·4장)
// 이 함수는 "무엇을 보낼지" 만 정합니다. 실제로 메시지를 만들어 저장하고 보내는 일은 하지 않습니다.
//
// 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//
//   0) 먼저 걸러 내는 조건 (하나라도 맞으면 항상 null)
//        - 연락 금지 시간 안이다
//        - 하루 메시지 수가 0 이다 (이벤트 메시지까지 모두 꺼진 상태)
//   1) 오늘 목표를 달성했다              →  GOAL_DONE (칭찬)
//        목표 시간이 0 보다 크고, 순공시간이 목표 이상일 때입니다.
//   2) 3일 이상 연속 미달이다            →  MISSED_3_DAYS (회복)
//        회복 모드가 우선이라서, 이때는 아래의 PLAN_LATE 를 아예 보내지 않습니다.
//        (이미 보냈으면 다른 메시지로 넘어가지 않고 null 입니다)
//   3) 계획 시간이 지났는데 오늘 공부를 한 번도 안 했고 타이머도 꺼져 있다  →  PLAN_LATE (담백한 알림)
//   4) 어디에도 안 맞는다                →  null
//
// 각 메시지는 오늘 이미 보냈으면 건너뜁니다. (하루에 한 번만)
//
// 예시 (모두 지금 오후 8시, 연락 금지 23시~7시, 하루 3개, 오늘 아직 아무것도 안 보냈을 때)
//   목표 120분, 순공 130분                                  →  GOAL_DONE
//   목표 120분, 순공 130분, 이미 GOAL_DONE 보냄              →  null  (같은 메시지는 두 번 안 보냄)
//   목표 120분, 순공 0분, 3일 연속 미달                      →  MISSED_3_DAYS
//   위와 같은데 이미 MISSED_3_DAYS 보냄                      →  null  (회복 중에는 독촉하지 않음)
//   계획 시각 19:00, 공부 안 함, 타이머 꺼짐, 2일 연속 미달   →  PLAN_LATE
//   위와 같은데 타이머가 켜져 있음                           →  null
//   위와 같은데 오늘 이미 공부를 했음                        →  null
//   지금 밤 11시 30분 (연락 금지)                            →  null  (위의 어느 것이든)
//   하루 메시지 수 0                                        →  null
//   계획 시각을 정하지 않았음(null), 공부 안 함               →  null  (3일 연속 미달이 아니면)
//
// FE 의 getEventMessage (src/lib/eventMessage.ts) 와 같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버가 같은 메시지를 고릅니다.
public final class EventMessage {

  // 이 일수 이상 연속으로 목표를 못 채우면 회복 메시지를 보냅니다. (기획서 4장: 3일 연속 미달)
  public static final int MISSED_DAYS_FOR_RECOVERY = 3;

  // 배포 서버는 보통 UTC 로 돌아가서, 서버 컴퓨터의 시간대에 기대지 않고 한국 시간을 직접 지정합니다.
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  // 판단에 필요한 재료 묶음입니다. (FE 의 EventMessageInput 과 같은 모양)
  //
  // now: 지금 시각
  // quietStart, quietEnd: 연락 금지 시간. 이 안이면 무엇이든 보내지 않습니다. (QuietHours)
  // dailyCount: 하루에 받을 AI 메시지 수. 0 이면 이벤트 메시지도 모두 꺼집니다.
  // alreadySentToday: 오늘 이미 보낸 이벤트 메시지 종류들. 같은 메시지를 하루에 두 번 보내지 않으려고 받습니다.
  // plannedStartTime: 오늘 공부를 시작하기로 한 시각. 정하지 않았으면 null
  // isTimerRunning: 타이머가 지금 돌아가고 있는지
  // hasStudiedToday: 오늘 공부를 한 번이라도 시작했는지 (공부 기록이 하나라도 있으면 true)
  // todayStudyMinutes: 오늘 지금까지 순공시간(분)
  // todayTargetMinutes: 오늘 계획들의 목표 시간 합계(분). 계획이 없으면 0
  // consecutiveMissDays: 어제부터 거슬러 올라가며 연속으로 목표를 못 채운 일수 (MissStreak)
  public record Input(
      Instant now,
      LocalTime quietStart,
      LocalTime quietEnd,
      int dailyCount,
      List<EventMessageType> alreadySentToday,
      LocalTime plannedStartTime,
      boolean isTimerRunning,
      boolean hasStudiedToday,
      int todayStudyMinutes,
      int todayTargetMinutes,
      int consecutiveMissDays) {}

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private EventMessage() {}

  // 지금 보낼 이벤트 메시지를 정합니다. 보낼 것이 없으면 null 을 돌려줍니다.
  public static EventMessageType pick(Input input) {
    // 0) 연락 금지 시간이거나 메시지를 모두 끈 상태
    if (QuietHours.isQuiet(input.now(), input.quietStart(), input.quietEnd())) {
      return null;
    }
    if (input.dailyCount() <= 0) {
      return null;
    }

    List<EventMessageType> alreadySent = input.alreadySentToday();

    // 1) 오늘 목표 달성 → 칭찬
    boolean isGoalDone =
        input.todayTargetMinutes() > 0 && input.todayStudyMinutes() >= input.todayTargetMinutes();
    if (isGoalDone) {
      return alreadySent.contains(EventMessageType.GOAL_DONE) ? null : EventMessageType.GOAL_DONE;
    }

    // 2) 3일 연속 미달 → 회복. 회복 모드가 우선이라 독촉(PLAN_LATE)으로 넘어가지 않습니다.
    if (input.consecutiveMissDays() >= MISSED_DAYS_FOR_RECOVERY) {
      return alreadySent.contains(EventMessageType.MISSED_3_DAYS)
          ? null
          : EventMessageType.MISSED_3_DAYS;
    }

    // 3) 계획 시간이 지났는데 공부를 안 시작했다 → 담백한 알림
    if (input.plannedStartTime() != null) {
      // 시와 분을 "자정부터 몇 분째인지" 하나의 숫자로 바꿔서 비교합니다. (19:30 → 1170) 초는 보지 않습니다.
      int nowMinutes = minutesOfDay(input.now().atZone(SEOUL).toLocalTime());
      boolean isPlanTimePassed = nowMinutes >= minutesOfDay(input.plannedStartTime());

      if (isPlanTimePassed
          && !input.isTimerRunning()
          && !input.hasStudiedToday()
          && !alreadySent.contains(EventMessageType.PLAN_LATE)) {
        return EventMessageType.PLAN_LATE;
      }
    }

    return null;
  }

  // 시각을 "자정부터 몇 분째인지" 로 바꿉니다. (07:30 → 450)
  private static int minutesOfDay(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }
}
