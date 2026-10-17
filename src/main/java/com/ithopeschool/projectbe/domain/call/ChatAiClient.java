package com.ithopeschool.projectbe.domain.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

  private final String apiKey;
  private final String model;
  private final RestClient restClient;

  // Spring 이 쓰는 생성자입니다. 설정에서 키와 모델 이름을 받습니다. 둘 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public ChatAiClient(
      @Value("${app.ai.openai-api-key:}") String apiKey, @Value("${app.ai.model:}") String model) {
    this(apiKey, model, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (ChatAiClientTest)
  ChatAiClient(String apiKey, String model, RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();
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
  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  // ---- OpenAI 와 주고받는 JSON 모양 (이 파일 안에서만 씁니다) ----

  // 보내는 모양: { "model": "...", "instructions": "...", "input": "...", "store": false }
  record OpenAiRequest(String model, String instructions, String input, boolean store) {}

  // 받는 모양에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenAiResponse(List<Item> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String type, List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {}
  }
}
