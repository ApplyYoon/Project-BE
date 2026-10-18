package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 온보딩 API 를 처음부터 끝까지 불러 봅니다.
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 설문·과목이 보이거나 섞이지 않는지" 와
// "탈퇴하면 설문(딸린 테이블까지)과 처음 계획이 지워지는지" 도 같이 봅니다.
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingFlowTest {

  private static final String PASSWORD = "study2026";
  private static final String REQUIRED =
      "\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180";

  @Autowired private MockMvc mockMvc;
  @Autowired private OnboardingAnswerRepository onboardingAnswerRepository;
  @Autowired private UserInitialPlanRepository userInitialPlanRepository;
  // 딸린 테이블은 엔티티가 아니라서 SQL 로 줄 수를 셉니다.
  @Autowired private EntityManager entityManager;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "onboarding-a-" + suffix + "@starflow.com";
    String emailB = "onboarding-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    userIdB = signup(emailB, "비이비이");
    tokenA = login(emailA);
    tokenB = login(emailB);
  }

  @Test
  @DisplayName("처음 설문 → 더 알려주기(PATCH) → 생각이 바뀜(PATCH) → 처음 자기 인식은 남는다 · 처음 계획은 한 번만")
  void wholeOnboarding() throws Exception {
    try {
      // 설문 전에는 404
      send(get("/api/v1/onboarding"), tokenA, null).andExpect(status().isNotFound());

      // 1. 처음 실행: 꼭 받는 네 개만
      send(put("/api/v1/onboarding"), tokenA, "{" + REQUIRED + "}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.grade").value("고2"))
          .andExpect(jsonPath("$.chronotype").isEmpty())
          .andExpect(jsonPath("$.firstSelfAwareness.dailyAvailableMinutes").value(180));

      // 2. 처음 계획 세우기, 두 번째는 409 (덮어쓰지 않음)
      send(
              post("/api/v1/onboarding/initial-plan"),
              tokenA,
              "{\"dailyMinutes\":180,\"daysPerWeek\":5}")
          .andExpect(status().isCreated());
      send(
              post("/api/v1/onboarding/initial-plan"),
              tokenA,
              "{\"dailyMinutes\":60,\"daysPerWeek\":3}")
          .andExpect(status().isConflict());

      // 3. 내 과목을 하나 만들고, "내 정보 더 알려주기" 에서 자기 인식과 과목 수준을 처음 답함
      String subjectId = createSubject(tokenA, "수학");
      send(
              patch("/api/v1/onboarding"),
              tokenA,
              "{\"chronotype\":\"morning\",\"maxFocusMinutes\":50,\"biggestDistraction\":\"폰·SNS\","
                  + "\"pastFailureCauses\":[\"weekend\"],"
                  + "\"subjectLevels\":[{\"subjectId\":\""
                  + subjectId
                  + "\",\"grade\":3,\"confidence\":2}]}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.subjectLevels[0].subjectId").value(subjectId));

      // 4. 생각이 바뀌어서 저녁형으로 고침 → 지금은 저녁형, 처음 자기 인식은 아침형
      send(patch("/api/v1/onboarding"), tokenA, "{\"chronotype\":\"night\"}")
          .andExpect(status().isOk());
      ResultActions mine = send(get("/api/v1/onboarding"), tokenA, null);
      mine.andExpect(status().isOk())
          .andExpect(jsonPath("$.chronotype").value("night"))
          .andExpect(jsonPath("$.pastFailureCauses[0]").value("weekend"))
          .andExpect(jsonPath("$.subjectLevels.length()").value(1))
          .andExpect(jsonPath("$.firstSelfAwareness.chronotype").value("morning"))
          .andExpect(jsonPath("$.firstSelfAwareness.maxFocusMinutes").value(50));
      // 시각은 밀리초 숫자로 나갑니다. (docs/API.md 2-2)
      assertThat((Object) JsonPath.read(responseBody(mine), "$.answeredAt"))
          .isInstanceOf(Long.class);
      // 고칠 때마다 새 줄이 쌓였습니다. (PUT 1 + PATCH 2)
      assertThat(onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(userIdA))
          .hasSize(3);

      // 5. 다시 세우기는 원래 줄을 고칩니다. (한 사람에 한 줄 그대로)
      send(
              put("/api/v1/onboarding/initial-plan"),
              tokenA,
              "{\"dailyMinutes\":120,\"daysPerWeek\":6}")
          .andExpect(status().isOk());
      send(get("/api/v1/onboarding/initial-plan"), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dailyMinutes").value(120))
          .andExpect(jsonPath("$.daysPerWeek").value(6));
      assertThat(
              userInitialPlanRepository.findAll().stream()
                  .filter(p -> p.getUserId().equals(userIdA)))
          .hasSize(1);
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("비이에게는 에이의 설문·처음 계획이 보이지 않고, 에이의 과목 번호로 수준을 저장할 수 없다")
  void otherUsersDataIsHidden() throws Exception {
    try {
      String subjectOfA = createSubject(tokenA, "국어");
      send(put("/api/v1/onboarding"), tokenA, "{" + REQUIRED + "}").andExpect(status().isOk());
      send(
              post("/api/v1/onboarding/initial-plan"),
              tokenA,
              "{\"dailyMinutes\":180,\"daysPerWeek\":5}")
          .andExpect(status().isCreated());

      // 비이는 아직 설문·처음 계획이 없어서 404 (에이 것이 보이면 안 됨)
      send(get("/api/v1/onboarding"), tokenB, null).andExpect(status().isNotFound());
      send(get("/api/v1/onboarding/initial-plan"), tokenB, null).andExpect(status().isNotFound());
      // 비이가 에이의 과목 번호로 수준을 저장하면 "없는 과목" 과 똑같이 404 이고, 아무것도 저장되지 않음
      send(
              put("/api/v1/onboarding"),
              tokenB,
              "{"
                  + REQUIRED
                  + ",\"subjectLevels\":[{\"subjectId\":\""
                  + subjectOfA
                  + "\",\"grade\":1,\"confidence\":5}]}")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
      assertThat(onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(userIdB))
          .isEmpty();
      // 비이가 처음 계획을 세워도 에이의 처음 계획은 그대로
      send(
              post("/api/v1/onboarding/initial-plan"),
              tokenB,
              "{\"dailyMinutes\":60,\"daysPerWeek\":3}")
          .andExpect(status().isCreated());
      send(get("/api/v1/onboarding/initial-plan"), tokenA, null)
          .andExpect(jsonPath("$.dailyMinutes").value(180));
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("탈퇴하면 설문 답 · 딸린 두 테이블 · 처음 계획이 모두 지워지고, 남의 것은 그대로다")
  void withdrawDeletesEverything() throws Exception {
    String subjectOfA = createSubject(tokenA, "영어");
    String subjectOfB = createSubject(tokenB, "영어");
    for (String[] user : new String[][] {{tokenA, subjectOfA}, {tokenB, subjectOfB}}) {
      send(
              put("/api/v1/onboarding"),
              user[0],
              "{"
                  + REQUIRED
                  + ",\"pastFailureCauses\":[\"weekend\",\"phone\"],"
                  + "\"subjectLevels\":[{\"subjectId\":\""
                  + user[1]
                  + "\",\"score\":70,\"confidence\":3}]}")
          .andExpect(status().isOk());
      send(
              post("/api/v1/onboarding/initial-plan"),
              user[0],
              "{\"dailyMinutes\":180,\"daysPerWeek\":5}")
          .andExpect(status().isCreated());
    }
    List<Long> answerIdsOfA = answerIdsOf(userIdA);
    List<Long> answerIdsOfB = answerIdsOf(userIdB);
    assertThat(countChildRows("onboarding_answer_failure_causes", answerIdsOfA)).isEqualTo(2);

    withdraw(tokenA);
    try {
      assertThat(answerIdsOf(userIdA)).isEmpty();
      assertThat(userInitialPlanRepository.findByUserId(userIdA)).isEmpty();
      // 딸린 테이블에는 userId 가 없어서, 지운 설문 번호로 남은 줄이 없는지 봅니다.
      assertThat(countChildRows("onboarding_answer_failure_causes", answerIdsOfA)).isZero();
      assertThat(countChildRows("onboarding_answer_subject_levels", answerIdsOfA)).isZero();
      // 비이 것은 그대로입니다.
      assertThat(countChildRows("onboarding_answer_failure_causes", answerIdsOfB)).isEqualTo(2);
      assertThat(countChildRows("onboarding_answer_subject_levels", answerIdsOfB)).isEqualTo(1);
      assertThat(userInitialPlanRepository.findByUserId(userIdB)).isPresent();
    } finally {
      withdraw(tokenB);
    }
  }

  // ---- 도우미 ----

  private List<Long> answerIdsOf(Long userId) {
    return onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(userId).stream()
        .map(OnboardingAnswer::getId)
        .toList();
  }

  // 설문 번호들(answerIds)에 딸린 줄이 몇 개인지 셉니다. 표 이름은 우리가 정한 글자이고, 번호는 파라미터로 넘깁니다.
  private long countChildRows(String table, List<Long> answerIds) {
    if (answerIds.isEmpty()) {
      return 0;
    }
    return ((Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from " + table + " where onboarding_answer_id in (:ids)")
                .setParameter("ids", answerIds)
                .getSingleResult())
        .longValue();
  }

  private String createSubject(String token, String name) throws Exception {
    ResultActions result =
        send(post("/api/v1/subjects"), token, "{\"name\":\"" + name + "\",\"color\":\"red\"}");
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

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }
}
