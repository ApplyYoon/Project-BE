package com.ithopeschool.projectbe.domain.care;

import java.util.List;

// 관리 강도 0~4 단계의 이름·설명·기본 이유 문장입니다. (이슈 #62 의 "왜 이 모드인지 항상 말함")
//
//   0 회복 · 1 당근 · 2 넛지 · 3 심리적 채찍 · 4 강제적 채찍(집중 모드)
//
// 오늘의 단계는 CareLevel 이 정하고, 왜 그 단계인지의 문장은 보통 CareDecision.reason 이 맡습니다.
// 그런데 안전 브레이크나 사용자가 정한 한도 때문에 단계가 바뀐 날, 처음 쓰는 날처럼 그 문장이 없는 날이 있습니다.
// 그럴 때 defaultReason 을 대신 보여 줍니다.
// 이유를 모르는 채로 혼내는 말투가 되면 안 되어서, 모두 "지금 어떻게 대하는지" 만 담담하게 알려 줍니다.
//
// FE 의 careLevelInfos · getCareLevelInfo (src/lib/careLevelInfo.ts) 와 같은 문구입니다. (색은 화면의 일이라 뺐습니다)
// 한쪽을 고치면 다른 쪽도 같이 고쳐야 화면과 서버의 말이 어긋나지 않습니다.
//
// level: 단계 번호 (0~4) / name: 단계 이름 (예: "넛지") / description: 한 줄 설명
// defaultReason: 오늘 이 단계일 때 이유 문장이 따로 없으면 보여줄 기본 한 줄
public record CareLevelInfo(int level, String name, String description, String defaultReason) {

  // 다섯 단계의 정보입니다. 목록의 순서가 곧 단계 번호입니다. (0번 = 회복)
  public static final List<CareLevelInfo> ALL =
      List.of(
          new CareLevelInfo(
              0, "회복", "위로만. \"오늘은 10분만\"", "지금은 쉬어 가는 중이야. 오늘은 부담 없이 할 수 있는 만큼만 해 보자."),
          new CareLevelInfo(1, "당근", "구체적 칭찬, 나무 성장", "요즘 잘 해내고 있어서, 잘한 점을 구체적으로 알려주는 중이야."),
          new CareLevelInfo(2, "넛지", "담백한 알림. \"계획 시간 10분 지났어\"", "계획 시간마다 가볍게 챙기는 중이야."),
          new CareLevelInfo(3, "심리적 채찍", "팩폭 말투, 독촉 메시지, 나무 시듦", "계획이 자꾸 미뤄져서, 조금 더 따끔하게 말하는 중이야."),
          new CareLevelInfo(4, "강제적 채찍", "공부 시간에 집중 모드 잠금", "공부 시간에는 집중 모드로 화면을 잠그는 중이야."));

  // 단계 번호로 정보를 찾습니다.
  // 0~4 밖의 값이 들어오면 에러를 내지 않고 가장 약한 0단계(회복)로 봅니다.
  // (단계를 "정하는" CareLevel.of 는 잘못된 값을 거절하지만, 여기는 보여줄 말을 고르는 곳이라
  //  잘못된 값 때문에 센 말이 나가는 것보다 가장 부드러운 말이 나가는 쪽이 안전합니다. FE 도 같습니다)
  public static CareLevelInfo of(int level) {
    for (CareLevelInfo info : ALL) {
      if (info.level() == level) {
        return info;
      }
    }
    return ALL.get(0);
  }
}
