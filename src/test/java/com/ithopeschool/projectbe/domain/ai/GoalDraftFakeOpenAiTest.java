package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import com.ithopeschool.projectbe.domain.goalchat.GoalChatPrompt;
import com.ithopeschool.projectbe.domain.goalchat.GoalDraft;
import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// '목표 만들기' 대화의 초안(이슈 #239)을 가짜 OpenAI 서버로 확인합니다. GPT 를 진짜로 부르지 않습니다.
// "보내는 질문의 칸 모양" 과 "받은 JSON 을 초안 칸(dDay 등)으로 읽는지" 를 봅니다.
// AiClient 의 테스트용 생성자가 이 패키지(ai)에만 열려 있어서 이 테스트도 여기에 둡니다.
// ⚠️ 진짜 GPT 가 이 모양으로 답하는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
class GoalDraftFakeOpenAiTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  private final GptUsageGuard gptUsageGuard = mock(GptUsageGuard.class);
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
  private final AiClient aiClient = new AiClient("test-key", "test-model", gptUsageGuard, builder);

  @Test
  @DisplayName("초안 질문은 goal_draft 칸 모양으로 보내고, 받은 JSON 을 목표 저장 칸으로 읽어 검사한다")
  void readsDraftFromOpenAi() {
    // OpenAI 가 돌려주는 글 안의 JSON (따옴표는 \" 로 넣습니다)
    String draftJson =
        "{\\\"kind\\\":\\\"completion\\\",\\\"title\\\":\\\"영단어장 1회독\\\",\\\"dDay\\\":\\\"2026-11-30\\\","
            + "\\\"currentScore\\\":null,\\\"totalAmount\\\":300,\\\"amountUnit\\\":\\\"쪽\\\","
            + "\\\"benefit\\\":\\\"단어 걱정이 줄어듦\\\",\\\"cost\\\":\\\"또 미루게 됨\\\","
            + "\\\"obstacle\\\":\\\"자기 전에 폰을 봄\\\",\\\"plan\\\":\\\"폰이 보이면 책상 서랍에 넣는다\\\"}";
    openAi
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andExpect(jsonPath("$.text.format.name").value("goal_draft"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(
            withSuccess(
                "{ \"output\": [ { \"type\": \"message\", \"content\": [ { \"type\": \"output_text\","
                    + " \"text\": \""
                    + draftJson
                    + "\" } ] } ] }",
                MediaType.APPLICATION_JSON));

    List<Line> lines = List.of(new Line("ai", GoalChatPrompt.OPENING), new Line("me", "영단어장 1회독"));
    Optional<GoalChatPrompt.Answer> answer =
        aiClient.ask(
            USER_ID,
            GptUsageKind.ASSIST,
            GoalChatPrompt.draftQuestion(lines, TODAY),
            GoalChatPrompt.Answer.class);
    GoalDraft draft = GoalDraft.from(answer.orElse(null), TODAY);

    openAi.verify();
    assertThat(draft)
        .isEqualTo(
            new GoalDraft(
                "completion",
                "영단어장 1회독",
                "2026-11-30",
                null,
                300,
                "쪽",
                "단어 걱정이 줄어듦",
                "또 미루게 됨",
                "자기 전에 폰을 봄",
                "폰이 보이면 책상 서랍에 넣는다"));
  }
}
