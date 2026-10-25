package com.ithopeschool.projectbe.domain.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 일기를 쓰고 · 보고 · 지워 봅니다. (이슈 #60)
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 일기 원문은 볼 수도 지울 수도 없는지" 를 같이 봅니다.
// 테스트 서버에는 GPT 키가 없어서 AI 분석은 하지 않습니다. (analyzed: false)
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다.
@SpringBootTest
@AutoConfigureMockMvc
class DiaryFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private DiaryRepository diaryRepository;
  @Autowired private Clock clock;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private LocalDate today;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "diary-a-" + suffix + "@starflow.com";
    String emailB = "diary-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    signup(emailB, "비이비이");
    tokenA = login(emailA);
    tokenB = login(emailB);
    today = DayRule.dateOf(clock.instant());
  }

  @AfterEach
  void withdrawBoth() throws Exception {
    send(post("/api/v1/auth/withdraw"), tokenA, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    send(post("/api/v1/auth/withdraw"), tokenB, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    assertThat(
            diaryRepository.findByUserIdAndDiaryDateBetweenOrderByWrittenAtDesc(
                userIdA, today, today))
        .isEmpty();
  }

  @Test
  @DisplayName("쓰면 201, 내 목록에만 보이고, 남이 지우려 하면 404, 내가 지우면 204")
  void writeListDelete() throws Exception {
    String id = createDiary(tokenA, "오늘은 폰을 보다가 수학을 못 했다");

    send(listOfToday(), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].content").value("오늘은 폰을 보다가 수학을 못 했다"))
        .andExpect(jsonPath("$[0].analyzed").value(false));
    // 비이에게는 에이의 일기가 보이지 않습니다.
    send(listOfToday(), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    send(delete("/api/v1/diaries/" + id), tokenB, null).andExpect(status().isNotFound());
    send(delete("/api/v1/diaries/" + id), tokenA, null).andExpect(status().isNoContent());
    send(delete("/api/v1/diaries/" + id), tokenA, null).andExpect(status().isNotFound());
    send(listOfToday(), tokenA, null).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("위기 표현이 있으면 저장하면서 도움 안내(showHelp)를 같이 돌려준다")
  void crisisShowsHelp() throws Exception {
    send(post("/api/v1/diaries"), tokenA, "{\"content\":\"요즘 다 그만두고 사라지고 싶어\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"))
        .andExpect(jsonPath("$.analyzed").value(false));
  }

  @Test
  @DisplayName("로그인하지 않으면 401")
  void requiresLogin() throws Exception {
    send(post("/api/v1/diaries"), null, "{\"content\":\"일기\"}")
        .andExpect(status().isUnauthorized());
    send(listOfToday(), null, null).andExpect(status().isUnauthorized());
  }

  private MockHttpServletRequestBuilder listOfToday() {
    return get("/api/v1/diaries").param("from", today.toString()).param("to", today.toString());
  }

  private String createDiary(String token, String content) throws Exception {
    ResultActions result =
        send(post("/api/v1/diaries"), token, "{\"content\":\"" + content + "\"}");
    result.andExpect(status().isCreated()).andExpect(jsonPath("$.showHelp").value(false));
    return JsonPath.read(responseBody(result), "$.id");
  }

  private Long signup(String email, String nickname) throws Exception {
    String body =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": true, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD, nickname);
    ResultActions result = send(post("/api/v1/auth/signup"), null, body);
    result.andExpect(status().isCreated());
    return Long.valueOf(JsonPath.read(responseBody(result), "$.id"));
  }

  private String login(String email) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    result.andExpect(status().isOk());
    return JsonPath.read(responseBody(result), "$.token");
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

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
