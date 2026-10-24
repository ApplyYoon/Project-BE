package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ProfileLinesResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 성향 요약 3줄의 로직입니다. (이슈 #237 표 9번)
// ⚠️ 아무것도 저장하지 않습니다.
//
// 순서
//   1. 서버 DB 에 저장된 내 가장 최근 심리검사(#219)를 읽습니다. 없으면 lines 가 null 입니다.
//      답이 거의 다 같아서 믿기 어려우면 AI 를 부르지 않고 정해 둔 3줄입니다. (ProfileLines.UNSURE_LINES, 2026-10-18)
//   2. 가장 두드러진 척도 3개를 코드가 고릅니다. (ProfileLines.pick)
//   3. AI 가 척도마다 한 줄씩 씁니다. 꺼짐 · 실패 · 쓸 수 없는 답이면 lines 가 null 입니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 검사만 읽습니다.
@Service
@RequiredArgsConstructor
public class ProfileLinesService {

  private final CurrentUserProvider currentUserProvider;
  private final PsychTestRepository psychTestRepository;
  private final AiClient aiClient;

  public ProfileLinesResponse suggest() {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 내 가장 최근 심리검사
    Optional<PsychTest> latest = psychTestRepository.findFirstByUserIdOrderByIdDesc(userId);
    if (latest.isEmpty()) {
      return new ProfileLinesResponse(null);
    }
    if (!latest.get().isReliable()) {
      return new ProfileLinesResponse(ProfileLines.UNSURE_LINES);
    }

    // 2) 척도 고르기
    List<ProfileLines.Pick> picks =
        ProfileLines.pick(PsychScore.scoresOf(latest.get().getAnswers()));

    // 3) AI
    Optional<ProfileLines.Answer> answer =
        aiClient.ask(
            userId, GptUsageKind.ASSIST, ProfileLines.question(picks), ProfileLines.Answer.class);
    return new ProfileLinesResponse(answer.map(ProfileLines::from).orElse(null));
  }
}
