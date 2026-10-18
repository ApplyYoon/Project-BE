package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 시험 API 를 처음부터 끝까지 불러 봅니다.
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 시험이 보이거나 바뀌지 않는지" 도 같이 봅니다.
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다. (탈퇴가 시험과 점수까지 지우는지도 확인됩니다)
@SpringBootTest
@AutoConfigureMockMvc
class ExamFlowTest {

  private static final String PASSWORD = "study2026";
  // 이미 지난 시험과 아직 한참 남은 시험의 날짜. (실제 점수는 시험 날짜가 지나야 넣을 수 있습니다)
  private static final String PAST_DATE = "2020-04-20";
  private static final String FUTURE_DATE = "2099-01-01";
  private static final String TOO_EARLY_MESSAGE = "시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요";

  @Autowired private MockMvc mockMvc;
  @Autowired private ExamRepository examRepository;
  @Autowired private ExamScoreRepository examScoreRepository;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "exam-a-" + suffix + "@starflow.com";
    String emailB = "exam-b-" + suffix + "@starflow.com";
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
    // 탈퇴하면 두 사람의 시험과 과목별 점수도 함께 지워집니다.
    assertThat(examRepository.findByUserIdOrderByExamDateDescIdDesc(userIdA)).isEmpty();
    assertThat(examRepository.findByUserIdOrderByExamDateDescIdDesc(userIdB)).isEmpty();
    assertThat(examScoreRepository.findAll())
        .noneMatch(score -> score.getUserId().equals(userIdA) || score.getUserId().equals(userIdB));
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

  // 시험 등록 요청의 JSON. 과목은 (번호, 예상 점수) 쌍으로 넘깁니다.
  private static String examJson(String type, String name, String date, String... subjectScores) {
    StringBuilder scores = new StringBuilder();
    for (int i = 0; i < subjectScores.length; i += 2) {
      if (i > 0) {
        scores.append(",");
      }
      scores.append(
          "{\"subjectId\":\"%s\",\"expectedScore\":%s}"
              .formatted(subjectScores[i], subjectScores[i + 1]));
    }
    return "{\"type\":\"%s\",\"name\":\"%s\",\"date\":\"%s\",\"subjectScores\":[%s]}"
        .formatted(type, name, date, scores);
  }

  // 실제 점수 입력 요청의 JSON. 과목은 (번호, 실제 점수) 쌍으로 넘깁니다.
  private static String resultJson(String... subjectScores) {
    StringBuilder scores = new StringBuilder();
    for (int i = 0; i < subjectScores.length; i += 2) {
      if (i > 0) {
        scores.append(",");
      }
      scores.append(
          "{\"subjectId\":\"%s\",\"actualScore\":%s}"
              .formatted(subjectScores[i], subjectScores[i + 1]));
    }
    return "{\"subjectScores\":[%s]}".formatted(scores);
  }

  // 시험을 등록하고 201 을 확인한 뒤, 만들어진 시험의 번호(글자)를 돌려줍니다.
  private String addExam(String token, String body) throws Exception {
    ResultActions result = send(post("/api/v1/exams"), token, body);
    result.andExpect(status().isCreated());
    return JsonPath.read(responseBody(result), "$.id");
  }

  // ---- 시나리오 ----

  @Test
  @DisplayName("로그인하지 않으면 세 주소 모두 401")
  void requiresLogin() throws Exception {
    send(get("/api/v1/exams"), null, null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    send(post("/api/v1/exams"), null, examJson("mock", "시험", PAST_DATE, "1", "80"))
        .andExpect(status().isUnauthorized());
    send(patch("/api/v1/exams/1"), null, resultJson("1", "80"))
        .andExpect(status().isUnauthorized());
    // 틀린 토큰도 마찬가지입니다.
    send(get("/api/v1/exams"), "not-a-real-token", null).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("등록 → 목록 → 틀린 값 거절 → 실제 점수 입력(날짜 전 400, 날짜 후 200) → 고치기")
  void registerListAndEnterScores() throws Exception {
    String math = addSubject(tokenA, "수학", "blue");
    String english = addSubject(tokenA, "영어", "purple");

    // 처음에는 빈 배열입니다. (404 가 아닙니다)
    send(get("/api/v1/exams"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));

    // 등록: 201 과 {id, type, name, date, subjectScores, goalId} 여섯 칸. id 는 글자, type 은 소문자, 실제 점수는
    // null
    // 목표 없이 등록해서 goalId 는 null 입니다. (이슈 #228)
    ResultActions created =
        send(
            post("/api/v1/exams"),
            tokenA,
            examJson("school", "1학기 중간고사", PAST_DATE, math, "85", english, "70"));
    created
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.type").value("school"))
        .andExpect(jsonPath("$.name").value("1학기 중간고사"))
        .andExpect(jsonPath("$.date").value(PAST_DATE))
        .andExpect(jsonPath("$.goalId").isEmpty())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$.subjectScores.length()").value(2))
        .andExpect(jsonPath("$.subjectScores[0].subjectId").value(math))
        .andExpect(jsonPath("$.subjectScores[0].expectedScore").value(85))
        .andExpect(jsonPath("$.subjectScores[0].actualScore").isEmpty())
        .andExpect(jsonPath("$.subjectScores[1].subjectId").value(english))
        .andExpect(jsonPath("$.subjectScores[1].expectedScore").value(70));
    String pastExamId = JsonPath.read(responseBody(created), "$.id");
    String futureExamId = addExam(tokenA, examJson("mock", "미래 모의고사", FUTURE_DATE, math, "90"));

    // 목록: 시험 날짜가 늦은 것부터
    send(get("/api/v1/exams"), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(futureExamId))
        .andExpect(jsonPath("$[0].name").value("미래 모의고사"))
        .andExpect(jsonPath("$[1].id").value(pastExamId))
        .andExpect(jsonPath("$[1].subjectScores.length()").value(2));

    // 틀린 값은 400 이고, 저장되지 않습니다.
    String[] wrongBodies = {
      // 모르는 종류
      examJson("midterm", "시험", PAST_DATE, math, "80"),
      // 점수가 0~100 밖
      examJson("mock", "시험", PAST_DATE, math, "101"),
      examJson("mock", "시험", PAST_DATE, math, "-1"),
      // 정수가 아닌 점수 (85 로 잘려서 저장되면 안 됩니다)
      examJson("mock", "시험", PAST_DATE, math, "85.5"),
      // 같은 과목 두 번
      examJson("mock", "시험", PAST_DATE, math, "80", math, "90"),
      // 과목 없이
      examJson("mock", "시험", PAST_DATE),
      // 이름이 비어 있음
      examJson("mock", "   ", PAST_DATE, math, "80"),
      // 날짜 모양이 틀림
      examJson("mock", "시험", "2026/10/20", math, "80"),
      // 19자리 과목 번호 (500 이 아니라 400 이어야 합니다)
      examJson("mock", "시험", PAST_DATE, "9999999999999999999", "80"),
    };
    for (String body : wrongBodies) {
      send(post("/api/v1/exams"), tokenA, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    send(get("/api/v1/exams"), tokenA, null).andExpect(jsonPath("$.length()").value(2));

    // 시험 날짜 전에는 실제 점수를 넣을 수 없습니다.
    send(patch("/api/v1/exams/" + futureExamId), tokenA, resultJson(math, "80"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(TOO_EARLY_MESSAGE));

    // 시험에 없는 과목 · 같은 과목 두 번 · 0~100 밖의 점수는 400 이고, 앞의 맞는 과목도 바뀌지 않습니다.
    String other = addSubject(tokenA, "과학", "green");
    for (String body :
        new String[] {
          resultJson(math, "80", other, "50"),
          resultJson(math, "80", math, "90"),
          resultJson(math, "101"),
          resultJson(math, "-1"),
          resultJson(math, "80.5")
        }) {
      send(patch("/api/v1/exams/" + pastExamId), tokenA, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    send(get("/api/v1/exams"), tokenA, null)
        .andExpect(jsonPath("$[1].subjectScores[0].actualScore").isEmpty());

    // 날짜가 지난 시험에는 넣을 수 있습니다. 보낸 과목만 바뀝니다.
    send(patch("/api/v1/exams/" + pastExamId), tokenA, resultJson(math, "80"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pastExamId))
        .andExpect(jsonPath("$.subjectScores[0].expectedScore").value(85))
        .andExpect(jsonPath("$.subjectScores[0].actualScore").value(80))
        .andExpect(jsonPath("$.subjectScores[1].actualScore").isEmpty());

    // 이미 넣은 점수는 다시 보내서 고칠 수 있습니다. 목록에도 반영됩니다.
    send(patch("/api/v1/exams/" + pastExamId), tokenA, resultJson(math, "82", english, "72"))
        .andExpect(status().isOk());
    send(get("/api/v1/exams"), tokenA, null)
        .andExpect(jsonPath("$[1].subjectScores[0].actualScore").value(82))
        .andExpect(jsonPath("$[1].subjectScores[1].actualScore").value(72));
  }

  @Test
  @DisplayName("남의 시험은 보이지도, 바뀌지도 않고 남의 과목으로 시험을 만들 수도 없다")
  void othersDataIsInvisible() throws Exception {
    String mathOfA = addSubject(tokenA, "수학", "blue");
    String examOfA = addExam(tokenA, examJson("mock", "에이의 시험", PAST_DATE, mathOfA, "85"));

    // 비이의 목록에는 에이의 시험이 없습니다.
    send(get("/api/v1/exams"), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));

    // 비이가 에이의 시험 번호로 실제 점수를 넣으려 하면 "없는 번호" 와 똑같이 404 입니다.
    send(patch("/api/v1/exams/" + examOfA), tokenB, resultJson(mathOfA, "10"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("시험을 찾을 수 없어요"));
    send(patch("/api/v1/exams/99999999"), tokenB, resultJson(mathOfA, "10"))
        .andExpect(status().isNotFound());

    // 비이가 에이의 과목 번호로 시험을 만들려 하면 404 이고, 시험이 저장되지 않습니다.
    send(post("/api/v1/exams"), tokenB, examJson("mock", "시험", PAST_DATE, mathOfA, "80"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
    send(get("/api/v1/exams"), tokenB, null).andExpect(jsonPath("$.length()").value(0));

    // 에이의 시험은 아무것도 바뀌지 않았습니다.
    send(get("/api/v1/exams"), tokenA, null)
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectScores[0].actualScore").isEmpty());
  }

  @Test
  @DisplayName("네 가지 종류(mock · school · quiz · etc)가 모두 등록되고 그대로 돌아온다")
  void allFourTypes() throws Exception {
    String math = addSubject(tokenA, "수학", "blue");

    for (String type : new String[] {"mock", "school", "quiz", "etc"}) {
      send(post("/api/v1/exams"), tokenA, examJson(type, type + " 시험", PAST_DATE, math, "80"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.type").value(type));
    }
    send(get("/api/v1/exams"), tokenA, null).andExpect(jsonPath("$.length()").value(4));
  }
}
