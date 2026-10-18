package com.ithopeschool.projectbe.domain.ai.dto;

import java.util.List;

// AI 자기 분석의 결과입니다. 아무것도 저장하지 않습니다. (FE#419 표 14번, 이슈 #237)
//   { "showHelp": false,
//     "serverFacts": [ { "key": "callHabit1", "group": "distortion", "label": "통화에서 자주 보인 생각 습관",
//                        "value": "계획 오류 · 최근 14일 중 3일", "basis": "AI 통화 정리" } ],
//     "analysis": {
//       "summary": "아침에 더 잘 되는데 스스로는 저녁형이라고 믿는 편이야",
//       "sections": [ { "group": "distortion", "headline": "저녁형이라고 했지만 아침 카드가 더 잘 돼",
//                       "body": "아침 카드 실행률은 95%, 밤 카드는 63%야. 밤에 하는 게 익숙해서 그렇게 느꼈을 수 있어.",
//                       "evidenceKeys": ["hypothesisChronotype"] }, … ],
//       "nextExperiment": { "ifText": "만약 학원에서 돌아오면", "thenText": "폰을 다른 방에 두고 오답부터 편다",
//                           "why": "밤 수학 오답 카드를 놓친 날의 원인 1위가 폰이었어" } } }
//
// - serverFacts: 서버가 DB 에서 읽어 더한 숫자. AI 가 꺼져 있어도 옵니다. 없으면 빈 목록입니다.
// - analysis 가 null 이면 AI 가 쓰지 못한 것입니다. (AI 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답)
//   에러가 아니라 200 이고, FE 는 코드가 숫자로 만든 기본 문장을 보여 줍니다.
// - summary 가 null 이면 요약 한 줄만 못 쓴 것입니다. (FE 는 요약만 기본 문장으로 바꿉니다)
// - nextExperiment 가 null 이면 실험 제안만 못 쓴 것입니다.
// - sections 의 evidenceKeys 는 보낸 숫자(facts)와 serverFacts 의 key 중에서만 옵니다. 화면은 그 숫자를 근거로 같이 보여 줍니다.
// - showHelp 가 true 면 보낸 글(카드 이름 등)에서 위기 표현이 감지된 것입니다. AI 를 부르지 않았고, FE 는 도움 안내를 먼저 보여 줍니다.
public record AnalysisResponse(
    boolean showHelp, List<AnalysisRequest.Fact> serverFacts, Analysis analysis) {

  // AI 가 쓴 분석. 서버가 검사를 통과한 것만 담습니다. (SelfAnalysis.from)
  public record Analysis(String summary, List<Section> sections, Experiment nextExperiment) {}

  // 묶음 하나의 분석
  //   group       : tendency(성향) · distortion(생각과 실제) · willpower(약속 지키기) · efficiency(공부 효율) ·
  // failure(실패 원인)
  //   headline    : 한 줄 (40자까지)
  //   body        : 사실 한 문장 + 가설로 말한 해석 한 문장 (160자까지)
  //   evidenceKeys: 근거로 쓴 숫자의 key
  public record Section(String group, String headline, String body, List<String> evidenceKeys) {}

  // 2주 동안 해 볼 실험 하나 ("만약 ~하면 → ~한다")
  public record Experiment(String ifText, String thenText, String why) {}

  public static AnalysisResponse of(List<AnalysisRequest.Fact> serverFacts, Analysis analysis) {
    return new AnalysisResponse(false, serverFacts, analysis);
  }

  // 위기 표현이 감지됐을 때의 응답입니다. AI 를 부르지 않습니다.
  public static AnalysisResponse help() {
    return new AnalysisResponse(true, List.of(), null);
  }
}
