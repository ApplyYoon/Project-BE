package com.ithopeschool.projectbe.domain.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 켜고 "통화를 마무리하면 그날의 체크인이 남는지" 를 체크인 API 로 확인합니다. (이슈 #29 · #24)
// GPT 정리(CallReviewClient)만 가짜로 바꿉니다. 내 컴퓨터에서는 AI 가 꺼져 있어서 마무리를 할 수 없기 때문입니다.
// 통화 시작은 OpenAI 를 불러야 해서, 통화 기록은 DB 에 바로 넣습니다.
@SpringBootTest
@AutoConfigureMockMvc
class CallCheckInFlowTest {

  private static final String PASSWORD = "study2026";
  // 통화한 날 (지난 날짜라 체크인 날짜로 써도 됩니다)
  private static final LocalDate CALL_DATE = LocalDate.of(2026, 10, 14);

  private static final String TRANSCRIPT =
      "{\"lines\":[{\"speaker\":\"ai\",\"text\":\"어제 어땠어?\"},"
          + "{\"speaker\":\"me\",\"text\":\"폰 보다가 못 했어. 오늘은 한 시간 반 할게\"}]}";

  @Autowired private MockMvc mockMvc;
  @Autowired private CallRecordRepository callRecordRepository;

  @MockitoBean private CallReviewClient callReviewClient;

  private String token;
  private Long userId;

  @BeforeEach
  void signUp() throws Exception {
    String email = "call-checkin-" + UUID.randomUUID().toString().substring(0, 8) + "@starflow.com";
    String body =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "통화체크인",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false }
        }
        """
            .formatted(email, PASSWORD);
    ResultActions signup = send(post("/api/v1/auth/signup"), null, body);
    signup.andExpect(status().isCreated());
    userId = Long.valueOf(JsonPath.read(responseBody(signup), "$.id"));
    ResultActions login =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    token = JsonPath.read(responseBody(login), "$.token");

    // GPT 정리: 기분 3, 폰 때문에 못 채움, 오늘 90분, 약속 하나
    given(callReviewClient.isEnabled()).willReturn(true);
    given(callReviewClient.review(anyString(), anyString(), any()))
        .willReturn(
            Optional.of(
                new CallReview.Raw(
                    false,
                    "폰을 보다가 못 했다고 함.",
                    null,
                    null,
                    null,
                    "phone",
                    "학원에서 오면",
                    "폰을 서랍에 넣기",
                    null,
                    3,
                    90)));
  }

  @AfterEach
  void withdraw() throws Exception {
    // 탈퇴하면 통화 기록과 체크인도 함께 지워집니다.
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("그날 체크인이 없으면, 통화를 마무리할 때 통화 값으로 체크인(source: call)이 생긴다")
  void finishingACallCreatesTheCheckIn() throws Exception {
    Long callId = startedCall();

    send(post("/api/v1/calls/" + callId + "/finish"), token, TRANSCRIPT).andExpect(status().isOk());

    send(get("/api/v1/checkins?from=2026-10-14&to=2026-10-14"), token, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-10-14"))
        .andExpect(jsonPath("$[0].source").value("call"))
        .andExpect(jsonPath("$[0].mood").value(3))
        .andExpect(jsonPath("$[0].missReason").value("phone"))
        .andExpect(jsonPath("$[0].expectedMinutes").value(90))
        .andExpect(jsonPath("$[0].promiseIf").value("학원에서 오면"))
        .andExpect(jsonPath("$[0].promiseThen").value("폰을 서랍에 넣기"));
  }

  @Test
  @DisplayName("그날 직접 한 체크인이 있으면, 학생이 고른 값은 그대로 두고 빈 칸만 통화 값으로 채운다")
  void finishingACallFillsOnlyTheBlanks() throws Exception {
    send(put("/api/v1/checkins/2026-10-14"), token, "{\"source\":\"manual\",\"mood\":5}")
        .andExpect(status().isOk());
    Long callId = startedCall();

    send(post("/api/v1/calls/" + callId + "/finish"), token, TRANSCRIPT).andExpect(status().isOk());

    send(get("/api/v1/checkins?from=2026-10-14&to=2026-10-14"), token, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].source").value("manual"))
        .andExpect(jsonPath("$[0].mood").value(5))
        .andExpect(jsonPath("$[0].missReason").value("phone"))
        .andExpect(jsonPath("$[0].expectedMinutes").value(90));
  }

  // ---- 도우미 ----

  // 마무리 전의 통화 기록 하나를 DB 에 넣고 번호를 돌려줍니다.
  private Long startedCall() {
    return callRecordRepository
        .save(new CallRecord(userId, CALL_DATE, Instant.parse("2026-10-14T11:00:00Z"), null))
        .getId();
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body)
      throws Exception {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
