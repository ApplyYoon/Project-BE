package com.ithopeschool.projectbe.domain.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.push.dto.PushPublicKeyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다.
@WebMvcTest(PushController.class)
class PushControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PushSubscriptionService pushSubscriptionService;

  // 브라우저의 PushSubscription.toJSON() 모양 (쓰지 않는 expirationTime 도 같이 옵니다)
  private static final String VALID =
      """
      { "endpoint": "https://fcm.googleapis.com/fcm/send/abc",
        "expirationTime": null,
        "keys": { "p256dh": "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM", "auth": "tBHItJI5svbpez7KI4CCXg" } }
      """;

  private ResultActions subscribe(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/push/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    subscribe(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(pushSubscriptionService);
  }

  @Test
  @DisplayName("공개 키는 200 과 { publicKey } 로 답한다")
  void publicKey() throws Exception {
    given(pushSubscriptionService.getPublicKey()).willReturn(new PushPublicKeyResponse("BEl62"));

    mockMvc
        .perform(get("/api/v1/push/public-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicKey").value("BEl62"));
  }

  @Test
  @DisplayName("구독 켜기·끄기는 204 로 답한다 (쓰지 않는 칸 expirationTime 은 무시)")
  void subscribeAndUnsubscribe() throws Exception {
    subscribe(VALID).andExpect(status().isNoContent());
    verify(pushSubscriptionService).subscribe(any());

    mockMvc
        .perform(
            delete("/api/v1/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("주소가 https 가 아니거나, 열쇠가 없거나 모양이 틀리면 400 이고 서비스를 부르지 않는다")
  void rejectsBadSubscriptions() throws Exception {
    expectRejected(
        VALID.replace("https://fcm", "http://fcm"), "구독 주소(endpoint)는 https:// 로 시작해야 해요");
    expectRejected("{\"endpoint\":\"https://fcm.googleapis.com/x\"}", "구독 열쇠(keys)를 보내 주세요");
    expectRejected(
        VALID.replace("tBHItJI5svbpez7KI4CCXg", "tBH<script>"), "keys.auth 의 모양이 올바르지 않아요");
    expectRejected(
        VALID.replace("\"auth\": \"tBHItJI5svbpez7KI4CCXg\"", "\"auth\": \"\""),
        "keys.auth 를 보내 주세요");
  }
}
