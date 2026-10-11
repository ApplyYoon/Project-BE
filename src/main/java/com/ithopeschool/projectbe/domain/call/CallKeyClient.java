package com.ithopeschool.projectbe.domain.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
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

// OpenAI 에서 음성 통화용 "임시 키" 를 받아 오는 곳입니다. (이슈 #27)
//
// ■ 임시 키가 왜 필요한가요?
//   음성 통화는 소리가 끊기지 않게 학생의 브라우저가 OpenAI 와 직접 연결합니다.
//   그런데 브라우저에 진짜 GPT 키(sk-…)를 주면 누구나 꺼내서 우리 돈으로 GPT 를 쓸 수 있습니다.
//   그래서 서버가 진짜 키로 "이번 통화에만 쓰는 출입증" 을 만들어서, 그것만 브라우저에 줍니다.
//   출입증에는 AI 의 지침(말투·질문)이 묶여 있고, 60초 안에 통화를 시작하지 않으면 쓸 수 없습니다.
//
// ■ 배포 서버에서만 켜집니다
//   GPT 키와 음성 모델 이름의 자리는 application-prod.yml 에만 있습니다. (OPENAI_API_KEY, OPENAI_REALTIME_MODEL)
//   내 컴퓨터에서는 꺼져 있습니다. 음성은 글보다 훨씬 비싸서, 모델 이름을 일부러 넣어야만 켜지게 했습니다.
//
// ⚠️ 진짜 키, 임시 키, 지침(학생의 기록이 들어 있습니다)은 로그에 찍지 않습니다.
//
// 부르는 방법은 OpenAI 공식 문서를 따랐습니다. (2026-10-11 확인)
//   POST https://api.openai.com/v1/realtime/client_secrets
@Component
public class CallKeyClient {

  private static final Logger log = LoggerFactory.getLogger(CallKeyClient.class);

  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

  // 임시 키로 통화를 "시작" 할 수 있는 시간(초). 이미 시작한 통화는 이 뒤에도 이어집니다.
  // 짧게 잡는 이유: 임시 키 하나로 통화를 여러 번 시작하지 못하게 하려는 것입니다.
  static final int KEY_VALID_SECONDS = 60;

  // 학생의 말을 글자로 바꾸는 모델입니다. 화면의 자막(내 말)에 씁니다.
  private static final String TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  // FE 는 통화 연결 전체를 15초까지 기다립니다. 그 안에 마이크 허락·연결까지 해야 해서 짧게 잡습니다.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(6);

  private final String apiKey;
  private final String model;
  private final String voice;
  private final RestClient restClient;

  // Spring 이 쓰는 생성자입니다. 키와 음성 모델 이름 중 하나라도 비어 있으면 꺼진 상태입니다.
  @Autowired
  public CallKeyClient(
      @Value("${app.ai.openai-api-key:}") String apiKey,
      @Value("${app.ai.realtime-model:}") String model,
      @Value("${app.ai.realtime-voice:marin}") String voice) {
    this(apiKey, model, voice, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 OpenAI 를 끼워 넣을 때 쓰는 생성자입니다. (CallKeyClientTest)
  CallKeyClient(String apiKey, String model, String voice, RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.voice = voice == null || voice.isBlank() ? "marin" : voice.trim();
    this.restClient = builder.baseUrl(OPENAI_BASE_URL).build();

    // 값은 찍지 않고 켜졌는지만 알립니다.
    if (isEnabled()) {
      log.info("[AI 통화] 켜져 있어요. (키와 음성 모델 이름이 설정돼 있습니다)");
    } else {
      log.info("[AI 통화] 꺼져 있어요. 배포 서버에 OPENAI_API_KEY 와 OPENAI_REALTIME_MODEL 이 있어야 켜집니다.");
    }
  }

  // 통화를 켤 수 있는 서버인지 알려줍니다.
  public boolean isEnabled() {
    return !apiKey.isEmpty() && !model.isEmpty();
  }

  // 임시 키 하나를 받아 옵니다. 꺼져 있거나 실패하면 빈 값입니다.
  //   instructions     : AI 가 따를 지침 (CallScript 가 만든 대본)
  //   safetyIdentifier : 이 요청이 어느 사용자의 것인지 OpenAI 가 구분하는 값. 이름·이메일이 아닌, 알아볼 수 없는 글자를 넣습니다.
  public Optional<CallKey> createKey(String instructions, String safetyIdentifier) {
    if (!isEnabled()) {
      return Optional.empty();
    }

    // OpenAI 문서의 JSON 모양을 그대로 옮긴 것입니다. (Map.of 는 { "이름": 값 } 한 덩어리)
    Map<String, Object> body =
        Map.of(
            "expires_after", Map.of("anchor", "created_at", "seconds", KEY_VALID_SECONDS),
            "session",
                Map.of(
                    "type",
                    "realtime",
                    "model",
                    model,
                    "instructions",
                    instructions,
                    "audio",
                    Map.of(
                        "input",
                            Map.of(
                                "transcription",
                                Map.of("model", TRANSCRIBE_MODEL, "language", "ko")),
                        "output", Map.of("voice", voice))));

    try {
      KeyResponse response =
          restClient
              .post()
              .uri("/realtime/client_secrets")
              .header("Authorization", "Bearer " + apiKey)
              .header("OpenAI-Safety-Identifier", safetyIdentifier)
              .body(body)
              .retrieve()
              .body(KeyResponse.class);

      if (response == null || response.value() == null || response.value().isBlank()) {
        log.warn("[AI 통화] OpenAI 가 임시 키를 주지 않았어요.");
        return Optional.empty();
      }
      // 임시 키 자리에 진짜 키 모양(sk-…)이 왔다면 절대 내보내지 않습니다. (올 일은 없지만, 새면 큰일이라 한 번 더 막습니다)
      if (response.value().startsWith("sk-")) {
        log.error("[AI 통화] 임시 키 자리에 진짜 키 모양의 값이 왔어요. 내보내지 않았습니다.");
        return Optional.empty();
      }
      // OpenAI 는 끝나는 시각을 "초" 로 줍니다. 우리 API 의 시각은 밀리초라서 1000 을 곱합니다. (docs/API.md 2-2)
      return Optional.of(new CallKey(response.value(), response.expiresAt() * 1000));
    } catch (RestClientException e) {
      // 시간 초과, 연결 실패, 4xx·5xx 응답이 모두 여기로 옵니다. 예외 메시지에는 받은 내용이 들어 있을 수 있어 종류만 찍습니다.
      log.warn("[AI 통화] 임시 키를 받지 못했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  // 받아 온 임시 키와 끝나는 순간(밀리초)
  public record CallKey(String value, long expiresAtMillis) {}

  // OpenAI 응답에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다.
  //   { "value": "ek_…", "expires_at": 1756310470, "session": { … } }
  @JsonIgnoreProperties(ignoreUnknown = true)
  record KeyResponse(String value, @JsonProperty("expires_at") long expiresAt) {}
}
