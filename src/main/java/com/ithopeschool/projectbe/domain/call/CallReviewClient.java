package com.ithopeschool.projectbe.domain.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 통화가 끝난 뒤, GPT(OpenAI) 에게 자막을 주고 "정해진 칸" 으로 정리해 달라고 부탁하는 곳입니다. (이슈 #29)
// 무엇을 어떻게 정리할지(지침·칸 모양)는 CallReviewPrompt 에, 받은 값을 검사하는 일은 CallReview 에 있습니다.
//
// ■ 코치 문단(CoachAiClient)과 다른 점
//   - 글 대신 JSON 하나를 받습니다. OpenAI 에 칸 모양(JSON schema)을 같이 보내서, 그 모양으로만 답하게 합니다.
//   - 5분 통화의 자막을 읽고 정리하느라 더 오래 걸려서, 답을 20초까지 기다립니다.
//   부르는 방법은 CoachAiClient 와 같습니다. 코드를 합치면 두 기능을 한 번에 이해해야 해서 따로 두었습니다.
//
// ■ 배포 서버에서만 켜집니다
//   GPT 키와 모델 이름은 application-prod.yml 에만 자리가 있습니다. (CoachAiClient 와 같은 키·모델)
//   내 컴퓨터(local)에서는 isEnabled() 가 false 입니다.
//
// ■ 실패해도 에러를 던지지 않습니다
//   느리거나, 거절하거나, 모양이 다른 답을 주면 빈 값(Optional.empty)을 돌려줍니다. 어떻게 할지는 부르는 쪽이 정합니다.
//
// ⚠️ 키, 보낸 자막, 받은 정리는 로그에 찍지 않습니다. 미성년자의 대화입니다.
//
// 부르는 방법은 OpenAI 공식 문서의 Responses API 와 Structured Outputs 를 따랐습니다.
//   POST https://api.openai.com/v1/responses
//   { model, instructions, input, store, text: { format: { type: "json_schema", name, strict,
// schema } } }
@Component
public class CallReviewClient {

  private static final Logger log = LoggerFactory.getLogger(CallReviewClient.class);

  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

  // OpenAI 에 연결되기를 기다리는 최대 시간
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  // 답을 기다리는 최대 시간. FE 는 통화가 끝난 화면에서 이보다 길게 기다립니다.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

  // 받은 JSON 글자를 자바 값으로 바꾸는 도구
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String apiKey;
  private final String model;
  private final RestClient restClient;

  // Spring 이 쓰는 생성자입니다. 둘 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public CallReviewClient(
      @Value("${app.ai.openai-api-key:}") String apiKey, @Value("${app.ai.model:}") String model) {
    this(apiKey, model, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (CallReviewClientTest)
  CallReviewClient(String apiKey, String model, RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();
  }

  // GPT 를 부를 수 있는 서버인지 알려줍니다. 키와 모델 이름이 둘 다 있어야 true 입니다.
  public boolean isEnabled() {
    return !apiKey.isEmpty() && !model.isEmpty();
  }

  // 지침과 자막을 주고, 칸 모양(schema)대로 정리한 값을 받습니다. 꺼져 있거나 실패하면 빈 값입니다.
  public Optional<CallReview.Raw> review(
      String instructions, String input, Map<String, Object> schema) {
    if (!isEnabled()) {
      return Optional.empty();
    }

    try {
      // store=false: OpenAI 쪽에 이 요청과 답을 보관하지 말라는 뜻입니다. (미성년자의 대화라 남기지 않습니다)
      OpenAiRequest request =
          new OpenAiRequest(
              model,
              instructions,
              input,
              false,
              new TextOption(new Format("json_schema", "call_review", true, schema)));
      OpenAiResponse response =
          restClient
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + apiKey)
              .body(request)
              .retrieve()
              .body(OpenAiResponse.class);

      Optional<String> text = textOf(response);
      if (text.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(JSON.readValue(text.get(), CallReview.Raw.class));
    } catch (RestClientResponseException e) {
      // OpenAI 가 4xx·5xx 로 답했습니다. 상태 번호로 원인을 나눌 수 있어서 번호도 찍습니다. (본문은 찍지 않음)
      //   400 모델 이름 · 칸 모양(schema)이 틀림   401 키가 틀림   429 OpenAI 쪽 한도   5xx OpenAI 쪽 문제
      log.warn(
          "[AI] 통화 정리 호출에 실패했어요: {} {}", e.getClass().getSimpleName(), e.getStatusCode().value());
      return Optional.empty();
    } catch (RestClientException e) {
      // 시간 초과, 연결 실패가 여기로 옵니다. 내용이 담겨 있을 수 있어서 종류만 찍습니다.
      log.warn("[AI] 통화 정리 호출에 실패했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (JacksonException e) {
      // 약속한 JSON 모양이 아닌 답입니다. 받은 글은 찍지 않습니다.
      log.warn("[AI] 통화 정리 답의 모양이 달라요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 응답에서 글만 꺼냅니다. type 이 "message" 인 것 안의 type 이 "output_text" 인 조각만 모읍니다. (CoachAiClient 와 같음)
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

  // 보내는 모양:
  //   { "model": "...", "instructions": "...", "input": "...", "store": false,
  //     "text": { "format": { "type": "json_schema", "name": "call_review", "strict": true,
  // "schema": {…} } } }
  record OpenAiRequest(
      String model, String instructions, String input, boolean store, TextOption text) {}

  record TextOption(Format format) {}

  record Format(String type, String name, boolean strict, Map<String, Object> schema) {}

  // 받는 모양에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다. (ignoreUnknown)
  @JsonIgnoreProperties(ignoreUnknown = true)
  record OpenAiResponse(List<Item> output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String type, List<Content> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String type, String text) {}
  }
}
