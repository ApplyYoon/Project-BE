package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// 서버 전체를 진짜로 켜고(가짜 없이) "로그인한 사람만 쓸 수 있는지" 를 모든 API 에 대해 한 번에 봅니다. (이슈 #20)
//
//   1. 토큰 없이 부르면 상태 확인·가입·로그인을 뺀 모든 API 가 401 이다
//   2. A 의 토큰으로 B 의 계획을 부르면 404 다 (있는지 없는지도 알려 주지 않습니다)
//
// ⚠️ 새 API 를 만들면 이 테스트가 실패합니다. 아래 LOGIN_REQUIRED 표에 한 줄을 더해 주세요.
//    그 한 줄이 "이 API 는 토큰 없이 부르면 401 이다" 를 확인해 줍니다.
//    401 이 안 나오면 Service 에서 currentUserProvider.getCurrentUserId() 를 부르지 않은 것입니다. (CLAUDE.md 3장)
@SpringBootTest
@AutoConfigureMockMvc
class LoginRequiredFlowTest {

  private static final String PASSWORD = "study2026";

  // API 하나입니다.
  // method · pattern: Controller 에 적힌 방식과 주소 (예: PATCH /api/v1/study-plans/{id})
  // path: 실제로 부를 주소. {id} 같은 자리에 값을 넣은 것입니다.
  // body: 같이 보낼 JSON. 없으면 null. 검사(@Valid)를 통과하는 올바른 값이어야 합니다.
  //   (틀린 값을 보내면 로그인 검사보다 먼저 400 이 나와서, 401 인지 확인할 수 없습니다)
  private record Api(String method, String pattern, String path, String body) {

    private String name() {
      return method + " " + pattern;
    }
  }

  private static final String PLAN_BODY =
      "{\"date\":\"2026-10-12\",\"subjectId\":\"1\",\"content\":\"수학 문제집\",\"targetMinutes\":60}";

  private static final String EXAM_BODY =
      "{\"type\":\"mock\",\"name\":\"10월 모의고사\",\"date\":\"2026-10-20\","
          + "\"subjectScores\":[{\"subjectId\":\"1\",\"expectedScore\":85}]}";

  private static final String COACH_BODY =
      "{\"numbers\":[{\"label\":\"완주율\",\"value\":\"55%\",\"basis\":\"20번\"}],"
          + "\"maxCareLevel\":2,\"maxHarshness\":3}";

  // 로그인한 사람만 쓸 수 있는 API 들입니다. 새 API 를 만들면 여기에 한 줄을 더합니다.
  private static final List<Api> LOGIN_REQUIRED =
      List.of(
          // 내 정보 · 계정
          new Api("GET", "/api/v1/auth/me", "/api/v1/auth/me", null),
          new Api("GET", "/api/v1/auth/profile", "/api/v1/auth/profile", null),
          new Api(
              "PUT",
              "/api/v1/auth/profile",
              "/api/v1/auth/profile",
              "{\"name\":\"김티맥\",\"nickname\":\"티맥이\",\"diaryAnalysis\":false}"),
          new Api(
              "PUT",
              "/api/v1/auth/password",
              "/api/v1/auth/password",
              "{\"currentPassword\":\"study2026\",\"newPassword\":\"study2027\"}"),
          new Api("POST", "/api/v1/auth/logout", "/api/v1/auth/logout", null),
          new Api(
              "POST",
              "/api/v1/auth/withdraw",
              "/api/v1/auth/withdraw",
              "{\"password\":\"study2026\"}"),
          // 과목
          new Api("GET", "/api/v1/subjects", "/api/v1/subjects", null),
          new Api(
              "POST",
              "/api/v1/subjects",
              "/api/v1/subjects",
              "{\"name\":\"수학\",\"color\":\"red\"}"),
          new Api("PATCH", "/api/v1/subjects/{id}", "/api/v1/subjects/1", "{\"name\":\"수학\"}"),
          new Api("DELETE", "/api/v1/subjects/{id}", "/api/v1/subjects/1", null),
          // 공부 계획
          new Api("GET", "/api/v1/study-plans", "/api/v1/study-plans?date=2026-10-12", null),
          new Api("POST", "/api/v1/study-plans", "/api/v1/study-plans", PLAN_BODY),
          new Api(
              "POST",
              "/api/v1/study-plans/batch",
              "/api/v1/study-plans/batch",
              "{\"plans\":[" + PLAN_BODY + "]}"),
          new Api("PATCH", "/api/v1/study-plans/{id}", "/api/v1/study-plans/1", "{\"done\":true}"),
          new Api("DELETE", "/api/v1/study-plans/{id}", "/api/v1/study-plans/1", null),
          // 시험 점수
          new Api("GET", "/api/v1/exams", "/api/v1/exams", null),
          new Api("POST", "/api/v1/exams", "/api/v1/exams", EXAM_BODY),
          new Api(
              "PATCH",
              "/api/v1/exams/{id}",
              "/api/v1/exams/1",
              "{\"subjectScores\":[{\"subjectId\":\"1\",\"actualScore\":80}]}"),
          // AI 메시지
          new Api("GET", "/api/v1/messages", "/api/v1/messages", null),
          new Api("PATCH", "/api/v1/messages/{id}/read", "/api/v1/messages/1/read", null),
          // AI 코치 · AI 통화 (아무나 부르면 GPT 비용이 나갑니다)
          new Api(
              "POST",
              "/api/v1/coach/weekly",
              "/api/v1/coach/weekly",
              "{\"mondayKey\":\"2026-10-05\"," + COACH_BODY.substring(1)),
          new Api("POST", "/api/v1/coach/daily", "/api/v1/coach/daily", COACH_BODY),
          new Api("POST", "/api/v1/coach/monthly", "/api/v1/coach/monthly", COACH_BODY),
          new Api(
              "POST",
              "/api/v1/realtime/session",
              "/api/v1/realtime/session",
              "{\"careLevel\":1,\"maxHarshness\":3}"),
          // AI 통화 기록 (자막은 학생의 대화입니다)
          new Api("GET", "/api/v1/calls", "/api/v1/calls", null),
          new Api("GET", "/api/v1/calls/habits", "/api/v1/calls/habits", null),
          // 웹 푸시 (내 기기를 알림 받을 곳으로 등록합니다)
          new Api("GET", "/api/v1/push/public-key", "/api/v1/push/public-key", null),
          new Api(
              "POST",
              "/api/v1/push/subscriptions",
              "/api/v1/push/subscriptions",
              "{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"BNc\",\"auth\":\"tBH\"}}"),
          new Api(
              "DELETE",
              "/api/v1/push/subscriptions",
              "/api/v1/push/subscriptions",
              "{\"endpoint\":\"https://fcm.googleapis.com/x\"}"),
          // 생각 습관 테스트 (학생의 심리 기록입니다)
          new Api("GET", "/api/v1/thinking-tests/latest", "/api/v1/thinking-tests/latest", null),
          new Api("GET", "/api/v1/thinking-tests/gap", "/api/v1/thinking-tests/gap", null),
          new Api(
              "POST",
              "/api/v1/thinking-tests",
              "/api/v1/thinking-tests",
              "{\"testVersion\":2,\"scores\":["
                  + "{\"habit\":\"PLANNING_FALLACY\",\"average\":4.0},"
                  + "{\"habit\":\"FUTURE_SELF\",\"average\":4.0},"
                  + "{\"habit\":\"WHAT_THE_HELL\",\"average\":4.0},"
                  + "{\"habit\":\"EMOTIONAL_REASONING\",\"average\":4.0},"
                  + "{\"habit\":\"EXTERNAL_BLAME\",\"average\":4.0},"
                  + "{\"habit\":\"ALL_OR_NOTHING\",\"average\":4.0},"
                  + "{\"habit\":\"MORAL_LICENSING\",\"average\":4.0},"
                  + "{\"habit\":\"OVERGENERALIZING\",\"average\":4.0}]}"),
          new Api(
              "POST",
              "/api/v1/calls/{id}/finish",
              "/api/v1/calls/1/finish",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"));

  // 토큰 없이 불러도 되는 API 들입니다. 로그인하기 전에 써야 하는 것뿐입니다.
  // 구글로 시작하기 · 구글로 가입하기(이슈 #19)도 로그인하기 전에 부릅니다. 대신 구글 확인 정보가 있어야 합니다.
  private static final List<String> PUBLIC =
      List.of(
          "GET /api/v1/health",
          "POST /api/v1/auth/signup",
          "POST /api/v1/auth/login",
          "POST /api/v1/auth/google",
          "POST /api/v1/auth/google/signup");

  // 로그인 토큰이 아니라 "작업 키"(X-Job-Key)로 지키는 API 입니다. 사람이 아니라 예약 작업이 부릅니다.
  // 키가 없으면 401 인 것은 JobFlowTest 가 확인합니다.
  private static final List<String> JOB_KEY_REQUIRED = List.of("POST /api/v1/jobs/{name}");

  @Autowired private MockMvc mockMvc;

  // Spring 이 알고 있는 "주소 → Controller 메서드" 표입니다. 서버에 어떤 API 가 있는지 여기서 전부 꺼냅니다.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  // ---- 도우미 ----

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

  private ResultActions call(Api api, String token) throws Exception {
    return send(request(HttpMethod.valueOf(api.method()), api.path()), token, api.body());
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  // 새 사용자를 가입시키고 로그인해서 토큰을 돌려줍니다.
  private String signupAndLogin(String nickname) throws Exception {
    String email =
        "login-required-" + UUID.randomUUID().toString().substring(0, 8) + "@starflow.com";
    String signupBody =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false }
        }
        """
            .formatted(email, PASSWORD, nickname);
    send(post("/api/v1/auth/signup"), null, signupBody).andExpect(status().isCreated());

    ResultActions login =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    login.andExpect(status().isOk());
    return JsonPath.read(responseBody(login), "$.token");
  }

  // 탈퇴시켜서 그 사람이 만든 데이터를 모두 지웁니다.
  private void withdraw(String token) throws Exception {
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // ---- 1. 토큰 없이 부르면 401 ----

  @Test
  @DisplayName("토큰 없이 부르면 로그인이 필요한 API 는 하나도 빠짐없이 401 이다")
  void everyApiRejectsRequestWithoutToken() throws Exception {
    for (Api api : LOGIN_REQUIRED) {
      ResultActions result = call(api, null);

      assertThat(result.andReturn().getResponse().getStatus()).as(api.name()).isEqualTo(401);
      result.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
  }

  @Test
  @DisplayName("모르는 토큰으로 불러도 하나도 빠짐없이 401 이다")
  void everyApiRejectsUnknownToken() throws Exception {
    for (Api api : LOGIN_REQUIRED) {
      ResultActions result = call(api, "this-token-does-not-exist");

      assertThat(result.andReturn().getResponse().getStatus()).as(api.name()).isEqualTo(401);
      result.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
  }

  @Test
  @DisplayName("상태 확인(health)은 토큰 없이도 200 이다")
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("서버에 있는 모든 API 가 위의 표에 들어 있다 (새 API 를 만들고 표에 안 넣으면 실패)")
  void tableCoversEveryApi() {
    // 서버에 실제로 있는 API 를 "방식 주소" 글자로 모읍니다. 예: "PATCH /api/v1/study-plans/{id}"
    Set<String> actual = new TreeSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      for (String pattern : info.getPatternValues()) {
        // Swagger 화면·에러 화면처럼 /api/v1 밖에 있는 주소는 우리가 만든 API 가 아니라서 뺍니다.
        if (!pattern.startsWith("/api/v1")) {
          continue;
        }
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
          actual.add(method.name() + " " + pattern);
        }
      }
    }

    Set<String> expected = new TreeSet<>();
    for (Api api : LOGIN_REQUIRED) {
      expected.add(api.name());
    }
    expected.addAll(PUBLIC);
    expected.addAll(JOB_KEY_REQUIRED);

    assertThat(actual)
        .as("새 API 를 만들었으면 LOGIN_REQUIRED 표에 한 줄을 더해 주세요. 지운 API 는 표에서도 지워 주세요")
        .isEqualTo(expected);
  }

  // ---- 2. 남의 데이터는 404 ----

  @Test
  @DisplayName("A 의 토큰으로 B 의 계획을 바꾸거나 지우려 하면 404 이고, B 의 계획은 그대로다")
  void otherUsersPlanIsNotFound() throws Exception {
    String tokenA = signupAndLogin("에이에이");
    String tokenB = signupAndLogin("비이비이");
    try {
      // 비이가 과목과 계획을 하나씩 만듭니다.
      ResultActions subject =
          send(post("/api/v1/subjects"), tokenB, "{\"name\":\"수학\",\"color\":\"red\"}");
      subject.andExpect(status().isCreated());
      String subjectId = JsonPath.read(responseBody(subject), "$.id");

      String planBody =
          "{\"date\":\"2026-10-12\",\"subjectId\":\"%s\",\"content\":\"수학 문제집\",\"targetMinutes\":60}"
              .formatted(subjectId);
      ResultActions plan = send(post("/api/v1/study-plans"), tokenB, planBody);
      plan.andExpect(status().isCreated());
      String planId = JsonPath.read(responseBody(plan), "$.id").toString();

      // 에이가 비이의 계획 번호로 부르면 "없는 계획" 과 똑같이 404 입니다.
      send(patch("/api/v1/study-plans/" + planId), tokenA, "{\"done\":true}")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
      send(delete("/api/v1/study-plans/" + planId), tokenA, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

      // 에이의 그날 목록에는 비이의 계획이 보이지 않습니다.
      send(get("/api/v1/study-plans?date=2026-10-12"), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));

      // 비이의 계획은 지워지지도, 완료로 바뀌지도 않았습니다.
      send(get("/api/v1/study-plans?date=2026-10-12"), tokenB, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].done").value(false));
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }
}
