package com.ithopeschool.projectbe.domain.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRepository;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRule;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStatus;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStrategy;
import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.insight.InsightRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 나 사용설명서를 불러 봅니다. (이슈 #46)
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 실험 · 인사이트가 섞이지 않는지" 도 같이 봅니다.
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다. (실험과 인사이트 줄까지 지워지는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class ManualFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private ExperimentRepository experimentRepository;
  @Autowired private InsightRepository insightRepository;
  @Autowired private ManualHiddenItemRepository manualHiddenItemRepository;
  @Autowired private Clock clock;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;
  private LocalDate today;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "manual-a-" + suffix + "@starflow.com";
    String emailB = "manual-b-" + suffix + "@starflow.com";
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
    assertThat(experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userIdB)).isEmpty();
    assertThat(insightRepository.findByUserIdOrderByShownDateDescIdDesc(userIdB)).isEmpty();
    // 숨기기 기록도 탈퇴하면 같이 지워져야 합니다. (AccountService.withdraw 가 userId 칸을 보고 지움)
    assertThat(manualHiddenItemRepository.findByUserId(userIdA)).isEmpty();
    assertThat(manualHiddenItemRepository.findByUserId(userIdB)).isEmpty();
  }

  @Test
  @DisplayName("방금 가입해 아무것도 없으면 404 가 아니라 200 과 빈 배열이다")
  void emptyForNewUser() throws Exception {
    send(get("/api/v1/manual"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("효과 있는 실험과 확실한 인사이트가 내 사용설명서에만 올라오고, 남의 것은 섞이지 않는다")
  void onlyMyVerifiedFacts() throws Exception {
    // 비이만 효과 있음으로 판정된 실험과 확실한 인사이트를 하나씩 가집니다. (API 로는 14일이 지난 실험을 만들 수 없어 DB 에 바로 넣습니다)
    Long cardId = Long.valueOf(createCard(tokenB, "수학"));
    Experiment effective =
        new Experiment(
            userIdB,
            "폰 때문에 놓친다",
            null,
            ExperimentStrategy.PHONE_BAG,
            Set.of(cardId),
            today.minusDays(20),
            10,
            clock.instant());
    effective.judge(
        new ExperimentRule.Judgement(ExperimentStatus.EFFECTIVE, 0.5, 0.7), clock.instant());
    Long experimentId = experimentRepository.save(effective).getId();
    insightRepository.save(
        new Insight(
            userIdB,
            "오전에 공부한 날 순공이 40% 길었어",
            "morning-vs-night",
            false,
            today,
            List.of(today.minusDays(5), today.minusDays(1))));

    // 에이의 사용설명서는 비어 있습니다.
    send(get("/api/v1/manual"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // 비이에게는 두 개가 올라옵니다. 둘 다 오늘 확인됐으니 id 순서입니다.
    send(get("/api/v1/manual"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("experiment-" + experimentId))
        .andExpect(jsonPath("$[0].category").value("effectiveMethod"))
        .andExpect(jsonPath("$[0].title").value("시작할 때 폰을 가방에 넣기"))
        .andExpect(jsonPath("$[0].confirmedDate").value(today.toString()))
        .andExpect(jsonPath("$[0].evidence").value("14일 실험: 카드 실행률 50% → 70% (+20%p)"))
        .andExpect(jsonPath("$[1].id").value("insight-morning-vs-night"))
        .andExpect(jsonPath("$[1].category").value("rhythm"))
        .andExpect(jsonPath("$[1].title").value("오전에 공부한 날 순공이 40% 길었어"))
        .andExpect(jsonPath("$[1].confirmedDate").value(today.toString()))
        .andExpect(
            jsonPath("$[1].evidence")
                .value(
                    "근거 2일 (%s ~ %s)"
                        .formatted(monthDay(today.minusDays(5)), monthDay(today.minusDays(1)))));
  }

  @Test
  @DisplayName("가설로 보여 준 인사이트는 올라오지 않는다")
  void hypothesisInsightIsNotListed() throws Exception {
    insightRepository.save(
        new Insight(
            userIdB, "월요일 순공이 가장 짧아", "monday-drop", true, today, List.of(today.minusDays(1))));

    send(get("/api/v1/manual"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("14일이 끝났는데 아직 판정 전인 실험은 사용설명서를 열 때 판정해 남긴다")
  void judgesEndedExperimentWhenOpened() throws Exception {
    Long cardId = Long.valueOf(createCard(tokenA, "수학"));
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

    // 카드 기록이 없어서 "알 수 없음" 이 되므로 사용설명서에는 오르지 않지만, 판정은 이때 남습니다.
    send(get("/api/v1/manual"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    Experiment saved = experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userIdA).get(0);
    assertThat(saved.getStatus()).isEqualTo(ExperimentStatus.UNCLEAR);
    assertThat(saved.getJudgedAt()).isNotNull();
  }

  // ---- 숨기기 ----

  @Test
  @DisplayName("숨기면 목록에서 빠지고, 같은 항목을 또 숨겨도 204 이며 줄은 하나뿐이다")
  void hideRemovesItemAndIsIdempotent() throws Exception {
    Long experimentId = saveEffectiveExperiment(userIdB, tokenB);
    saveCertainInsight(userIdB);

    send(put("/api/v1/manual/hidden-items/experiment-" + experimentId), tokenB, null)
        .andExpect(status().isNoContent());
    send(put("/api/v1/manual/hidden-items/experiment-" + experimentId), tokenB, null)
        .andExpect(status().isNoContent());

    send(get("/api/v1/manual"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("insight-morning-vs-night"));
    assertThat(manualHiddenItemRepository.findByUserId(userIdB)).hasSize(1);
  }

  @Test
  @DisplayName("남의 항목 · 없는 항목 · 아무 글자는 404 이고, 아무것도 저장되지 않는다")
  void cannotHideWhatIsNotMine() throws Exception {
    Long othersExperimentId = saveEffectiveExperiment(userIdB, tokenB);

    // 에이의 사용설명서에는 비이의 실험이 없으니, 이름표를 알아도 숨길 수 없습니다.
    for (String itemId : List.of("experiment-" + othersExperimentId, "experiment-0", "아무-글자")) {
      send(put("/api/v1/manual/hidden-items/" + itemId), tokenA, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
    assertThat(manualHiddenItemRepository.findByUserId(userIdA)).isEmpty();

    // 비이의 사용설명서는 그대로입니다.
    send(get("/api/v1/manual"), tokenB, null).andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("이름표가 같은 인사이트라도 숨기기는 사람마다 따로다")
  void hidingIsPerUser() throws Exception {
    saveCertainInsight(userIdA);
    saveCertainInsight(userIdB);

    send(put("/api/v1/manual/hidden-items/insight-morning-vs-night"), tokenB, null)
        .andExpect(status().isNoContent());

    send(get("/api/v1/manual"), tokenB, null).andExpect(jsonPath("$.length()").value(0));
    send(get("/api/v1/manual"), tokenA, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("insight-morning-vs-night"));
  }

  // ---- 도우미 ----

  // 효과 있음으로 판정된 실험을 DB 에 바로 넣고 번호를 돌려줍니다. (API 로는 14일이 지난 실험을 만들 수 없습니다)
  private Long saveEffectiveExperiment(Long userId, String token) throws Exception {
    Long cardId = Long.valueOf(createCard(token, "수학"));
    Experiment effective =
        new Experiment(
            userId,
            "폰 때문에 놓친다",
            null,
            ExperimentStrategy.PHONE_BAG,
            Set.of(cardId),
            today.minusDays(20),
            10,
            clock.instant());
    effective.judge(
        new ExperimentRule.Judgement(ExperimentStatus.EFFECTIVE, 0.5, 0.7), clock.instant());
    return experimentRepository.save(effective).getId();
  }

  // 가설이 아닌(확실한) 인사이트를 DB 에 바로 넣습니다. 종류는 "morning-vs-night" 입니다.
  private void saveCertainInsight(Long userId) {
    insightRepository.save(
        new Insight(
            userId,
            "오전에 공부한 날 순공이 40% 길었어",
            "morning-vs-night",
            false,
            today,
            List.of(today.minusDays(5), today.minusDays(1))));
  }

  private static String monthDay(LocalDate date) {
    return "%02d/%02d".formatted(date.getMonthValue(), date.getDayOfMonth());
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
