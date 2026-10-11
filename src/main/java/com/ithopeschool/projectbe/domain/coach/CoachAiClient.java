package com.ithopeschool.projectbe.domain.coach;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

// GPT(OpenAI) 에게 글을 써 달라고 부탁하는 곳입니다. GPT 를 부르는 코드는 이 파일에만 있습니다.
//
// ■ 배포 서버에서만 켜집니다
//   GPT 키와 모델 이름은 application-prod.yml 에만 자리가 있습니다. (환경변수 OPENAI_API_KEY, OPENAI_MODEL)
//   모델은 기본값(gpt-6-luna)이 있어서, 배포 서버에 키만 넣으면 켜집니다.
//   내 컴퓨터(local)에는 자리가 없어서, 환경변수를 넣어도 GPT 를 부르지 않습니다. (isEnabled() 가 false)
//   키가 여러 컴퓨터에 퍼지면 새어 나가기 쉽고, 부를 때마다 돈이 나가기 때문입니다.
//
// ■ 실패해도 에러를 던지지 않습니다
//   GPT 가 느리거나, 거절하거나, 이상한 답을 줘도 빈 값(Optional.empty)을 돌려줍니다.
//   AI 글은 "있으면 더 좋은 것" 이라서, 없으면 FE 가 기본 문장을 보여주면 됩니다.
//
// ⚠️ 키, 보낸 글, 받은 글은 로그에 찍지 않습니다.
//
// 부르는 방법은 OpenAI 공식 문서의 Responses API 를 따랐습니다. (2026-10-11 확인)
//   POST https://api.openai.com/v1/responses   { model, instructions, input, store }
@Component
public class CoachAiClient {

  private static final Logger log = LoggerFactory.getLogger(CoachAiClient.class);

  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

  // OpenAI 에 연결되기를 기다리는 최대 시간
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  // 답을 기다리는 최대 시간. FE 는 8초까지 기다리므로(weeklyCoach.ts), 그보다 먼저 포기해야 FE 가 답을 받습니다.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(6);

  private final String apiKey;
  private final String model;
  private final RestClient restClient;

  // Spring 이 쓰는 생성자입니다. 설정에서 키와 모델 이름을 받습니다. 둘 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public CoachAiClient(
      @Value("${app.ai.openai-api-key:}") String apiKey, @Value("${app.ai.model:}") String model) {
    this(apiKey, model, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (CoachAiClientTest)
  CoachAiClient(String apiKey, String model, RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();

    // 값은 찍지 않고 켜졌는지만 알립니다.
    if (isEnabled()) {
      log.info("[AI] GPT 를 쓸 수 있어요. (키와 모델 이름이 설정돼 있습니다)");
    } else {
      log.info("[AI] GPT 가 꺼져 있어요. 배포 서버에 OPENAI_API_KEY 가 있어야 켜집니다.");
    }
  }

  // GPT 를 부를 수 있는 서버인지 알려줍니다. 키와 모델 이름이 둘 다 있어야 true 입니다.
  public boolean isEnabled() {
    return !apiKey.isEmpty() && !model.isEmpty();
  }

  // GPT 에게 지침과 입력을 주고, 써 준 글을 받습니다. 꺼져 있거나 실패하면 빈 값입니다.
  public Optional<String> ask(String instructions, String input) {
    if (!isEnabled()) {
      return Optional.empty();
    }

    try {
      // store=false: OpenAI 쪽에 이 요청과 답을 보관하지 말라는 뜻입니다. (미성년자의 기록이라 남기지 않습니다)
      OpenAiRequest request = new OpenAiRequest(model, instructions, input, false);
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
      // 시간 초과, 연결 실패, 4xx·5xx 응답이 모두 여기로 옵니다.
      // 예외 메시지에는 OpenAI 가 돌려준 내용이 들어 있을 수 있어서, 종류만 찍습니다.
      log.warn("[AI] GPT 호출에 실패했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 응답에서 글만 꺼냅니다. 글이 없으면 빈 값입니다.
  // 응답의 output 에는 글 말고 다른 것(생각한 과정 등)도 섞여 있어서, type 이 "message" 인 것 안의
  // type 이 "output_text" 인 조각만 모읍니다.
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

  // 받는 모양에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다. (ignoreUnknown)
  //   { "output": [ { "type": "message", "content": [ { "type": "output_text", "text": "…" } ] } ]
  // }
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenAiResponse(List<Item> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String type, List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {}
  }
}
