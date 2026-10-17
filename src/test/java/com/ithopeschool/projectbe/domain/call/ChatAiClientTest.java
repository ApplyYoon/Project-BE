package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest.Line;
import java.util.List;
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

// GPT 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버(MockRestServiceServer)를 끼워 넣습니다. (이슈 #238 "가짜 OpenAI 서버
// 테스트")
// ⚠️ 진짜 GPT 와 맞는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
@ExtendWith(OutputCaptureExtension.class)
class ChatAiClientTest {

  // 테스트용 가짜 키입니다. 진짜 키처럼 보이지 않게 적었습니다.
  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-model";
  private static final String URL = "https://api.openai.com/v1/responses";

  private final RestClient.Builder builder = RestClient.builder();
  // bindTo: 이 builder 로 만든 RestClient 의 요청이 진짜 인터넷 대신 가짜 서버로 갑니다.
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private ChatAiClient clientWith(String key, String model) {
    return new ChatAiClient(key, model, builder);
  }

  // OpenAI 가 글 하나를 돌려줄 때의 응답 모양 (CoachAiClientTest 와 같음)
  private static String answerOf(String text) {
    return """
        { "output": [
          { "type": "reasoning", "summary": [] },
          { "type": "message", "role": "assistant",
            "content": [ { "type": "output_text", "text": "%s", "annotations": [] } ] }
        ] }
        """
        .formatted(text);
  }

  private static final List<Line> TALK =
      List.of(new Line("ai", "어제 어땠어?"), new Line("me", "폰 보다가 늦게 시작했어"));

  @Test
  @DisplayName("키와 모델 이름이 둘 다 있어야 켜지고, 꺼져 있으면 OpenAI 를 부르지 않는다")
  void doesNotCallWhenDisabled() {
    openAi.expect(never(), anything());

    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, " ").isEnabled()).isFalse();
    assertThat(clientWith("", "").reply("지침", TALK)).isEmpty();

    openAi.verify();
  }

  @Test
  @DisplayName("대본을 instructions 로, 대화를 '코치: / 학생:' 줄로 보내고, 답에서 글만 꺼낸다 (store=false)")
  void sendsTheScriptAndTheTalk() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.instructions").value("채팅 대본"))
        .andExpect(
            jsonPath("$.input")
                .value("코치: 어제 어땠어?\n학생: 폰 보다가 늦게 시작했어\n\n(위 대화에 이어서 코치로서 다음 말을 해.)"))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(withSuccess(answerOf("그때 폰은 어디 있었어?"), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).contains("그때 폰은 어디 있었어?");
    openAi.verify();
  }

  @Test
  @DisplayName("채팅을 막 열었으면(대화 없음) 첫 인사를 부탁한다")
  void asksForTheFirstGreeting() {
    assertThat(ChatAiClient.inputOf(List.of())).isEqualTo("(학생이 방금 채팅을 열었어. [통화 순서] 1번부터 시작해.)");
  }

  @Test
  @DisplayName("OpenAI 가 실패하면 빈 값이고, 키·대화·답은 로그에 남지 않는다")
  void emptyWhenOpenAiFailsAndNothingIsLogged(CapturedOutput output) {
    openAi
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"OpenAI 가 돌려준 내용\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).isEmpty();
    assertThat(output)
        .doesNotContain(KEY)
        .doesNotContain("폰 보다가 늦게 시작했어")
        .doesNotContain("OpenAI 가 돌려준 내용");
  }

  @Test
  @DisplayName("답에 글이 없으면 빈 값이다")
  void emptyWhenThereIsNoText() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"output\": [] }", MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).reply("채팅 대본", TALK)).isEmpty();
  }
}
