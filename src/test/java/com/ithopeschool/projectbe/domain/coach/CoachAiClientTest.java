package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// GPT 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버(MockRestServiceServer)를 끼워 넣고,
// "우리가 약속한 모양으로 보내는지" 와 "여러 가지 답을 제대로 읽는지" 를 봅니다.
// ⚠️ 진짜 GPT 와 맞는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
@ExtendWith(OutputCaptureExtension.class)
class CoachAiClientTest {

  // 테스트용 가짜 키입니다. 진짜 키처럼 보이지 않게 적었습니다.
  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-model";
  private static final String URL = "https://api.openai.com/v1/responses";

  private final RestClient.Builder builder = RestClient.builder();
  // bindTo: 이 builder 로 만든 RestClient 의 요청이 진짜 인터넷 대신 가짜 서버로 갑니다.
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private CoachAiClient clientWith(String key, String model) {
    return new CoachAiClient(key, model, builder);
  }

  // OpenAI 가 글 하나를 돌려줄 때의 응답 모양입니다. (공식 문서의 예시에서 우리가 읽는 부분)
  private static String answerOf(String text) {
    return """
        {
          "id": "resp_1",
          "status": "completed",
          "output": [
            { "type": "reasoning", "summary": [] },
            {
              "type": "message",
              "role": "assistant",
              "content": [ { "type": "output_text", "text": "%s", "annotations": [] } ]
            }
          ],
          "usage": { "input_tokens": 10, "output_tokens": 20 }
        }
        """
        .formatted(text);
  }

  @Test
  @DisplayName("키와 모델 이름이 둘 다 있어야 켜진다")
  void enabledOnlyWithKeyAndModel() {
    assertThat(clientWith(KEY, MODEL).isEnabled()).isTrue();

    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, "").isEnabled()).isFalse();
    assertThat(clientWith("   ", "  ").isEnabled()).isFalse();
    assertThat(clientWith(null, null).isEnabled()).isFalse();
  }

  @Test
  @DisplayName("꺼져 있으면 OpenAI 를 부르지 않고 빈 값을 돌려준다")
  void doesNotCallWhenDisabled() {
    openAi.expect(never(), anything());

    assertThat(clientWith("", "").ask("지침", "입력")).isEmpty();
    assertThat(clientWith(KEY, "").ask("지침", "입력")).isEmpty();

    openAi.verify();
  }

  @Test
  @DisplayName("약속한 주소·헤더·본문으로 부르고, 답에서 글만 꺼낸다")
  void sendsTheAgreedRequestAndReadsTheText() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.instructions").value("규칙을 지켜"))
        .andExpect(jsonPath("$.input").value("이번 주 숫자야."))
        // OpenAI 쪽에 요청과 답을 보관하지 않게 합니다.
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(withSuccess(answerOf("이번 주는 계획의 64% 를 했어."), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).ask("규칙을 지켜", "이번 주 숫자야.")).contains("이번 주는 계획의 64% 를 했어.");
    openAi.verify();
  }

  @Test
  @DisplayName("글 조각이 여러 개면 이어 붙이고, 앞뒤 공백은 지운다")
  void joinsTextParts() {
    String body =
        """
        { "output": [
          { "type": "message", "content": [
            { "type": "output_text", "text": "  첫 문장이야. " },
            { "type": "refusal", "refusal": "이건 글이 아님" },
            { "type": "output_text", "text": "둘째 문장이야.  " }
          ] }
        ] }
        """;
    openAi.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).contains("첫 문장이야. 둘째 문장이야.");
  }

  @Test
  @DisplayName("답에 글이 없으면 빈 값을 돌려준다")
  void emptyWhenThereIsNoText() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"output\": [] }", MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).isEmpty();
  }

  @Test
  @DisplayName("답에 output 이 아예 없거나 생각한 과정만 있어도 빈 값을 돌려준다")
  void emptyWhenOutputIsMissing() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"status\": \"incomplete\" }", MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).isEmpty();

    openAi.reset();
    openAi
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                "{ \"output\": [ { \"type\": \"reasoning\", \"summary\": [] } ] }",
                MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).isEmpty();
  }

  @Test
  @DisplayName("OpenAI 가 거절하거나(401·429) 고장 났을 때(500) 에러를 던지지 않고 빈 값을 돌려준다")
  void emptyWhenOpenAiFails(CapturedOutput output) {
    for (HttpStatus status :
        new HttpStatus[] {
          HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR
        }) {
      openAi.reset();
      openAi
          .expect(requestTo(URL))
          .andRespond(
              withStatus(status)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body("{\"error\":{\"message\":\"OpenAI 가 돌려준 내용\"}}"));

      assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).isEmpty();
    }

    // 실패했다는 사실만 남기고, OpenAI 가 돌려준 내용은 로그에 찍지 않습니다.
    assertThat(output).contains("GPT 호출에 실패했어요");
    assertThat(output).doesNotContain("OpenAI 가 돌려준 내용");
  }

  @Test
  @DisplayName("JSON 이 아닌 답이 와도 에러를 던지지 않고 빈 값을 돌려준다")
  void emptyWhenTheAnswerIsNotJson() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("<html>점검 중</html>", MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).ask("지침", "입력")).isEmpty();
  }

  @Test
  @DisplayName("키와 주고받은 글은 로그에 남지 않는다")
  void keyAndTextsAreNotLogged(CapturedOutput output) {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess(answerOf("받은 글은 비밀이야"), MediaType.APPLICATION_JSON));

    clientWith(KEY, MODEL).ask("보낸 지침은 비밀이야", "보낸 입력도 비밀이야");

    assertThat(output).doesNotContain(KEY);
    assertThat(output).doesNotContain("보낸 지침은 비밀이야");
    assertThat(output).doesNotContain("보낸 입력도 비밀이야");
    assertThat(output).doesNotContain("받은 글은 비밀이야");
  }
}
