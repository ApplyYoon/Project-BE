package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 서버 전체를 진짜로 켜고(가짜 없이) AI 통화 시작을 불러 봅니다. 내 컴퓨터와 같은 local 설정입니다.
//
// 환경변수 OPENAI_API_KEY 와 OPENAI_REALTIME_MODEL 을 일부러 넣어 둔 채로 켭니다.
// local 설정에는 키와 음성 모델의 자리가 없어서, 넣어 두어도 통화가 켜지지 않아야 합니다. (AI 는 배포 서버에서만)
@SpringBootTest(
    properties = {"OPENAI_API_KEY=test-key-123", "OPENAI_REALTIME_MODEL=test-realtime-model"})
@AutoConfigureMockMvc
class CallFlowTest {

  private static final String BODY =
      "{\"careLevel\":1,\"maxHarshness\":3,\"card\":{\"yesterdayStudyMinutes\":190,\"yesterdayPlanMinutes\":240}}";

  @Autowired private MockMvc mockMvc;
  @Autowired private CallKeyClient callKeyClient;

  @Test
  @DisplayName("내 컴퓨터(local)에서는 환경변수에 키와 모델을 넣어도 통화가 꺼져 있다")
  void callIsOffOnLocalEvenWithTheKey() {
    assertThat(callKeyClient.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("로그인하지 않고 부르면 401")
  void requiresLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/realtime/session").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("로그인하고 부르면, 통화가 꺼져 있어서 503 과 안내가 온다")
  void unavailableOnLocal() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/realtime/session")
                // 내 컴퓨터 전용 테스트 토큰입니다. (application-local.yml)
                .header("Authorization", "Bearer local-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.error.message").value("AI 통화가 아직 켜져 있지 않아요"));
  }

  @Test
  @DisplayName("로그인했어도 틀린 모양으로 보내면 400")
  void rejectsBadBody() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/realtime/session")
                .header("Authorization", "Bearer local-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("190", "99999")))
        .andExpect(status().isBadRequest());
  }
}
