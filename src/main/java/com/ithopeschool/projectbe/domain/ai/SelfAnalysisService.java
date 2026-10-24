package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.AnalysisRequest;
import com.ithopeschool.projectbe.domain.ai.dto.AnalysisResponse;
import com.ithopeschool.projectbe.domain.call.CallRecordService;
import com.ithopeschool.projectbe.domain.call.dto.BiasHabitResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// AI 자기 분석의 로직입니다. 기록에서 계산한 숫자를 받아 AI 가 묶음별 분석과 다음 실험 제안을 씁니다. (FE#419 표 14번, 이슈 #237)
// ⚠️ 아무것도 저장하지 않습니다. FE 가 하루에 한 번만 묻고 그날 받은 답을 저장해 둡니다.
//
// 순서
//   1. 같은 key 의 숫자가 두 번 오면 처음 것만 씁니다.
//   2. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. 카드 이름이 값 안에 들어올 수 있어서입니다.
//   3. 서버 DB 에 있는 것을 더합니다: 최근 14일 통화에서 자주 보인 생각 습관 (최대 2개, CallRecordService.getHabits)
//   4. AI 가 씁니다. 답이 길어서 askLong(25초)으로 묻습니다. 호출 기록은 작은 AI 기능들과 같은 ASSIST 로 남깁니다.
//      꺼짐 · 실패 · 쓸 수 없는 답이면 analysis 가 null 이고, FE 는 기본 문장을 씁니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 기록만 읽습니다.
// AI 에게는 숫자와 그 이름만 줍니다. 이름 · 학교 · 통화 근거 문장(lastEvidence) 은 주지 않습니다.
@Service
@RequiredArgsConstructor
public class SelfAnalysisService {

  // 서버가 더하는 생각 습관의 최대 수
  static final int MAX_CALL_HABITS = 2;

  private final CurrentUserProvider currentUserProvider;
  private final CallRecordService callRecordService;
  private final AiClient aiClient;

  public AnalysisResponse analyze(AnalysisRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 같은 key 는 처음 것만
    List<AnalysisRequest.Fact> facts = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    for (AnalysisRequest.Fact fact : request.facts()) {
      if (keys.add(fact.key())) {
        facts.add(fact);
      }
    }

    // 2) 위기 표현
    List<String> texts = new ArrayList<>();
    for (AnalysisRequest.Fact fact : facts) {
      texts.add(fact.label());
      texts.add(fact.value());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return AnalysisResponse.help();
    }

    // 3) 서버 DB 의 생각 습관
    List<AnalysisRequest.Fact> serverFacts = callHabitFacts(keys);
    List<AnalysisRequest.Fact> all = new ArrayList<>(facts);
    all.addAll(serverFacts);

    // 4) AI
    Optional<SelfAnalysis.Answer> answer =
        aiClient.askLong(
            userId, GptUsageKind.ASSIST, SelfAnalysis.question(all), SelfAnalysis.Answer.class);
    AnalysisResponse.Analysis analysis =
        answer.map(raw -> SelfAnalysis.from(raw, all)).orElse(null);
    return AnalysisResponse.of(serverFacts, analysis);
  }

  // 최근 14일 통화에서 자주 보인 생각 습관을 숫자 모양으로 바꿉니다. 보낸 key 와 겹치지 않게 callHabit1, callHabit2 …
  //   { key: "callHabit1", group: "distortion", label: "통화에서 자주 보인 생각 습관",
  //     value: "계획 오류 · 최근 14일 중 3일", basis: "AI 통화 정리" }
  private List<AnalysisRequest.Fact> callHabitFacts(Set<String> usedKeys) {
    List<AnalysisRequest.Fact> facts = new ArrayList<>();
    List<BiasHabitResponse> habits = callRecordService.getHabits();
    for (int i = 0; i < habits.size() && facts.size() < MAX_CALL_HABITS; i++) {
      BiasHabitResponse habit = habits.get(i);
      String key = "callHabit" + (facts.size() + 1);
      if (usedKeys.contains(key)) {
        continue;
      }
      facts.add(
          new AnalysisRequest.Fact(
              key,
              "distortion",
              "통화에서 자주 보인 생각 습관",
              habit.label() + " · 최근 14일 중 " + habit.days() + "일",
              "AI 통화 정리"));
    }
    return facts;
  }
}
