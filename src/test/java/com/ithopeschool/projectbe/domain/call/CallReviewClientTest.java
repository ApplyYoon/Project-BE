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

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// GPT 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버를 끼워 넣고,
// "칸 모양을 같이 보내는지" 와 "받은 JSON 을 제대로 읽는지" 를 봅니다.
// ⚠️ 진짜 GPT 가 이 모양으로 답하는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 불러 봐야 합니다.
class CallReviewClientTest {

  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-model";
  private static final String URL = "https://api.openai.com/v1/responses";
  private static final Map<String, Object> SCHEMA = Map.of("type", "object");

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private CallReviewClient clientWith(String key, String model) {
    return new CallReviewClient(key, model, builder);
  }

  // OpenAI 가 글 하나를 돌려주는 응답. 글 안의 JSON 은 따옴표를 \" 로, 줄바꿈을 \n 으로 바꿔 넣습니다.
  private static String answerOf(String text) {
    String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    return """
        { "output": [ { "type": "message",
            "content": [ { "type": "output_text", "text": "%s" } ] } ] }
        """
        .formatted(escaped);
  }

  private static final String REVIEW_JSON =
      """
      {"crisis":false,"summary":"폰 때문에 늦었다고 함.","foundBias":"FUTURE_SELF",
       "foundEvidence":"이따가 하면 된다고 함","actualQuestion":null,"missReason":"phone",
       "promiseIf":"학원에서 오면","promiseThen":"폰을 서랍에 넣기","promiseKept":null,
       "mood":3,"expectedMinutes":90}
      """;

  @Test
  @DisplayName("키와 모델 이름이 둘 다 있어야 켜지고, 꺼져 있으면 OpenAI 를 부르지 않는다")
  void enabledOnlyWithKeyAndModel() {
    openAi.expect(never(), anything());

    assertThat(clientWith(KEY, MODEL).isEnabled()).isTrue();
    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, " ").isEnabled()).isFalse();
    assertThat(clientWith("", "").review("지침", "자막", SCHEMA)).isEmpty();
    openAi.verify();
  }

  @Test
  @DisplayName("칸 모양(json_schema)을 같이 보내고, 보관하지 말라고(store=false) 하고, 받은 JSON 을 칸별로 읽는다")
  void sendsTheSchemaAndReadsTheJson() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(jsonPath("$.model").value(MODEL))
        .andExpect(jsonPath("$.instructions").value("지침"))
        .andExpect(jsonPath("$.input").value("자막"))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.name").value("call_review"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.text.format.schema.type").value("object"))
        .andRespond(withSuccess(answerOf(REVIEW_JSON), MediaType.APPLICATION_JSON));

    CallReview.Raw raw = clientWith(KEY, MODEL).review("지침", "자막", SCHEMA).orElseThrow();

    assertThat(raw.crisis()).isFalse();
    assertThat(raw.summary()).isEqualTo("폰 때문에 늦었다고 함.");
    assertThat(raw.foundBias()).isEqualTo("FUTURE_SELF");
    assertThat(raw.actualQuestion()).isNull();
    assertThat(raw.missReason()).isEqualTo("phone");
    assertThat(raw.promiseKept()).isNull();
    assertThat(raw.mood()).isEqualTo(3);
    assertThat(raw.expectedMinutes()).isEqualTo(90);
    openAi.verify();
  }

  @Test
  @DisplayName("답이 JSON 이 아니면 빈 값이다")
  void notJsonMeansEmpty() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess(answerOf("정리했어요!"), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).review("지침", "자막", SCHEMA)).isEmpty();
  }

  @Test
  @DisplayName("답에 글이 없으면 빈 값이다")
  void noTextMeansEmpty() {
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{ \"output\": [] }", MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).review("지침", "자막", SCHEMA)).isEmpty();
  }

  @Test
  @DisplayName("OpenAI 가 에러로 답하면 빈 값이다 (에러를 던지지 않는다)")
  void errorMeansEmpty() {
    openAi.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThat(clientWith(KEY, MODEL).review("지침", "자막", SCHEMA)).isEmpty();
  }
}
