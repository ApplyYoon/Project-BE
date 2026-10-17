package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextRequest;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.settings.SettingsService;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 말로 설정 바꾸기의 로직입니다. 학생의 부탁 한 줄을 관리 설정 변경안으로 바꿉니다. (이슈 #237 표 8번)
// ⚠️ 설정을 바꾸지 않습니다. 변경안만 돌려주고, 학생이 확인하면 FE 가 PATCH /api/v1/settings 로 저장합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. 서버 DB 에 저장된 내 관리 설정을 읽습니다. 관리 설정 보기 API 와 같은 값입니다. (SettingsService.getSettings)
//   3. AI 가 변경안을 씁니다. 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답 · 바뀌는 칸이 없으면 proposal 이 null 입니다.
//
// 독설 수위 · 제재 한도 · 앱 잠금은 말로 바꿀 수 없습니다. AI 에게 줄 칸 모양에 아예 없습니다. (SettingsFromText)
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 설정만 읽습니다.
// 학생이 쓴 글과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class SettingsFromTextService {

  private final CurrentUserProvider currentUserProvider;
  private final SettingsService settingsService;
  private final AiClient aiClient;

  public SettingsFromTextResponse suggest(SettingsFromTextRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현
    if (CrisisText.hasCrisisText(List.of(request.text()))) {
      return SettingsFromTextResponse.help();
    }

    // 2) 지금 설정 (설정을 저장한 적이 없으면 처음 값)
    SettingsResponse settings = settingsService.getSettings();
    SettingsFromText.Current current =
        new SettingsFromText.Current(
            settings.callTime(), settings.dailyCount(), settings.quietStart(), settings.quietEnd());

    // 3) AI
    Optional<SettingsFromText.Answer> answer =
        aiClient.ask(
            userId,
            GptUsageKind.ASSIST,
            SettingsFromText.question(request.text(), current),
            SettingsFromText.Answer.class);
    SettingsFromText.Proposal proposal =
        answer.map(raw -> SettingsFromText.from(raw, current)).orElse(null);
    if (proposal == null) {
      return SettingsFromTextResponse.of(null);
    }
    return SettingsFromTextResponse.of(
        new SettingsFromTextResponse.Proposal(
            proposal.callTime(),
            proposal.dailyCount(),
            proposal.quietStart(),
            proposal.quietEnd(),
            proposal.summary()));
  }
}
