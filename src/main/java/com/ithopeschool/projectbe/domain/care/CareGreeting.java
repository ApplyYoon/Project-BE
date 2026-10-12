package com.ithopeschool.projectbe.domain.care;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 관리 강도 단계별 첫 인사말입니다. (이슈 #65 「AI 말투 지침」의 단계·수위별 문장)
// 통화를 시작할 때 AI 가 건네는 첫 마디로 쓰고, 단계마다 말투가 어떻게 달라지는지 보여 주는 기준 문장이기도 합니다.
//
//   0 회복        위로. "오늘은 10분만 해볼까?"
//   1 당근        구체적 칭찬. "어제보다 첫 시작이 40분 빨랐어!"
//   2 넛지        담백한 알림. "계획 시간 10분 지났어"
//   3 심리적 채찍  걱정 → 담백 → 팩폭 → '물러나는 척'. "요즘 계속 미뤄지는 것 같아서 걱정돼."
//
// 문구 규칙
//   - 외모·성적·능력을 깎아내리는 말, 다른 사람과 비교하는 말은 쓰지 않습니다. 지적은 행동에만 합니다.
//   - 새 문구를 추가할 때는 수위(harshness)를 꼭 정직하게 답니다. 허용 수위보다 센 문구는 나오지 않습니다.
//   - 아직 수위 4~5 문구는 없습니다. 허용 수위를 4~5 로 올려도 3 이하 문구만 나옵니다.
//   - 숫자(40분, 10분 등)는 예시입니다. 실제 기록을 연결하면 그 값으로 바꿉니다.
//
// FE 의 careGreetings · pickGreeting (src/lib/careTheme.ts) 과 같은 문장·같은 규칙입니다.
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 첫 마디가 어긋나지 않습니다.
public final class CareGreeting {

  // 허용 수위를 모를 때 쓰는 값입니다. 설정 화면의 처음 값과 같습니다.
  public static final int DEFAULT_MAX_HARSHNESS = 3;

  // 인사말이 따로 있는 가장 높은 단계입니다. 4(강제적 채찍)는 말투가 3 과 같아서 3 의 인사말을 씁니다.
  private static final int HIGHEST_GREETING_LEVEL = 3;

  // 인사말 한 개입니다.
  // text: 인사말 / harshness: 이 말이 얼마나 센지 (1 부드럽게 / 2 담백하게 / 3 따끔하게 / 4 세게 / 5 제일 세게)
  // 설정의 "독설 허용 수위" 와 같은 기준입니다.
  public record Greeting(String text, int harshness) {}

  private static final Map<Integer, List<Greeting>> GREETINGS =
      Map.of(
          0,
          List.of(
              new Greeting("오늘은 10분만 해볼까?", 1),
              new Greeting("괜찮아, 천천히 시작하자.", 1),
              new Greeting("오늘은 가볍게 한 가지만 해보자.", 1)),
          1,
          List.of(
              new Greeting("어제보다 첫 시작이 40분 빨랐어!", 1),
              new Greeting("어제 계획을 거의 다 해냈네, 잘했어!", 1),
              new Greeting("꾸준히 하는 모습이 보기 좋아.", 1)),
          2,
          List.of(
              new Greeting("계획 시간 10분 지났어", 1),
              new Greeting("오늘 첫 계획 시작할 시간이야.", 1),
              new Greeting("타이머 켤 시간이야.", 1)),
          3,
          List.of(
              // 걱정
              new Greeting("요즘 계속 미뤄지는 것 같아서 걱정돼.", 1),
              // 담백
              new Greeting("오늘 계획, 아직 시작 전이네.", 2),
              // 마무리 ('물러나는 척')
              new Greeting("독촉이 안 통하는 것 같으니 당분간 연락 안 할게.", 2),
              // 팩폭
              new Greeting("계획이 3일째 그대로야. 오늘도 미룰 거야?", 3),
              // 팩폭
              new Greeting("이번 주 계획이 계속 비어 있어. 오늘은 뭐부터 할 거야?", 3)));

  // 값만 계산하는 도구라서 객체를 만들 일이 없습니다.
  private CareGreeting() {}

  // 그 단계에 정해 둔 인사말들을 돌려줍니다. 4단계는 3단계와 같은 인사말입니다.
  public static List<Greeting> greetingsOf(int careLevel) {
    if (careLevel < CareLevel.MIN_LEVEL || careLevel > CareLevel.MAX_LEVEL) {
      throw new IllegalArgumentException(
          "관리 강도는 "
              + CareLevel.MIN_LEVEL
              + " ~ "
              + CareLevel.MAX_LEVEL
              + " 사이여야 해요. 받은 값: "
              + careLevel);
    }
    return GREETINGS.get(Math.min(careLevel, HIGHEST_GREETING_LEVEL));
  }

  // 그 단계의 인사말 중 하나를 아무거나 고릅니다.
  public static String pick(int careLevel, int maxHarshness) {
    return pick(careLevel, maxHarshness, Math.random());
  }

  // 그 단계의 인사말 중 하나를 고릅니다.
  // maxHarshness(허용 수위)보다 센 문구는 고르지 않습니다. 고를 수 있는 것이 하나도 없으면 가장 순한 문구 중에서 고릅니다.
  //
  //   (3단계, 허용 수위 1)  →  "요즘 계속 미뤄지는 것 같아서 걱정돼."  (걱정하는 문장만)
  //   (3단계, 허용 수위 2)  →  걱정·담백·마무리 셋 중 하나
  //   (3단계, 허용 수위 3 이상)  →  팩폭까지 다섯 중 하나
  //
  // random: 0 이상 1 미만의 수. 남은 문구 중 몇 번째를 쓸지 정합니다. 0 이면 항상 첫 번째 문구입니다.
  //         (테스트에서 결과를 고정하려고 받습니다. 평소에는 위의 pick 을 부르면 됩니다)
  public static String pick(int careLevel, int maxHarshness, double random) {
    if (random < 0 || random >= 1) {
      throw new IllegalArgumentException("random 은 0 이상 1 미만이어야 해요. 받은 값: " + random);
    }
    List<Greeting> greetings = greetingsOf(careLevel);

    List<Greeting> candidates = new ArrayList<>();
    for (Greeting greeting : greetings) {
      if (greeting.harshness() <= maxHarshness) {
        candidates.add(greeting);
      }
    }
    // 허용 수위가 너무 낮아서 하나도 안 남으면, 그 단계에서 가장 순한 문구들 중에서 고릅니다.
    if (candidates.isEmpty()) {
      int lowest = Integer.MAX_VALUE;
      for (Greeting greeting : greetings) {
        lowest = Math.min(lowest, greeting.harshness());
      }
      for (Greeting greeting : greetings) {
        if (greeting.harshness() == lowest) {
          candidates.add(greeting);
        }
      }
    }

    return candidates.get((int) Math.floor(random * candidates.size())).text();
  }
}
