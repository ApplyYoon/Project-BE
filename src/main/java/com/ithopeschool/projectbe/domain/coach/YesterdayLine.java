package com.ithopeschool.projectbe.domain.coach;

import com.ithopeschool.projectbe.domain.coach.dto.WeeklyCoachRequest;
import com.ithopeschool.projectbe.domain.summary.DaySummary;
import java.util.List;

// 홈의 "어제 한 줄" 의 기본 문장과 그 근거 숫자를 만듭니다. (이슈 #87, 어제 한 줄은 POST /coach/daily 가 씁니다)
// 숫자는 코드가(DaySummary), 말은 AI 가 맡습니다. AI 가 없거나 AI 의 문장을 버렸을 때는 이 기본 문장을 보여줍니다.
//
// 정해 둔 규칙
//   1. 어제 공부 기록이 있고 계획도 있었으면  →  "어제 순공 3시간 10분, 계획의 79%야."   (숫자 2개)
//   2. 기록은 있는데 계획이 없었으면         →  "어제 순공 1시간, 계획 없이도 해냈네."   (숫자 1개)
//      계획 없이 공부한 날을 "계획의 0%" 라고 하면 못한 것처럼 들려서 다르게 말합니다.
//      계획은 있었지만 목표가 0분이라 달성률을 계산할 수 없는 날도 여기에 들어갑니다.
//   3. 어제 기록이 없으면                    →  "오늘부터 시작해 볼까?"   (숫자 없음. AI 에게 묻지 않습니다)
//      못 한 것을 짚지 않고, 오늘 할 수 있는 것부터 말합니다.
//
// 시간은 "3시간 10분" · "40분" · "2시간" 처럼 적습니다.
//
// FE 의 getYesterdayLine (src/lib/dailyCoach.ts) 과 같은 규칙·같은 문장입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 한 줄이 어긋나지 않습니다.
//
// sentence: 숫자를 끼워 만든 기본 문장
// evidence: AI 에게 건넬 숫자들. 비어 있으면 AI 에게 묻지 않습니다. (말로 풀 숫자가 없기 때문입니다)
//   (모양은 FE 가 보내던 WeeklyCoachRequest.Number 와 같아서 CoachPrompt · CoachParagraphCheck 에 그대로 넘길 수
// 있습니다)
public record YesterdayLine(String sentence, List<WeeklyCoachRequest.Number> evidence) {

  private static final int MINUTES_PER_HOUR = 60;

  // 어제의 요약으로 "어제 한 줄" 을 만듭니다.
  //
  // yesterday: 어제 하루의 지표 (DaySummary.of)
  // hasPlan: 어제 공부 계획이 하나라도 있었는지
  public static YesterdayLine of(DaySummary yesterday, boolean hasPlan) {
    // 규칙 3
    if (yesterday.studyMinutes() <= 0) {
      return new YesterdayLine("오늘부터 시작해 볼까?", List.of());
    }

    String studyText = minutesText(yesterday.studyMinutes());
    WeeklyCoachRequest.Number studyEvidence =
        new WeeklyCoachRequest.Number("어제 순공시간", studyText, "타이머 기록");

    // 규칙 1
    if (hasPlan && yesterday.achievementRate() != null) {
      return new YesterdayLine(
          "어제 순공 " + studyText + ", 계획의 " + yesterday.achievementRate() + "%야.",
          List.of(
              studyEvidence,
              new WeeklyCoachRequest.Number(
                  "어제 계획 대비 실제", yesterday.achievementRate() + "%", "어제 계획")));
    }

    // 규칙 2
    return new YesterdayLine("어제 순공 " + studyText + ", 계획 없이도 해냈네.", List.of(studyEvidence));
  }

  // 분(1 이상)을 "2시간 14분" · "40분" · "2시간" 처럼 바꿉니다.
  private static String minutesText(int totalMinutes) {
    int hours = totalMinutes / MINUTES_PER_HOUR;
    int minutes = totalMinutes % MINUTES_PER_HOUR;
    if (hours == 0) {
      return minutes + "분";
    }
    if (minutes == 0) {
      return hours + "시간";
    }
    return hours + "시간 " + minutes + "분";
  }
}
