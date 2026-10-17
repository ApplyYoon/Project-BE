package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
import org.springframework.transaction.support.TransactionTemplate;

// 서버 전체를 진짜로 켜고(가짜 없이) 과목 API 를 처음부터 끝까지 불러 봅니다.
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 과목이 보이거나 바뀌지 않는지" 도 같이 봅니다.
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다. (탈퇴가 과목까지 지우는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class SubjectFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private SubjectRepository subjectRepository;
  // 계획·공부 기록을 만드는 API 는 아직 없어서, DB 에 직접 넣어 "이미 쓰고 있는 과목" 을 만듭니다.
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "subject-a-" + suffix + "@starflow.com";
    String emailB = "subject-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    userIdB = signup(emailB, "비이비이");
    tokenA = login(emailA);
    tokenB = login(emailB);
  }

  @AfterEach
  void withdrawBoth() throws Exception {
    send(post("/api/v1/auth/withdraw"), tokenA, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    send(post("/api/v1/auth/withdraw"), tokenB, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
    // 탈퇴하면 두 사람의 과목도 함께 지워집니다.
    assertThat(subjectRepository.findByUserIdOrderById(userIdA)).isEmpty();
    assertThat(subjectRepository.findByUserIdOrderById(userIdB)).isEmpty();
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

  // 과목을 추가하고 201 을 확인한 뒤, 만들어진 과목의 번호(글자)를 돌려줍니다.
  private String addSubject(String token, String name, String color) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/subjects"),
            token,
            "{\"name\":\"%s\",\"color\":\"%s\"}".formatted(name, color));
    result.andExpect(status().isCreated());
    return JsonPath.read(responseBody(result), "$.id");
  }

  private void insertPlanOf(Long userId, String subjectId) {
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager.persist(
                new StudyPlan(
                    userId, Long.valueOf(subjectId), LocalDate.of(2026, 10, 12), "문제집 풀기", 60)));
  }

  private void insertSessionOf(Long userId, String subjectId) {
    Instant start = Instant.parse("2026-10-12T01:00:00Z");
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager.persist(
                new StudySession(
                    userId, Long.valueOf(subjectId), start, start.plusSeconds(60), 0)));
  }

  // ---- 시나리오 ----

  @Test
  @DisplayName("추가 → 목록 → 이름 겹침 · 틀린 값 거절 → 바꾸기 → 삭제 → 같은 이름으로 다시 추가")
  void addListUpdateDelete() throws Exception {
    // 처음에는 빈 배열입니다. (404 가 아닙니다)
    send(get("/api/v1/subjects"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));

    // 추가: 201 과 {id, name, color} 세 개. id 는 글자, color 는 소문자
    ResultActions created =
        send(post("/api/v1/subjects"), tokenA, "{\"name\":\"수학\",\"color\":\"blue\"}");
    created
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.name").value("수학"))
        .andExpect(jsonPath("$.color").value("blue"))
        .andExpect(jsonPath("$.length()").value(3));
    String mathId = JsonPath.read(responseBody(created), "$.id");
    String englishId = addSubject(tokenA, "영어", "purple");

    // 목록: 만든 순서대로
    send(get("/api/v1/subjects"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(mathId))
        .andExpect(jsonPath("$[0].name").value("수학"))
        .andExpect(jsonPath("$[1].id").value(englishId))
        .andExpect(jsonPath("$[1].color").value("purple"));

    // 같은 이름은 두 번 만들 수 없습니다. (색이 달라도, 앞뒤 공백이 붙어도)
    for (String body :
        new String[] {
          "{\"name\":\"수학\",\"color\":\"blue\"}",
          "{\"name\":\"수학\",\"color\":\"red\"}",
          "{\"name\":\"  수학  \",\"color\":\"green\"}"
        }) {
      send(post("/api/v1/subjects"), tokenA, body)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("CONFLICT"))
          .andExpect(jsonPath("$.error.message").value("이미 같은 이름의 과목이 있어요"));
    }
    send(get("/api/v1/subjects"), tokenA, null).andExpect(jsonPath("$.length()").value(2));

    // 틀린 값은 400 (서버가 멈추거나 500 이 나오면 안 됩니다)
    String colorMessage = "색은 red, orange, green, blue, purple 중에서 골라 주세요";
    expect400(post("/api/v1/subjects"), "{\"name\":\"과학\",\"color\":\"pink\"}", colorMessage);
    expect400(post("/api/v1/subjects"), "{\"name\":\"과학\",\"color\":\"BLUE\"}", colorMessage);
    expect400(post("/api/v1/subjects"), "{\"name\":\"과학\"}", "색을 골라 주세요");
    expect400(post("/api/v1/subjects"), "{\"color\":\"red\"}", "과목 이름을 입력해 주세요");
    expect400(post("/api/v1/subjects"), "{\"name\":\"   \",\"color\":\"red\"}", "과목 이름을 입력해 주세요");
    expect400(
        post("/api/v1/subjects"),
        "{\"name\":\"" + "가".repeat(31) + "\",\"color\":\"red\"}",
        "과목 이름은 30자 이하로 적어 주세요");
    send(post("/api/v1/subjects"), tokenA, "{깨진 JSON").andExpect(status().isBadRequest());
    send(get("/api/v1/subjects"), tokenA, null).andExpect(jsonPath("$.length()").value(2));

    // 이름 30자는 통과합니다.
    addSubject(tokenA, "가".repeat(30), "orange");

    // 바꾸기: 이름만 → 색만 → 둘 다. 보내지 않은 것은 그대로입니다.
    send(patch("/api/v1/subjects/" + mathId), tokenA, "{\"name\":\"국어\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(mathId))
        .andExpect(jsonPath("$.name").value("국어"))
        .andExpect(jsonPath("$.color").value("blue"));
    send(patch("/api/v1/subjects/" + mathId), tokenA, "{\"color\":\"green\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("국어"))
        .andExpect(jsonPath("$.color").value("green"));
    send(patch("/api/v1/subjects/" + mathId), tokenA, "{\"name\":\"수학\",\"color\":\"red\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("수학"))
        .andExpect(jsonPath("$.color").value("red"));
    // 지금 이름 그대로 보내는 것(수학 → 수학)은 겹침이 아닙니다.
    send(patch("/api/v1/subjects/" + mathId), tokenA, "{\"name\":\"수학\"}")
        .andExpect(status().isOk());

    // 바꾸기가 거절되는 경우: 내 다른 과목과 같은 이름 409 / 틀린 색 400 / 빈 이름 400 / 아무것도 안 보냄 400
    send(patch("/api/v1/subjects/" + mathId), tokenA, "{\"name\":\"영어\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("이미 같은 이름의 과목이 있어요"));
    // 틀린 색과 함께 보낸 이름은 바뀌지 않습니다.
    expect400(
        patch("/api/v1/subjects/" + mathId), "{\"name\":\"과학\",\"color\":\"pink\"}", colorMessage);
    expect400(
        patch("/api/v1/subjects/" + mathId), "{\"name\":\"\"}", "과목 이름은 1자 이상 30자 이하로 적어 주세요");
    expect400(patch("/api/v1/subjects/" + mathId), "{}", "바꿀 이름이나 색을 보내 주세요");
    send(get("/api/v1/subjects"), tokenA, null)
        .andExpect(jsonPath("$[0].name").value("수학"))
        .andExpect(jsonPath("$[0].color").value("red"));

    // 없는 번호는 404, 번호가 숫자가 아니면 400
    send(patch("/api/v1/subjects/999999"), tokenA, "{\"name\":\"과학\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
    send(delete("/api/v1/subjects/999999"), tokenA, null).andExpect(status().isNotFound());
    send(patch("/api/v1/subjects/abc"), tokenA, "{\"name\":\"과학\"}")
        .andExpect(status().isBadRequest());
    send(delete("/api/v1/subjects/abc"), tokenA, null).andExpect(status().isBadRequest());

    // 삭제: 204 에 본문 없음. 다시 지우면 404
    send(delete("/api/v1/subjects/" + englishId), tokenA, null)
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    send(delete("/api/v1/subjects/" + englishId), tokenA, null).andExpect(status().isNotFound());
    send(get("/api/v1/subjects"), tokenA, null)
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("수학"));

    // 지운 이름은 다시 쓸 수 있습니다.
    addSubject(tokenA, "영어", "red");
  }

  @Test
  @DisplayName("다른 사용자의 과목은 보이지도, 바뀌지도, 지워지지도 않는다 (같은 이름은 각자 쓸 수 있다)")
  void otherUsersSubjectsAreInvisible() throws Exception {
    String mathOfA = addSubject(tokenA, "수학", "blue");

    // 비이에게는 에이의 과목이 보이지 않습니다.
    send(get("/api/v1/subjects"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));

    // 같은 이름도 비이가 따로 만들 수 있습니다. (사용자마다 따로 셉니다)
    String mathOfB = addSubject(tokenB, "수학", "red");
    assertThat(mathOfB).isNotEqualTo(mathOfA);

    // 비이가 에이의 과목 번호로 바꾸거나 지우려 하면 "없는 번호" 와 똑같이 404 입니다. (403 이 아닙니다)
    send(patch("/api/v1/subjects/" + mathOfA), tokenB, "{\"name\":\"훔침\",\"color\":\"green\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
    send(delete("/api/v1/subjects/" + mathOfA), tokenB, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));

    // 에이의 과목은 그대로이고, 비이의 과목도 그대로입니다.
    send(get("/api/v1/subjects"), tokenA, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(mathOfA))
        .andExpect(jsonPath("$[0].name").value("수학"))
        .andExpect(jsonPath("$[0].color").value("blue"));
    send(get("/api/v1/subjects"), tokenB, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(mathOfB))
        .andExpect(jsonPath("$[0].color").value("red"));
  }

  @Test
  @DisplayName("계획이나 공부 기록이 있는 과목은 409 로 지워지지 않고, 이름·색은 바꿀 수 있고, 없는 과목은 지워진다")
  void subjectInUseCannotBeDeleted() throws Exception {
    String withPlan = addSubject(tokenA, "수학", "blue");
    String withSession = addSubject(tokenA, "영어", "red");
    String unused = addSubject(tokenA, "과학", "green");
    insertPlanOf(userIdA, withPlan);
    insertSessionOf(userIdA, withSession);

    String message = "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요";
    send(delete("/api/v1/subjects/" + withPlan), tokenA, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value(message));
    send(delete("/api/v1/subjects/" + withSession), tokenA, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value(message));
    send(get("/api/v1/subjects"), tokenA, null).andExpect(jsonPath("$.length()").value(3));

    // 쓰고 있는 과목도 이름과 색은 바꿀 수 있습니다.
    send(patch("/api/v1/subjects/" + withPlan), tokenA, "{\"name\":\"수학Ⅱ\",\"color\":\"purple\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("수학Ⅱ"));

    // 쓰지 않는 과목은 지워집니다.
    send(delete("/api/v1/subjects/" + unused), tokenA, null).andExpect(status().isNoContent());
    send(get("/api/v1/subjects"), tokenA, null).andExpect(jsonPath("$.length()").value(2));

    // 비이의 계획이 같은 과목 번호를 가리키고 있어도(잘못된 데이터), 에이의 삭제 판단에는 영향이 없습니다.
    String another = addSubject(tokenA, "한국사", "orange");
    insertPlanOf(userIdB, another);
    send(delete("/api/v1/subjects/" + another), tokenA, null).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("토큰이 없거나 틀리면 네 주소 모두 401 이다")
  void allRequireLogin() throws Exception {
    String body = "{\"name\":\"수학\",\"color\":\"blue\"}";
    for (String token : new String[] {null, "틀린-토큰"}) {
      send(get("/api/v1/subjects"), token, null)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
      send(post("/api/v1/subjects"), token, body).andExpect(status().isUnauthorized());
      send(patch("/api/v1/subjects/1"), token, "{\"name\":\"국어\"}")
          .andExpect(status().isUnauthorized());
      send(delete("/api/v1/subjects/1"), token, null).andExpect(status().isUnauthorized());
    }
  }

  @Test
  @DisplayName("내 컴퓨터의 테스트 토큰으로도 쓸 수 있다 (Swagger 로 불러 볼 때 쓰는 토큰)")
  void testTokenWorks() throws Exception {
    ResultActions created =
        send(
            post("/api/v1/subjects"), "local-test-token", "{\"name\":\"테스트과목\",\"color\":\"red\"}");
    created.andExpect(status().isCreated());
    String id = JsonPath.read(responseBody(created), "$.id");

    send(get("/api/v1/subjects"), "local-test-token", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(id)).value("테스트과목"));

    // 이 테스트가 만든 과목은 바로 지워서, 다른 테스트에 남지 않게 합니다.
    send(delete("/api/v1/subjects/" + id), "local-test-token", null)
        .andExpect(status().isNoContent());
  }

  private void expect400(MockHttpServletRequestBuilder builder, String body, String message)
      throws Exception {
    send(builder, tokenA, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
  }
}
