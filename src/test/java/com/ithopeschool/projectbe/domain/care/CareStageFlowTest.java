package com.ithopeschool.projectbe.domain.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.safety.SafetyService;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 관리 단계를 판정 · 저장 · 조회해 봅니다. (이슈 #62)
// 새 사용자 두 명(에이·비이)을 가입시키고, 끝나면 탈퇴시켜서 만든 데이터를 모두 지웁니다.
// (탈퇴가 daily_care_stages 의 줄까지 지우는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class CareStageFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private DailyCareStageRepository dailyCareStageRepository;
  @Autowired private CareStageService careStageService;
  @Autowired private SafetyService safetyService;
  @Autowired private Clock clock;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;
  private LocalDate today;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "care-a-" + suffix + "@starflow.com";
    String emailB = "care-b-" + suffix + "@starflow.com";
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
    assertThat(dailyCareStageRepository.findAll())
        .noneMatch(row -> row.getUserId().equals(userIdA) || row.getUserId().equals(userIdB));
  }

  @Test
  @DisplayName("기록이 없는 첫날: 기준 단계 2(넛지)로 판정해 저장하고, 다시 부르면 같은 줄을 돌려준다")
  void firstDay() throws Exception {
    // 카드 기록 · 심리검사가 없으면 의지력 50 → 기준 단계 2, 처음 7일 상한 2, 제재 한도 처음 값 2
    String first =
        responseBody(
            send(post("/api/v1/care-stages/today"), tokenA, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.stage").value(2))
                .andExpect(jsonPath("$.name").value(CareLevelInfo.of(2).name()))
                .andExpect(jsonPath("$.rule").value("toBase"))
                .andExpect(jsonPath("$.suggestShrink").value(false)));

    String second =
        responseBody(
            send(post("/api/v1/care-stages/today"), tokenA, null).andExpect(status().isOk()));
    assertThat(second).isEqualTo(first);
    assertThat(
            dailyCareStageRepository.findByUserIdAndStageDateBetweenOrderByStageDateAsc(
                userIdA, today, today))
        .hasSize(1);

    // 지난 기록: 내 것만 보입니다. (비이는 아직 판정한 날이 없음)
    send(
            get("/api/v1/care-stages")
                .param("from", today.minusDays(7).toString())
                .param("to", today.toString()),
            tokenA,
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].stage").value(2));
    send(
            get("/api/v1/care-stages")
                .param("from", today.minusDays(7).toString())
                .param("to", today.toString()),
            tokenB,
            null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("보호 조치 중(위기 표현 뒤)이면 다른 계산보다 먼저 0단계(회복) · safety 다")
  void safetyFirst() throws Exception {
    safetyService.activate(userIdB);

    send(post("/api/v1/care-stages/today"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value(0))
        .andExpect(jsonPath("$.rule").value("safety"));
  }

  @Test
  @DisplayName("예약 작업용: 온보딩을 마친 학생만 돌고, 같은 날 두 번째 판정은 하지 않는다")
  void forJob() throws Exception {
    assertThat(careStageService.onboardedUserIds()).doesNotContain(userIdA);
    send(post("/api/v1/onboarding/completion"), tokenA, null).andExpect(status().isOk());
    assertThat(careStageService.onboardedUserIds()).contains(userIdA);

    assertThat(careStageService.decideFor(userIdA, today)).isTrue();
    assertThat(careStageService.decideFor(userIdA, today)).isFalse();
    // 작업이 먼저 판정했으면 앱은 그 줄을 그대로 받습니다.
    send(post("/api/v1/care-stages/today"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value(today.toString()));
  }

  @Test
  @DisplayName("지난 기록: to 가 from 보다 앞이거나 92일을 넘으면 400")
  void badRange() throws Exception {
    send(
            get("/api/v1/care-stages").param("from", "2026-10-18").param("to", "2026-10-17"),
            tokenA,
            null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    send(
            get("/api/v1/care-stages").param("from", "2026-07-18").param("to", "2026-10-18"),
            tokenA,
            null)
        .andExpect(status().isBadRequest());
    // 딱 92일(7/19 ~ 10/18)은 됩니다.
    send(
            get("/api/v1/care-stages").param("from", "2026-07-19").param("to", "2026-10-18"),
            tokenA,
            null)
        .andExpect(status().isOk());
  }

  // ---- 도우미 ----

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
