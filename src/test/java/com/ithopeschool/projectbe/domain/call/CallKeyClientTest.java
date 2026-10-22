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

import com.ithopeschool.projectbe.domain.call.CallKeyClient.CallKey;
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

// OpenAI 를 진짜로 부르지 않는 테스트입니다. 가짜 OpenAI 서버를 끼워 넣고,
// "문서에 적힌 모양으로 보내는지" 와 "여러 가지 답을 제대로 읽는지" 를 봅니다.
// ⚠️ 진짜 OpenAI 와 맞는지는 이 테스트로 알 수 없습니다. 배포 서버에서 직접 통화해 봐야 합니다.
@ExtendWith(OutputCaptureExtension.class)
class CallKeyClientTest {

  // 테스트용 가짜 키입니다. 진짜 키처럼 보이지 않게 적었습니다.
  private static final String KEY = "test-key-123";
  private static final String MODEL = "test-realtime-model";
  private static final String URL = "https://api.openai.com/v1/realtime/client_secrets";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();

  private CallKeyClient clientWith(String key, String model) {
    return new CallKeyClient(key, model, "marin", builder);
  }

  // OpenAI 가 임시 키를 돌려줄 때의 응답 모양입니다. (공식 문서의 예시에서 우리가 읽는 부분)
  private static String answerOf(String value, long expiresAtSeconds) {
    return """
        {
          "value": "%s",
          "expires_at": %d,
          "session": { "type": "realtime", "object": "realtime.session", "id": "sess_1" }
        }
        """
        .formatted(value, expiresAtSeconds);
  }

  @Test
  @DisplayName("키와 음성 모델 이름이 둘 다 있어야 켜진다")
  void enabledOnlyWithKeyAndModel() {
    assertThat(clientWith(KEY, MODEL).isEnabled()).isTrue();

    assertThat(clientWith("", MODEL).isEnabled()).isFalse();
    assertThat(clientWith(KEY, "").isEnabled()).isFalse();
    assertThat(clientWith(KEY, "   ").isEnabled()).isFalse();
    assertThat(clientWith(null, null).isEnabled()).isFalse();
  }

  @Test
  @DisplayName("꺼져 있으면 OpenAI 를 부르지 않고 빈 값을 돌려준다")
  void doesNotCallWhenDisabled() {
    openAi.expect(never(), anything());

    assertThat(clientWith(KEY, "").createKey("지침", "user-hash")).isEmpty();
    assertThat(clientWith("", MODEL).createKey("지침", "user-hash")).isEmpty();

    openAi.verify();
  }

  @Test
  @DisplayName("문서에 적힌 주소·헤더·본문으로 부른다 (모델·지침·목소리·자막용 모델·60초짜리 키)")
  void sendsTheDocumentedRequest() {
    openAi
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + KEY))
        .andExpect(header("OpenAI-Safety-Identifier", "user-hash"))
        .andExpect(jsonPath("$.expires_after.anchor").value("created_at"))
        .andExpect(jsonPath("$.expires_after.seconds").value(60))
        .andExpect(jsonPath("$.session.type").value("realtime"))
        .andExpect(jsonPath("$.session.model").value(MODEL))
        .andExpect(jsonPath("$.session.instructions").value("오늘의 대본"))
        .andExpect(jsonPath("$.session.audio.output.voice").value("marin"))
        .andExpect(
            jsonPath("$.session.audio.input.transcription.model").value("gpt-4o-mini-transcribe"))
        .andExpect(jsonPath("$.session.audio.input.transcription.language").value("ko"))
        .andRespond(withSuccess(answerOf("ek_abc123", 1791700000L), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).createKey("오늘의 대본", "user-hash"))
        // 끝나는 시각은 초로 받아서 밀리초로 바꿉니다.
        .contains(new CallKey("ek_abc123", 1791700000000L));
    openAi.verify();
  }

  @Test
  @DisplayName("주변 소리를 말로 잡지 않게 잡음 줄이기(near_field)와 말 차례 감지(semantic_vad, low)를 보낸다")
  void sendsNoiseReductionAndTurnDetection() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.session.audio.input.noise_reduction.type").value("near_field"))
        .andExpect(jsonPath("$.session.audio.input.turn_detection.type").value("semantic_vad"))
        .andExpect(jsonPath("$.session.audio.input.turn_detection.eagerness").value("low"))
        // semantic_vad 에는 없는 칸입니다. 섞어 보내면 OpenAI 가 거절할 수 있어 들어가지 않았는지 봅니다.
        .andExpect(jsonPath("$.session.audio.input.turn_detection.threshold").doesNotExist())
        .andExpect(
            jsonPath("$.session.audio.input.turn_detection.silence_duration_ms").doesNotExist())
        // 원래 보내던 자막 설정도 그대로 있어야 합니다.
        .andExpect(
            jsonPath("$.session.audio.input.transcription.model").value("gpt-4o-mini-transcribe"))
        .andRespond(withSuccess(answerOf("ek_abc123", 1791700000L), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isPresent();
    openAi.verify();
  }

  @Test
  @DisplayName("목소리를 설정하지 않으면 기본 목소리(marin)를 쓴다")
  void usesDefaultVoice() {
    openAi
        .expect(requestTo(URL))
        .andExpect(jsonPath("$.session.audio.output.voice").value("marin"))
        .andRespond(withSuccess(answerOf("ek_abc123", 1791700000L), MediaType.APPLICATION_JSON));

    assertThat(new CallKeyClient(KEY, MODEL, "  ", builder).createKey("지침", "user-hash"))
        .isPresent();
  }

  @Test
  @DisplayName("임시 키 자리에 진짜 키 모양(sk-…)이 오면 내보내지 않는다")
  void neverReturnsARealLookingKey() {
    openAi
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(answerOf("sk-not-ephemeral", 1791700000L), MediaType.APPLICATION_JSON));

    assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isEmpty();
  }

  @Test
  @DisplayName("응답에 임시 키가 없거나 JSON 이 아니면 빈 값을 돌려준다")
  void emptyWhenTheAnswerIsUseless() {
    openAi.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isEmpty();

    openAi.reset();
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"value\":\"  \",\"expires_at\":1}", MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isEmpty();

    openAi.reset();
    openAi
        .expect(requestTo(URL))
        .andRespond(withSuccess("<html>점검 중</html>", MediaType.APPLICATION_JSON));
    assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isEmpty();
  }

  @Test
  @DisplayName("OpenAI 가 거절하거나(400·401·429) 고장 났을 때(500) 에러를 던지지 않고 빈 값을 돌려준다")
  void emptyWhenOpenAiFails(CapturedOutput output) {
    for (HttpStatus status :
        new HttpStatus[] {
          HttpStatus.BAD_REQUEST,
          HttpStatus.UNAUTHORIZED,
          HttpStatus.TOO_MANY_REQUESTS,
          HttpStatus.INTERNAL_SERVER_ERROR
        }) {
      openAi.reset();
      openAi
          .expect(requestTo(URL))
          .andRespond(
              withStatus(status)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body("{\"error\":{\"message\":\"OpenAI 가 돌려준 내용\"}}"));

      assertThat(clientWith(KEY, MODEL).createKey("지침", "user-hash")).isEmpty();
    }

    assertThat(output).contains("임시 키를 받지 못했어요");
    assertThat(output).doesNotContain("OpenAI 가 돌려준 내용");
  }

  @Test
  @DisplayName("진짜 키·임시 키·지침은 로그에 남지 않는다")
  void secretsAreNotLogged(CapturedOutput output) {
    openAi
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(answerOf("ek_secret_value", 1791700000L), MediaType.APPLICATION_JSON));

    clientWith(KEY, MODEL).createKey("학생의 기록이 든 지침", "user-hash");

    assertThat(output).doesNotContain(KEY);
    assertThat(output).doesNotContain("ek_secret_value");
    assertThat(output).doesNotContain("학생의 기록이 든 지침");
  }
}
