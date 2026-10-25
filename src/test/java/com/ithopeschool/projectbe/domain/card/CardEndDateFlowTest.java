package com.ithopeschool.projectbe.domain.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 반복 카드의 "그만하는 날"(endDate, 2026-10-25 QA)을 따라가 봅니다.
//   월요일마다 하는 카드 만들기 → 그만하는 날을 정하면 그 뒤 월요일 목록에서 빠짐 → "" 로 지우면 다시 나옴
// 끝나면 탈퇴시켜서 만든 데이터를 모두 지웁니다.
@SpringBootTest
@AutoConfigureMockMvc
class CardEndDateFlowTest {

  private static final String PASSWORD = "study2026";
  // 2026-10-12 는 월요일입니다. 이 날부터 월요일마다 하는 카드
  private static final String MONDAY_CARD =
      "{\"title\":\"수학 문제집\",\"date\":\"2026-10-12\",\"repeatDays\":[1],\"startTime\":\"19:00\","
          + "\"durationMinutes\":60,\"checklist\":[{\"text\":\"42쪽\"}]}";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("그만하는 날을 정하면 그 날까지만 그날의 카드 목록에 나오고, \"\" 로 지우면 다시 끝 없이 나온다")
  void repeatCardStopsAfterEndDate() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String token = signupAndLogin("card-end-" + suffix + "@starflow.com", "끝날짜");
    try {
      ResultActions created = send(post("/api/v1/cards"), token, MONDAY_CARD);
      created.andExpect(status().isCreated()).andExpect(jsonPath("$.endDate").isEmpty());
      String card = JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");

      // 1) 10-19(월) 까지로 정하면 응답에 날짜가 오고, 10-19 는 나오고 10-26(월) 은 안 나옵니다.
      send(patch("/api/v1/cards/" + card), token, "{\"endDate\":\"2026-10-19\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.endDate").value("2026-10-19"));
      send(get("/api/v1/cards").param("date", "2026-10-19"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));
      send(get("/api/v1/cards").param("date", "2026-10-26"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));

      // 2) 시작한 날보다 앞인 날은 400 입니다.
      send(patch("/api/v1/cards/" + card), token, "{\"endDate\":\"2026-10-05\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("끝나는 날은 시작한 날보다 앞일 수 없어요"));

      // 3) "" 로 지우면 10-26 에도 다시 나옵니다. (2) 의 400 은 저장되지 않았습니다)
      send(patch("/api/v1/cards/" + card), token, "{\"endDate\":\"\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.endDate").isEmpty());
      send(get("/api/v1/cards").param("date", "2026-10-26"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));
    } finally {
      withdraw(token);
    }
  }

  // 새 사용자를 가입시키고 로그인해서 토큰을 돌려줍니다. (CardSubjectFlowTest 와 같음)
  private String signupAndLogin(String email, String nickname) throws Exception {
    String body =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD, nickname);
    send(post("/api/v1/auth/signup"), null, body).andExpect(status().isCreated());
    ResultActions login =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    login.andExpect(status().isOk());
    return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.token");
  }

  private void withdraw(String token) throws Exception {
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // 이 토큰을 Authorization 헤더에 붙이고(null 이면 로그인 안 한 요청), 본문이 있으면 JSON 으로 담아 부릅니다.
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
}
