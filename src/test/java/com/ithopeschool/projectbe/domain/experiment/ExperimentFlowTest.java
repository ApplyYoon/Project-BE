package com.ithopeschool.projectbe.domain.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 자기 실험을 시작 · 조회 · 판정해 봅니다. (이슈 #52 · #53 · #54)
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 카드로 실험을 시작할 수 없는지" 도 같이 봅니다.
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다. (실험과 붙인 카드 줄까지 지워지는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class ExperimentFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private ExperimentRepository experimentRepository;
  @Autowired private Clock clock;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;
  private LocalDate today;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "exp-a-" + suffix + "@starflow.com";
    String emailB = "exp-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    userIdB = signup(emailB, "비이비이");
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
    assertThat(experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userIdA)).isEmpty();
    assertThat(experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userIdB)).isEmpty();
  }

  @Test
  @DisplayName("내 카드에 실험을 붙여 시작하면 201 이고, 목록에 진행 중 · 1일째로 보인다")
  void startAndList() throws Exception {
    String cardId = createCard(tokenA, "수학 문제집");

    send(post("/api/v1/experiments"), tokenA, experimentJson("phone-bag", "phone", cardId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.strategyId").value("phone-bag"))
        .andExpect(jsonPath("$.rule").value("시작할 때 폰을 가방에 넣기"))
        .andExpect(jsonPath("$.cause").value("phone"))
        .andExpect(jsonPath("$.cardIds[0]").value(cardId))
        .andExpect(jsonPath("$.startDate").value(today.toString()))
        .andExpect(jsonPath("$.endDate").value(today.plusDays(13).toString()))
        .andExpect(jsonPath("$.day").value(1))
        .andExpect(jsonPath("$.minGainPp").value(10))
        .andExpect(jsonPath("$.status").value("running"))
        .andExpect(jsonPath("$.baselineRate").isEmpty())
        .andExpect(jsonPath("$.fulfillment").isEmpty());

    send(get("/api/v1/experiments"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("running"));
    // 비이에게는 보이지 않습니다.
    send(get("/api/v1/experiments"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("남의 카드는 404, 진행 중 실험이 붙은 카드는 409, 진행 중이 2개면 세 번째는 409")
  void rules() throws Exception {
    String cardA1 = createCard(tokenA, "수학");
    String cardA2 = createCard(tokenA, "영어");
    String cardA3 = createCard(tokenA, "국어");

    send(post("/api/v1/experiments"), tokenB, experimentJson("phone-bag", null, cardA1))
        .andExpect(status().isNotFound());

    send(post("/api/v1/experiments"), tokenA, experimentJson("phone-bag", null, cardA1))
        .andExpect(status().isCreated());
    send(post("/api/v1/experiments"), tokenA, experimentJson("plan-shorter", null, cardA1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("그 카드에는 이미 진행 중인 실험이 붙어 있어요"));
    send(post("/api/v1/experiments"), tokenA, experimentJson("plan-shorter", null, cardA2))
        .andExpect(status().isCreated());
    send(post("/api/v1/experiments"), tokenA, experimentJson("start-desk", null, cardA3))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("실험은 한 번에 2개까지 할 수 있어요"));
  }

  @Test
  @DisplayName("없는 전략 · 원인, 내일이나 14일 전 시작 날짜는 400")
  void badValues() throws Exception {
    String cardId = createCard(tokenA, "수학");

    send(post("/api/v1/experiments"), tokenA, experimentJson("phone-easy-1", null, cardId))
        .andExpect(status().isBadRequest());
    send(post("/api/v1/experiments"), tokenA, experimentJson("phone-bag", "lazy", cardId))
        .andExpect(status().isBadRequest());
    for (LocalDate bad : new LocalDate[] {today.plusDays(1), today.minusDays(14)}) {
      String body =
          "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"%s\"],\"startDate\":\"%s\"}"
              .formatted(cardId, bad);
      send(post("/api/v1/experiments"), tokenA, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("시작 날짜는 13일 전부터 오늘까지만 고를 수 있어요"));
    }
    // 13일 전 시작은 됩니다. (기기에서 먼저 시작한 실험을 올리는 경우)
    String body =
        "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"%s\"],\"startDate\":\"%s\"}"
            .formatted(cardId, today.minusDays(13));
    send(post("/api/v1/experiments"), tokenA, body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.day").value(14));
  }

  @Test
  @DisplayName("14일이 끝난 실험은 목록을 볼 때 판정해 남긴다 (카드 기록이 없으면 알 수 없음)")
  void judgesEnded() throws Exception {
    Long cardId = Long.valueOf(createCard(tokenA, "수학"));
    // API 로는 14일 전 시작을 만들 수 없어서, 이미 끝난 실험을 DB 에 바로 넣습니다.
    experimentRepository.save(
        new Experiment(
            userIdA,
            "폰 때문에 놓친다",
            null,
            ExperimentStrategy.PHONE_ROOM,
            Set.of(cardId),
            today.minusDays(20),
            10,
            clock.instant()));

    send(get("/api/v1/experiments"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("unclear"))
        .andExpect(jsonPath("$[0].day").value(15));
    Experiment saved = experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userIdA).get(0);
    assertThat(saved.getStatus()).isEqualTo(ExperimentStatus.UNCLEAR);
    assertThat(saved.getJudgedAt()).isNotNull();
  }

  // ---- 도우미 ----

  private static String experimentJson(String strategyId, String cause, String cardId) {
    String causePart = cause == null ? "" : ",\"cause\":\"" + cause + "\"";
    return "{\"hypothesis\":\"폰·SNS 때문에 놓치는 날이 많다\",\"strategyId\":\"%s\",\"cardIds\":[\"%s\"]%s}"
        .formatted(strategyId, cardId, causePart);
  }

  // 오늘 카드를 하나 만들고 번호(글자)를 돌려줍니다. (체크리스트는 하나 이상이어야 합니다)
  private String createCard(String token, String title) throws Exception {
    String body =
        "{\"title\":\"%s\",\"date\":\"%s\",\"durationMinutes\":60,\"checklist\":[{\"text\":\"1쪽\"}]}"
            .formatted(title, today);
    ResultActions result = send(post("/api/v1/cards"), token, body);
    result.andExpect(status().isCreated());
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
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
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
