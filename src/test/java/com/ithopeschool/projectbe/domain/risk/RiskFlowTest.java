package com.ithopeschool.projectbe.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.consent.ConsentRecord;
import com.ithopeschool.projectbe.domain.consent.ConsentRecordRepository;
import com.ithopeschool.projectbe.domain.job.JobContext;
import com.ithopeschool.projectbe.domain.job.JobResultStore;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 위험 예측 API 를 처음부터 끝까지 불러 봅니다. (이슈 #49)
// 새 사용자(에이·비이)를 가입시켜서 "남의 예측이 보이거나 섞이지 않는지",
// "예측 → 그날이 지남 → 맞았는지 채워짐", "동의 안 한 사람의 저장 차단", "탈퇴하면 이유까지 지워지는지" 도 같이 봅니다.
// 날짜는 실행하는 순간의 "오늘"(새벽 4시 기준)에서 셉니다.
@SpringBootTest
@AutoConfigureMockMvc
class RiskFlowTest {

  private static final String PASSWORD = "study2026";

  @Autowired private MockMvc mockMvc;
  @Autowired private RiskForecastRepository riskForecastRepository;
  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private StudySessionRepository studySessionRepository;
  @Autowired private ConsentRecordRepository consentRecordRepository;
  @Autowired private RiskConfirmJob riskConfirmJob;
  @Autowired private JobResultStore jobResultStore;
  // 딸린 테이블은 엔티티가 아니라서 SQL 로 줄 수를 셉니다.
  @Autowired private EntityManager entityManager;

  private String tokenA;
  private String tokenB;
  private Long userIdA;
  private Long userIdB;
  private LocalDate today;

  @BeforeEach
  void signUpTwoUsers() throws Exception {
    today = DayRule.dateOf(Instant.now());
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String emailA = "risk-a-" + suffix + "@starflow.com";
    String emailB = "risk-b-" + suffix + "@starflow.com";
    userIdA = signup(emailA, "에이에이");
    userIdB = signup(emailB, "비이비이");
    tokenA = login(emailA);
    tokenB = login(emailB);
  }

  private static String dateBody(LocalDate date) {
    return "{\"date\":\"" + date + "\"}";
  }

  @Test
  @DisplayName("예측 → 수면이 늦게 들어와 다시 예측하면 같은 줄이 고쳐진다 → 조회 · 목록 · 틀린 요청")
  void wholeFlow() throws Exception {
    try {
      // 기록이 하나도 없으면 위험 없음(NONE). 그래도 한 줄을 남깁니다.
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.date").value(today.toString()))
          .andExpect(jsonPath("$.riskLevel").value("NONE"))
          .andExpect(jsonPath("$.reasons.length()").value(0))
          .andExpect(jsonPath("$.actualAchievementRate").isEmpty())
          .andExpect(jsonPath("$.wasCorrect").isEmpty());

      // 수면 기록이 나중에 들어옵니다: 오늘 새벽 4시보다 2시간 전(= 새벽 2시)에 잠
      Instant sleptAt = DayRule.startOf(today).minus(2, ChronoUnit.HOURS);
      send(
              post("/api/v1/daily-signals"),
              tokenA,
              "{\"date\":\"" + today + "\",\"sleptAt\":" + sleptAt.toEpochMilli() + "}")
          .andExpect(status().isOk());

      // 다시 예측하면 같은 줄이 최신으로 고쳐집니다. (줄이 늘지 않음)
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.riskLevel").value("WATCH"))
          .andExpect(jsonPath("$.reasons.length()").value(1))
          .andExpect(jsonPath("$.reasons[0].code").value("LATE_SLEEP"))
          .andExpect(jsonPath("$.reasons[0].label").value("늦게 잠"));
      assertThat(
              riskForecastRepository.findByUserIdAndForecastDateBetweenOrderByForecastDateAsc(
                  userIdA, today, today))
          .hasSize(1);

      // 같은 수면 기록은 "내일" 의 어젯밤이 아니라서, 내일은 위험 없음입니다.
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today.plusDays(1)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.riskLevel").value("NONE"));

      // 조회: 기록된 하루 · 기간 목록
      send(get("/api/v1/risk-forecasts/" + today), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.riskLevel").value("WATCH"));
      send(
              get("/api/v1/risk-forecasts?from=" + today.minusDays(1) + "&to=" + today.plusDays(1)),
              tokenA,
              null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.forecasts.length()").value(2))
          .andExpect(jsonPath("$.forecasts[0].date").value(today.toString()))
          .andExpect(jsonPath("$.forecasts[1].date").value(today.plusDays(1).toString()))
          .andExpect(jsonPath("$.confirmedCount").value(0))
          .andExpect(jsonPath("$.correctCount").value(0));

      // 조회는 줄을 만들지 않습니다: 예측하지 않은 날은 404
      send(get("/api/v1/risk-forecasts/" + today.minusDays(3)), tokenA, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

      // ── 틀린 요청들 ──
      // 지난 날과 모레 이후는 예측할 수 없음
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today.minusDays(1)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("위험 예측은 오늘이나 내일만 할 수 있어요"));
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today.plusDays(2)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("위험 예측은 오늘이나 내일만 할 수 있어요"));
      // 날짜를 빼거나, 모양이 틀리거나, 날짜가 아닌 값
      send(post("/api/v1/risk-forecasts"), tokenA, "{}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("예측할 날짜를 보내 주세요"));
      send(post("/api/v1/risk-forecasts"), tokenA, "{\"date\":\"abc\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
      send(post("/api/v1/risk-forecasts"), tokenA, "{\"date\":\"2026-02-30\"}")
          .andExpect(status().isBadRequest());
      // 숫자로 보내도 오늘·내일이 아니면 400 입니다. (숫자는 날짜로 읽히더라도 먼 날이 됩니다)
      send(post("/api/v1/risk-forecasts"), tokenA, "{\"date\":20261013}")
          .andExpect(status().isBadRequest());
      // 목록: 끝이 시작보다 앞 · 63일 · 날짜 빠짐
      send(get("/api/v1/risk-forecasts?from=2026-10-10&to=2026-10-09"), tokenA, null)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("끝 날짜는 시작 날짜와 같거나 뒤여야 해요"));
      send(get("/api/v1/risk-forecasts?from=2026-08-01&to=2026-10-02"), tokenA, null)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("한 번에 62일까지만 볼 수 있어요"));
      send(get("/api/v1/risk-forecasts?from=2026-10-01"), tokenA, null)
          .andExpect(status().isBadRequest());
      // 조회 주소의 날짜가 틀림
      send(get("/api/v1/risk-forecasts/abc"), tokenA, null).andExpect(status().isBadRequest());
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("그날이 지난 뒤: 새로 예측할 때 내 지난 예측이 채워지고, 예약 작업은 모든 학생 것을 채운다 (맞았는지 · 남의 것은 섞이지 않음)")
  void confirmsAfterTheDayPasses() throws Exception {
    try {
      LocalDate yesterday = today.minusDays(1);
      LocalDate twoDaysAgo = today.minusDays(2);

      // 에이: 어제 "주의" 라고 예측했고, 어제 계획 60분 중 30분만 함 → 50% → 못 채운 날 → 맞음
      Long subjectA = createSubject(tokenA, "수학");
      studyPlanRepository.save(new StudyPlan(userIdA, subjectA, yesterday, "수학", 60));
      studySessionRepository.save(session(userIdA, subjectA, yesterday, 30));
      riskForecastRepository.save(
          new RiskForecast(
              userIdA, yesterday, RiskLevel.WATCH, EnumSet.of(RiskReason.WEAK_WEEKDAY)));

      // 비이: 그저께 "없음" 이라고 예측했고, 그날 계획을 다 함 → 100% → 맞음. 비이는 API 를 부르지 않습니다.
      Long subjectB = createSubject(tokenB, "영어");
      studyPlanRepository.save(new StudyPlan(userIdB, subjectB, twoDaysAgo, "영어", 60));
      studySessionRepository.save(session(userIdB, subjectB, twoDaysAgo, 60));
      riskForecastRepository.save(
          new RiskForecast(userIdB, twoDaysAgo, RiskLevel.NONE, EnumSet.noneOf(RiskReason.class)));

      // 지난 예측은 아직 비어 있습니다.
      send(get("/api/v1/risk-forecasts/" + yesterday), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.actualAchievementRate").isEmpty())
          .andExpect(jsonPath("$.wasCorrect").isEmpty());

      // 에이가 오늘을 예측하면, 어제 것이 함께 채워집니다.
      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today)).andExpect(status().isOk());
      send(get("/api/v1/risk-forecasts/" + yesterday), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.actualAchievementRate").value(50))
          .andExpect(jsonPath("$.wasCorrect").value(true));

      // 예약 작업: 비이의 그저께 것이 채워집니다. (비이가 아무것도 부르지 않아도)
      riskConfirmJob.run(new JobContext(today, Instant.now()));
      assertThat(jobResultStore.find(RiskConfirmJob.NAME, today)).isPresent();
      send(get("/api/v1/risk-forecasts/" + twoDaysAgo), tokenB, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.actualAchievementRate").value(100))
          .andExpect(jsonPath("$.wasCorrect").value(true));

      // 같은 작업을 한 번 더 돌려도 결과가 그대로입니다. (이미 채운 줄은 건드리지 않음)
      riskConfirmJob.run(new JobContext(today, Instant.now()));
      send(get("/api/v1/risk-forecasts/" + twoDaysAgo), tokenB, null)
          .andExpect(jsonPath("$.actualAchievementRate").value(100))
          .andExpect(jsonPath("$.wasCorrect").value(true));

      // 목록에서 맞은 개수가 세어집니다. 에이는 확인된 것 1개 중 1개 맞음 (오늘 것은 아직 확인 전)
      send(get("/api/v1/risk-forecasts?from=" + twoDaysAgo + "&to=" + today), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.forecasts.length()").value(2))
          .andExpect(jsonPath("$.confirmedCount").value(1))
          .andExpect(jsonPath("$.correctCount").value(1));

      // 남의 예측은 보이지 않습니다: 비이에게는 에이의 어제 예측이 없고, 에이에게는 비이의 그저께 예측이 없음
      send(get("/api/v1/risk-forecasts/" + yesterday), tokenB, null)
          .andExpect(status().isNotFound());
      send(get("/api/v1/risk-forecasts/" + twoDaysAgo), tokenA, null)
          .andExpect(status().isNotFound());
      send(get("/api/v1/risk-forecasts?from=" + twoDaysAgo + "&to=" + today), tokenB, null)
          .andExpect(jsonPath("$.forecasts.length()").value(1));
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("동의 문구가 바뀌어 다시 동의하지 않은 사람은 예측을 저장할 수 없고(403), 보기는 되며, 아무 줄도 만들어지지 않는다")
  void outdatedConsentCannotSaveButCanRead() throws Exception {
    try {
      // 문구가 바뀌었다고 칩니다: 이 사람의 가장 최근 동의가 옛 버전(v0)이 됩니다.
      consentRecordRepository.save(new ConsentRecord(userIdA, Instant.now(), "v0", true));

      send(post("/api/v1/risk-forecasts"), tokenA, dateBody(today))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
      // 보기는 됩니다. (GET 은 줄을 쓰지 않으므로, 이 검사를 피해 가는 길이 없습니다)
      send(get("/api/v1/risk-forecasts/" + today), tokenA, null).andExpect(status().isNotFound());
      send(get("/api/v1/risk-forecasts?from=" + today + "&to=" + today), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.forecasts.length()").value(0));
      assertThat(
              riskForecastRepository.findAll().stream().filter(f -> f.getUserId().equals(userIdA)))
          .isEmpty();
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("탈퇴하면 예측과 딸린 이유가 모두 지워지고, 남의 것은 그대로다")
  void withdrawDeletesForecastsAndReasons() throws Exception {
    send(post("/api/v1/risk-forecasts"), tokenB, dateBody(today)).andExpect(status().isOk());
    // 이유가 있는 줄을 둘 다에게 만들어 둡니다. (늦게 잠 이유 한 줄씩)
    for (Long userId : new Long[] {userIdA, userIdB}) {
      riskForecastRepository.save(
          new RiskForecast(
              userId,
              today.minusDays(5),
              RiskLevel.WATCH,
              EnumSet.of(RiskReason.LATE_SLEEP, RiskReason.WEAK_WEEKDAY)));
    }
    List<Long> idsOfA = forecastIdsOf(userIdA);
    List<Long> idsOfB = forecastIdsOf(userIdB);
    assertThat(countReasons(idsOfA)).isEqualTo(2);

    withdraw(tokenA);
    try {
      assertThat(forecastIdsOf(userIdA)).isEmpty();
      // 딸린 테이블에는 userId 가 없어서, 지운 예측 번호로 남은 줄이 없는지 봅니다.
      assertThat(countReasons(idsOfA)).isZero();
      // 비이 것은 그대로입니다.
      assertThat(forecastIdsOf(userIdB)).hasSize(2);
      assertThat(countReasons(idsOfB)).isEqualTo(2);
    } finally {
      withdraw(tokenB);
    }
  }

  // ---- 도우미 ----

  // date 하루의 한국 시간 저녁 7시에 minutes 분 공부한 기록
  private static StudySession session(Long userId, Long subjectId, LocalDate date, int minutes) {
    Instant start = DayRule.startOf(date).plus(15, ChronoUnit.HOURS);
    return new StudySession(userId, subjectId, start, start.plus(minutes, ChronoUnit.MINUTES), 0);
  }

  private List<Long> forecastIdsOf(Long userId) {
    return riskForecastRepository.findAll().stream()
        .filter(forecast -> forecast.getUserId().equals(userId))
        .map(RiskForecast::getId)
        .toList();
  }

  // 예측 번호들(ids)에 딸린 이유 줄이 몇 개인지 셉니다. 번호는 파라미터로 넘깁니다.
  private long countReasons(List<Long> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    return ((Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from risk_forecast_reasons where risk_forecast_id in (:ids)")
                .setParameter("ids", ids)
                .getSingleResult())
        .longValue();
  }

  private Long createSubject(String token, String name) throws Exception {
    ResultActions result =
        send(post("/api/v1/subjects"), token, "{\"name\":\"" + name + "\",\"color\":\"red\"}");
    result.andExpect(status().isCreated());
    return Long.valueOf(JsonPath.read(responseBody(result), "$.id"));
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
