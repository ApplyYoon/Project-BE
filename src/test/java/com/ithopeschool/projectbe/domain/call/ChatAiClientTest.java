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
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import java.util.List;
import java.util.Optional;
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

  @Test
  @DisplayName("turn 은 대본 끝에 [답 모양] 을 붙이고 json_schema 로 불러, 받은 JSON 을 말 · 빠른 답 · 행동으로 나눈다")
  void turnAsksForJsonAndParsesIt() {
    ChatAiClient client = new ChatAiClient(KEY, MODEL, builder);
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.store").value(false))
        .andRespond(
            withSuccess(
                """
                {"output":[{"type":"message","content":[{"type":"output_text","text":
                "{\\"reply\\":\\"10분만 해 볼까?\\",\\"quickReplies\\":[\\"좋아\\",\\"오늘은 쉴래\\"],\\"action\\":{\\"type\\":\\"shrink_today\\",\\"minutes\\":10,\\"title\\":null}}"}]}]}
                """,
                MediaType.APPLICATION_JSON));

    Optional<ChatAiClient.ChatTurn> turn = client.turn("채팅 대본", List.of());

    assertThat(turn)
        .contains(
            new ChatAiClient.ChatTurn(
                "10분만 해 볼까?",
                List.of("좋아", "오늘은 쉴래"),
                new ChatReplyResponse.Action("shrink_today", 10, null)));
    openAi.verify();
  }

  @Test
  @DisplayName("긴 계획 버튼(make_plan)은 마감 날(date)까지 읽는다")
  void parseTurnReadsPlanDate() {
    String text =
        "{\"reply\":\"계획 짜 볼까?\",\"quickReplies\":[],\"action\":{\"type\":\"make_plan\","
            + "\"minutes\":null,\"title\":\"중간고사 미적분\",\"date\":\"2026-11-02\"}}";

    assertThat(ChatAiClient.parseTurn(text).action())
        .isEqualTo(new ChatReplyResponse.Action("make_plan", null, "중간고사 미적분", "2026-11-02"));
  }

  @Test
  @DisplayName("받은 글이 JSON 이 아니면 그 글을 그대로 말로 쓰고 버튼은 없다")
  void parseTurnFallsBackToText() {
    assertThat(ChatAiClient.parseTurn("그냥 글이야"))
        .isEqualTo(ChatAiClient.ChatTurn.textOnly("그냥 글이야"));
    assertThat(ChatAiClient.parseTurn("{\"reply\":\"\"}"))
        .isEqualTo(ChatAiClient.ChatTurn.textOnly("{\"reply\":\"\"}"));
  }

  @Test
  @DisplayName("주제가 있는 채팅의 이유 값(tags)을 읽고, 없으면 빈 목록이다")
  void parseTurnReadsTags() {
    String withTags =
        "{\"reply\":\"어땠어?\",\"quickReplies\":[],\"action\":null,\"tags\":[\"phone\",\"tired\"]}";
    String withoutTags = "{\"reply\":\"어땠어?\",\"quickReplies\":[],\"action\":null}";

    assertThat(ChatAiClient.parseTurn(withTags).tags()).containsExactly("phone", "tired");
    assertThat(ChatAiClient.parseTurn(withoutTags).tags()).isEmpty();
  }

  @Test
  @DisplayName("답 모양(TURN_SCHEMA)은 tags 를 꼭 채우게 한다 (strict 모드는 모든 칸이 required 여야 함)")
  void schemaRequiresTags() {
    assertThat(ChatAiClient.TURN_SCHEMA.get("required"))
        .isEqualTo(List.of("reply", "quickReplies", "action", "tags"));
  }
}
