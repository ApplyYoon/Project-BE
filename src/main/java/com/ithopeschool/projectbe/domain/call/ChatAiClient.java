package com.ithopeschool.projectbe.domain.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 상담 채팅에서 GPT(OpenAI) 의 답을 받는 곳입니다. (이슈 #238)
// 코치 문단(CoachAiClient)과 같은 방식 · 같은 설정입니다. 채팅은 답을 조금 더 기다리는 것만 다릅니다.
// (공통 AI 틀(이슈 #237, ai/ 패키지)은 정해진 JSON 모양으로만 받아서, 글 그대로 받는 이 클라이언트를 따로 두었습니다)
//
// ■ 배포 서버에서만 켜집니다
//   GPT 키와 모델 이름은 application-prod.yml 에만 자리가 있습니다. (환경변수 OPENAI_API_KEY, OPENAI_MODEL)
//   내 컴퓨터(local)에서는 꺼져 있습니다. (isEnabled() 가 false)
//
// ■ 실패해도 에러를 던지지 않습니다. 빈 값(Optional.empty)을 돌려주면 부르는 쪽(ChatService)이 503 으로 알립니다.
//
// ⚠️ 키, 보낸 대화, 받은 답은 로그에 찍지 않습니다. 학생의 마음 이야기라서입니다. (CLAUDE.md 4장)
//
// 부르는 방법은 OpenAI 공식 문서의 Responses API 를 따랐습니다. (CoachAiClient 와 같음)
//   POST https://api.openai.com/v1/responses   { model, instructions, input, store }
@Component
public class ChatAiClient {

  private static final Logger log = LoggerFactory.getLogger(ChatAiClient.class);

  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

  // OpenAI 에 연결되기를 기다리는 최대 시간
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  // 답을 기다리는 최대 시간. 채팅은 학생이 답을 기다리는 화면이라 15초가 지나면 포기하고 다시 보내게 합니다.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
  // 사진이 붙은 말은 사진을 읽느라 오래 걸려서 25초까지 기다립니다. (AiClient.askWithImage 와 같음)
  private static final Duration IMAGE_READ_TIMEOUT = Duration.ofSeconds(25);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  // 상담 채팅 답의 모양(JSON schema)입니다. OpenAI 가 이 모양으로만 답하게 합니다. (turn)
  //   { "reply": "…", "quickReplies": ["…"], "action": { "type": "none", "minutes": null, "title":
  // null, "date": null }, "tags": [] }
  // tags 는 "코치랑 이야기하기" 주제(ChatTopic)가 있을 때 AI 가 학생 말에서 짐작한 이유 값입니다. (2026-10-18)
  // 개수 · 길이 제한은 schema 대신 서버가 검사합니다. (ChatService.cleanTurn)
  static final Map<String, Object> TURN_SCHEMA =
      Map.of(
          "type",
          "object",
          "additionalProperties",
          false,
          "required",
          List.of("reply", "quickReplies", "action", "tags"),
          "properties",
          Map.of(
              "reply",
              Map.of("type", "string"),
              "quickReplies",
              Map.of("type", "array", "items", Map.of("type", "string")),
              "action",
              Map.of(
                  "type",
                  "object",
                  "additionalProperties",
                  false,
                  "required",
                  List.of("type", "minutes", "title", "date"),
                  "properties",
                  Map.of(
                      "type",
                      Map.of(
                          "type",
                          "string",
                          "enum",
                          List.of(
                              "none",
                              "start_timer",
                              "shrink_today",
                              "create_card",
                              "make_plan",
                              "start_quiz")),
                      "minutes",
                      Map.of("type", List.of("integer", "null")),
                      "title",
                      Map.of("type", List.of("string", "null")),
                      "date",
                      Map.of("type", List.of("string", "null")))),
              "tags",
              Map.of("type", "array", "items", Map.of("type", "string"))));

  // 상담 채팅 대본 끝에 붙이는 "답 모양" 규칙입니다.
  static final String TURN_RULES =
      String.join(
          "\n",
          "",
          "",
          "[답 모양]",
          "- reply: 학생에게 할 말. 두세 문장.",
          "- quickReplies: 학생이 눌러서 바로 보낼 짧은 답 2~3개. 학생 말투로, 각각 15자 안쪽."
              + " 예: '좋아, 해 볼게' · '하나만 할래' · '오늘은 힘들어'.",
          "- action: 학생이 지금 공부를 시작하겠다고 하면 start_timer."
              + " 목표를 줄이기로 했으면 shrink_today 와 minutes(30~60). 카드 수를 줄이자는 뜻이야."
              + " 새로 할 일을 정했으면 create_card 와 title(20자 안쪽) · minutes(30~120, 보통 40~60)."
              + " 10분 · 20분처럼 30분보다 짧은 공부는 권하지 마."
              + " 시험 · 대회 · 수행평가처럼 무엇을 언제까지 준비한다고 하면 make_plan 과 title(목표 한 줄, 30자 안쪽) ·"
              + " date(마감 날 YYYY-MM-DD, 모르면 null). 학생이 방금 공부를 끝냈다고 하거나 잘 알았는지 확인해 달라고 하면"
              + " start_quiz 와 title(공부한 주제, 30자 안쪽). 아니면 none. date 는 make_plan 에만 써.",
          "- tags: [이번 대화의 주제] 에 '고를 수 있는 이유' 가 있을 때만, 학생 말에서 보이는 이유 값 0~3개."
              + " 그 목록에 없는 값은 쓰지 마. 주제가 없거나 이유가 안 보이면 빈 목록.",
          "- 학생이 힘든 마음을 말하면 action 은 none 이고 quickReplies · tags 는 비워.");

  private final String apiKey;
  private final String model;
  private final RestClient restClient;
  // 사진이 붙은 말을 보낼 때 쓰는 연결 (더 오래 기다림)
  private final RestClient imageRestClient;

  // Spring 이 쓰는 생성자입니다. 설정에서 키와 모델 이름을 받습니다. 둘 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public ChatAiClient(
      @Value("${app.ai.openai-api-key:}") String apiKey, @Value("${app.ai.model:}") String model) {
    this(
        apiKey,
        model,
        RestClient.builder().requestFactory(requestFactoryWithTimeouts(READ_TIMEOUT)),
        RestClient.builder().requestFactory(requestFactoryWithTimeouts(IMAGE_READ_TIMEOUT)));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (ChatAiClientTest) 사진도 같은 가짜로 보냅니다.
  ChatAiClient(String apiKey, String model, RestClient.Builder builder) {
    this(apiKey, model, builder, builder);
  }

  private ChatAiClient(
      String apiKey, String model, RestClient.Builder builder, RestClient.Builder imageBuilder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();
    this.imageRestClient = imageBuilder.baseUrl(OPENAI_BASE_URL).build();
  }

  // GPT 를 부를 수 있는 서버인지 알려줍니다. 키와 모델 이름이 둘 다 있어야 true 입니다.
  public boolean isEnabled() {
    return !apiKey.isEmpty() && !model.isEmpty();
  }

  // 대본(지침)과 지금까지의 대화를 주고, AI 코치의 다음 말을 받습니다. 꺼져 있거나 실패하면 빈 값입니다.
  // lines 가 비어 있으면 채팅을 막 연 것이라 첫 인사를 부탁합니다.
  public Optional<String> reply(String instructions, List<CallFinishRequest.Line> lines) {
    if (!isEnabled()) {
      return Optional.empty();
    }
    try {
      // store=false: OpenAI 쪽에 이 요청과 답을 보관하지 말라는 뜻입니다. (미성년자의 기록이라 남기지 않습니다)
      OpenAiRequest request = new OpenAiRequest(model, instructions, inputOf(lines), false);
      OpenAiResponse response =
          restClient
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + apiKey)
              .body(request)
              .retrieve()
              .body(OpenAiResponse.class);
      return textOf(response);
    } catch (RestClientException e) {
      // 시간 초과, 연결 실패, 4xx·5xx 응답이 모두 여기로 옵니다. 예외 메시지에는 대화가 들어 있을 수 있어서 종류만 찍습니다.
      log.warn("[AI] 채팅 답을 받지 못했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 상담 채팅의 답 한 번: 학생에게 할 말 + 누를 수 있는 짧은 답 + 행동 하나 + 짐작한 이유 값. (2026-10-18)
  public record ChatTurn(
      String reply, List<String> quickReplies, ChatReplyResponse.Action action, List<String> tags) {

    // 이유 값 없는 답 (주제 없는 채팅 · 테스트에서 씀)
    public ChatTurn(String reply, List<String> quickReplies, ChatReplyResponse.Action action) {
      this(reply, quickReplies, action, List.of());
    }

    // 버튼 없이 말만 있는 답
    public static ChatTurn textOnly(String reply) {
      return new ChatTurn(reply, List.of(), null);
    }
  }

  // 대본과 대화를 주고, AI 코치의 다음 말과 버튼을 정해진 모양(TURN_SCHEMA)으로 받습니다. 꺼져 있거나 실패하면 빈 값입니다.
  // reply() 와 같은 방법으로 부르고, 대본 끝에 [답 모양] 규칙과 칸 모양을 더할 뿐입니다.
  public Optional<ChatTurn> turn(String instructions, List<CallFinishRequest.Line> lines) {
    return sendTurn(restClient, instructions, inputOf(lines));
  }

  // turn 과 같고, 학생의 마지막 말에 붙은 사진 한 장을 함께 보냅니다. (2026-10-18 사진 질문)
  //   imageDataUrl : "data:image/jpeg;base64,..." 모양의 사진 (OpenAI 문서 Images and vision 의
  // input_image)
  // 보내는 모양: [ { "role": "user", "content": [ { "type": "input_text", "text": "코치: … 학생: …" },
  //                 { "type": "input_image", "image_url": "data:...", "detail": "auto" } ] } ]
  // ⚠️ 사진은 여기서 OpenAI 로 보내고 끝입니다. 저장하거나 로그에 찍지 않습니다. (store=false 로 OpenAI 에도 남기지 않음)
  public Optional<ChatTurn> turnWithImage(
      String instructions, List<CallFinishRequest.Line> lines, String imageDataUrl) {
    List<Map<String, Object>> input =
        List.of(
            Map.of(
                "role",
                "user",
                "content",
                List.of(
                    Map.of("type", "input_text", "text", inputOf(lines)),
                    Map.of("type", "input_image", "image_url", imageDataUrl, "detail", "auto"))));
    return sendTurn(imageRestClient, instructions, input);
  }

  // turn · turnWithImage 가 같이 쓰는 보내기. input 은 글 하나이거나, 사진이 섞인 메시지 목록입니다.
  private Optional<ChatTurn> sendTurn(RestClient client, String instructions, Object input) {
    if (!isEnabled()) {
      return Optional.empty();
    }
    try {
      TurnRequest request =
          new TurnRequest(
              model,
              instructions + TURN_RULES,
              input,
              false,
              new TextOption(new Format("json_schema", "coach_turn", true, TURN_SCHEMA)));
      OpenAiResponse response =
          client
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + apiKey)
              .body(request)
              .retrieve()
              .body(OpenAiResponse.class);
      return textOf(response).map(ChatAiClient::parseTurn);
    } catch (RestClientException e) {
      // 예외 메시지에는 대화가 들어 있을 수 있어서 종류만 찍습니다.
      log.warn("[AI] 채팅 답을 받지 못했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 받은 글(JSON)을 답 한 번으로 바꿉니다. JSON 이 아니거나 reply 가 없으면 받은 글을 그대로 말로 씁니다. (버튼 없이)
  static ChatTurn parseTurn(String text) {
    try {
      RawTurn raw = JSON.readValue(text, RawTurn.class);
      if (raw.reply() == null || raw.reply().isBlank()) {
        return ChatTurn.textOnly(text);
      }
      ChatReplyResponse.Action action =
          raw.action() == null
              ? null
              : new ChatReplyResponse.Action(
                  raw.action().type(),
                  raw.action().minutes(),
                  raw.action().title(),
                  raw.action().date());
      return new ChatTurn(
          raw.reply().trim(),
          raw.quickReplies() == null ? List.of() : raw.quickReplies(),
          action,
          raw.tags() == null ? List.of() : raw.tags());
    } catch (JacksonException e) {
      return ChatTurn.textOnly(text);
    }
  }

  // GPT 에게 줄 입력 글입니다. 대화를 "코치: … / 학생: …" 한 줄씩으로 적고, 다음 말을 부탁합니다.
  //
  //   []                                  →  "(학생이 방금 채팅을 열었어. [통화 순서] 1번부터 시작해.)"
  //   [ai "어제 어땠어?", me "폰 봤어"]   →  "코치: 어제 어땠어?\n학생: 폰 봤어\n\n(위 대화에 이어서 코치로서 다음 말을 해.)"
  static String inputOf(List<CallFinishRequest.Line> lines) {
    if (lines.isEmpty()) {
      return "(학생이 방금 채팅을 열었어. [통화 순서] 1번부터 시작해.)";
    }
    StringBuilder input = new StringBuilder();
    for (CallFinishRequest.Line line : lines) {
      input.append("ai".equals(line.speaker()) ? "코치: " : "학생: ").append(line.text()).append('\n');
    }
    input.append("\n(위 대화에 이어서 코치로서 다음 말을 해.)");
    return input.toString();
  }

  // 응답에서 글만 꺼냅니다. 글이 없으면 빈 값입니다. (type 이 "message" 안의 "output_text" 조각만 모읍니다)
  private static Optional<String> textOf(OpenAiResponse response) {
    if (response == null || response.output() == null) {
      return Optional.empty();
    }
    StringBuilder text = new StringBuilder();
    for (OpenAiResponse.Item item : response.output()) {
      if (!"message".equals(item.type()) || item.content() == null) {
        continue;
      }
      for (OpenAiResponse.Content content : item.content()) {
        if ("output_text".equals(content.type()) && content.text() != null) {
          text.append(content.text());
        }
      }
    }
    String result = text.toString().trim();
    return result.isEmpty() ? Optional.empty() : Optional.of(result);
  }

  // 기다리는 시간을 정한 연결 도구를 만듭니다. 정하지 않으면 GPT 가 멈췄을 때 끝없이 기다립니다.
  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts(Duration readTimeout) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  // ---- OpenAI 와 주고받는 JSON 모양 (이 파일 안에서만 씁니다) ----

  // 보내는 모양: { "model": "...", "instructions": "...", "input": "...", "store": false }
  record OpenAiRequest(String model, String instructions, String input, boolean store) {}

  // 정해진 모양으로 받을 때 보내는 모양: 위에 text.format(json_schema)을 더합니다. (AiClient 와 같은 방법)
  // input 은 글 하나(String)이거나, 사진이 섞인 메시지 목록(turnWithImage)입니다.
  record TurnRequest(
      String model, String instructions, Object input, boolean store, TextOption text) {}

  record TextOption(Format format) {}

  record Format(String type, String name, boolean strict, Map<String, Object> schema) {}

  // 받은 JSON 의 모양 (TURN_SCHEMA)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RawTurn(String reply, List<String> quickReplies, RawAction action, List<String> tags) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawAction(String type, Integer minutes, String title, String date) {}
  }

  // 받는 모양에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenAiResponse(List<Item> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String type, List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {}
  }
}
