package com.ithopeschool.projectbe.domain.call.dto;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;

// 통화를 마무리한 결과입니다. FE 의 통화 결과 화면(/call/result)이 이 값으로 "오늘의 결론" 을 보여 줍니다.
//
//   { "callId": "12", "showHelp": false,
//     "summary": "폰 때문에 늦게 시작했다고 함. 오늘은 폰을 서랍에 두기로 함.",
//     "bias": { "key": "FUTURE_SELF", "label": "미래의 나 믿기",
//               "description": "\"내일의 나는 더 잘할 거야\" 하고 지금 할 일을 뒤로 미루는 생각",
//               "evidence": "이따가 하면 된다고 함", "solution": "\"내일부터\" 대신 오늘 5분짜리 시작 하나 정하기" },
//     "missReason": "phone", "promiseIf": "학원에서 오면", "promiseThen": "폰을 서랍에 넣기",
//     "promiseKept": false, "mood": 3, "expectedMinutes": 120 }
//
// - 모르는 칸은 null 입니다. 생각 습관이 뚜렷하게 보이지 않았으면 bias 가 null 입니다.
// - showHelp 가 true 면 위기 표현이 감지된 통화입니다. 다른 칸은 모두 null 이고, FE 는 도움 연결 안내를 먼저 보여 줍니다.
// - missReason · mood · expectedMinutes 는 FE 가 그날 체크인에 채워 넣는 데 씁니다. (통화로 답한 것을 다시 묻지 않게)
public record CallFinishResponse(
    String callId,
    boolean showHelp,
    String summary,
    Bias bias,
    String missReason,
    String promiseIf,
    String promiseThen,
    Boolean promiseKept,
    Integer mood,
    Integer expectedMinutes) {

  // 대답에서 보인 생각 습관 하나. 이름은 생각에 붙이는 이름입니다. ("너는 ~형" 이 아니라 "그런 생각을 ~라고 해")
  // key: 습관을 구분하는 이름 (CognitiveBias 의 이름 그대로) / label: 보여 줄 이름 / description: 어떤 생각인지
  // evidence: 그 습관이 보인 근거 한 줄. 없으면 null / solution: 알아차린 뒤 해 볼 행동 하나
  public record Bias(
      String key, String label, String description, String evidence, String solution) {

    public static Bias of(CognitiveBias bias, String evidence) {
      return new Bias(
          bias.name(), bias.getLabel(), bias.getDescription(), evidence, bias.getSolution());
    }
  }

  // 위기 표현이 감지된 통화의 결과. 도움 안내만 보여 주고 다른 내용은 담지 않습니다.
  public static CallFinishResponse help(String callId) {
    return new CallFinishResponse(callId, true, null, null, null, null, null, null, null, null);
  }
}
