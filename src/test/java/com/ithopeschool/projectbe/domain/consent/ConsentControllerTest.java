package com.ithopeschool.projectbe.domain.consent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.consent.dto.ConsentStatusResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양·상태 번호를 확인합니다. 진짜 로직(ConsentService)은 가짜입니다.
@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

  private static final String VALID =
      "{\"terms\":true,\"privacy\":true,\"over14\":true,\"version\":\"v1\"}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConsentService consentService;

  private ResultActions agree(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/consent").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    agree(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(consentService);
  }

  @Test
  @DisplayName("동의 상태는 200 이고, 시각은 밀리초 숫자 · 동의한 적이 없으면 null 로 나간다")
  void getStatus() throws Exception {
    given(consentService.getStatus())
        .willReturn(new ConsentStatusResponse("v1", "v0", 1792204469467L, true));

    mockMvc
        .perform(get("/api/v1/auth/consent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion").value("v1"))
        .andExpect(jsonPath("$.agreedVersion").value("v0"))
        .andExpect(jsonPath("$.agreedAt").value(1792204469467L))
        .andExpect(jsonPath("$.needsConsent").value(true))
        .andExpect(jsonPath("$.length()").value(4));

    given(consentService.getStatus()).willReturn(new ConsentStatusResponse("v1", null, null, true));
    mockMvc
        .perform(get("/api/v1/auth/consent"))
        .andExpect(jsonPath("$.agreedVersion").isEmpty())
        .andExpect(jsonPath("$.agreedAt").isEmpty());
  }

  @Test
  @DisplayName("다시 동의하면 201 과 새 동의 상태를 돌려준다")
  void agreeAgain() throws Exception {
    given(consentService.agreeAgain(any()))
        .willReturn(new ConsentStatusResponse("v1", "v1", 1792204469467L, false));

    agree(VALID)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.agreedVersion").value("v1"))
        .andExpect(jsonPath("$.needsConsent").value(false));
  }

  @Test
  @DisplayName("이용약관 · 개인정보 · 만 14세 확인이 true 가 아니면 400 이고 로직까지 가지 않는다")
  void rejectsMissingConsents() throws Exception {
    expectRejected(VALID.replace("\"terms\":true", "\"terms\":false"), "서비스 이용약관에 동의해 주세요");
    expectRejected(VALID.replace("\"privacy\":true", "\"privacy\":false"), "개인정보 수집·이용에 동의해 주세요");
    expectRejected(VALID.replace("\"over14\":true", "\"over14\":false"), "만 14세 이상만 이용할 수 있어요");
    // 아예 안 보낸 칸은 값을 읽을 수 없는 요청이라 400 과 기본 문장이 나옵니다.
    expectRejected(VALID.replace("\"over14\":true,", ""), "보낸 값을 다시 확인해 주세요");
    expectRejected("{}", "보낸 값을 다시 확인해 주세요");
  }

  @Test
  @DisplayName("동의 문구 버전이 없거나 비었거나 너무 길면 400 이다")
  void rejectsBadVersion() throws Exception {
    expectRejected(VALID.replace(",\"version\":\"v1\"", ""), "동의 문구 버전(version)을 보내 주세요");
    expectRejected(VALID.replace("\"v1\"", "\"  \""), "동의 문구 버전(version)을 보내 주세요");
    expectRejected(VALID.replace("\"v1\"", "\"" + "v".repeat(21) + "\""), "동의 문구 버전이 너무 길어요");
  }

  @Test
  @DisplayName("서비스가 던진 409(이미 동의함 · 낡은 문구)는 에러 모양으로 나간다")
  void conflictFromService() throws Exception {
    given(consentService.agreeAgain(any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "이미 이 동의 문구에 동의했어요"));

    agree(VALID)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이미 이 동의 문구에 동의했어요"));
  }
}
