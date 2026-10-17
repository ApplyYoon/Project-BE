package com.ithopeschool.projectbe.domain.school;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 교육부 NEIS(교육정보 개방 포털)를 부르는 곳입니다. (이슈 #282)
//   https://open.neis.go.kr/hub/schoolInfo      학교기본정보 — 학교 번호로 교육청 코드를 찾습니다
//   https://open.neis.go.kr/hub/SchoolSchedule  학사일정 — 시험 · 방학 · 행사
//
// ■ 인증키 (환경변수 NEIS_API_KEY)
//   있으면 한 번에 1000건까지 받습니다. 없으면 NEIS 가 한 번에 5건만 주고 쪽 넘기기도 안 돼서,
//   기간을 한 주씩 잘라 여러 번 받습니다. (2026-10-17 확인) 그래서 키 없이 받는 기간은 짧게 잡습니다. (SchoolEventService)
// ■ NEIS 가 멈추거나 이상한 답을 주면 에러를 던지지 않고 빈 값(Optional.empty)을 돌려줍니다.
//   그러면 서버는 전에 받아 둔 일정을 그대로 씁니다.
// ⚠️ 인증키는 로그에 찍지 않습니다.
@Component
public class NeisClient {

  private static final Logger log = LoggerFactory.getLogger(NeisClient.class);

  private static final String NEIS_BASE_URL = "https://open.neis.go.kr/hub";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
  // 키가 있을 때 한 번에 받는 개수 (NEIS 최대 1000)
  private static final int PAGE_SIZE_WITH_KEY = 1000;
  // 키가 없을 때 한 번에 받는 개수 (NEIS 가 정한 값)
  private static final int PAGE_SIZE_WITHOUT_KEY = 5;
  private static final DateTimeFormatter NEIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String apiKey;
  private final RestClient restClient;

  @Autowired
  public NeisClient(@Value("${app.neis.api-key:}") String apiKey) {
    this(apiKey, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 NEIS 를 끼워 넣을 때 쓰는 생성자입니다. (NeisClientTest)
  NeisClient(String apiKey, RestClient.Builder builder) {
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.restClient = builder.baseUrl(NEIS_BASE_URL).build();
  }

  // 인증키가 있는지 (있으면 한 번에 많이 받을 수 있습니다)
  public boolean hasApiKey() {
    return !apiKey.isEmpty();
  }

  // 학교 번호로 교육청 코드를 찾습니다. (예: '7530560' → 'J10' 경기도교육청) 못 찾으면 빈 값입니다.
  public Optional<String> findOfficeCode(String schoolCode) {
    Optional<JsonNode> rows =
        rowsOf(get("schoolInfo", "SD_SCHUL_CODE=" + schoolCode, 1), "schoolInfo");
    if (rows.isEmpty() || rows.get().isEmpty()) {
      return Optional.empty();
    }
    String officeCode = rows.get().get(0).path("ATPT_OFCDC_SC_CODE").asString("");
    return officeCode.isBlank() ? Optional.empty() : Optional.of(officeCode);
  }

  // from ~ to 의 학사일정을 받습니다. 한 번이라도 실패하면 빈 값입니다. (일부만 받은 일정으로 덮어쓰지 않게)
  public Optional<List<NeisEvent>> fetchSchedule(
      String officeCode, String schoolCode, LocalDate from, LocalDate to) {
    List<NeisEvent> events = new ArrayList<>();
    if (hasApiKey()) {
      Optional<List<NeisEvent>> all =
          fetchRange(officeCode, schoolCode, from, to, PAGE_SIZE_WITH_KEY);
      return all;
    }
    // 키가 없으면 5건까지만 와서, 한 주씩 잘라 받습니다.
    for (LocalDate start = from; !start.isAfter(to); start = start.plusWeeks(1)) {
      LocalDate end = start.plusDays(6).isAfter(to) ? to : start.plusDays(6);
      Optional<List<NeisEvent>> week =
          fetchRange(officeCode, schoolCode, start, end, PAGE_SIZE_WITHOUT_KEY);
      if (week.isEmpty()) {
        return Optional.empty();
      }
      events.addAll(week.get());
    }
    return Optional.of(events);
  }

  // 한 번 부르기. 데이터가 없는 기간이면 빈 목록입니다.
  private Optional<List<NeisEvent>> fetchRange(
      String officeCode, String schoolCode, LocalDate from, LocalDate to, int pageSize) {
    String query =
        "ATPT_OFCDC_SC_CODE="
            + officeCode
            + "&SD_SCHUL_CODE="
            + schoolCode
            + "&AA_FROM_YMD="
            + from.format(NEIS_DATE)
            + "&AA_TO_YMD="
            + to.format(NEIS_DATE);
    Optional<JsonNode> rows = rowsOf(get("SchoolSchedule", query, pageSize), "SchoolSchedule");
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    List<NeisEvent> events = new ArrayList<>();
    for (JsonNode row : rows.get()) {
      toEvent(row).ifPresent(events::add);
    }
    return Optional.of(events);
  }

  // NEIS 의 한 줄 → 일정 하나. 날짜나 이름이 없으면 버립니다.
  static Optional<NeisEvent> toEvent(JsonNode row) {
    String date = row.path("AA_YMD").asString("");
    String name = row.path("EVENT_NM").asString("").trim();
    if (date.length() != 8 || name.isEmpty()) {
      return Optional.empty();
    }
    LocalDate eventDate;
    try {
      eventDate = LocalDate.parse(date, NEIS_DATE);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    String content = row.path("EVENT_CNTNT").asString("").trim();
    // 학년마다 해당하는지 ('Y' 면 그 학년 행사). 셋 다 아니면 빈 목록 = 학년 구분 없음
    List<Integer> grades = new ArrayList<>();
    if ("Y".equals(row.path("ONE_GRADE_EVENT_YN").asString(""))) grades.add(1);
    if ("Y".equals(row.path("TW_GRADE_EVENT_YN").asString(""))) grades.add(2);
    if ("Y".equals(row.path("THREE_GRADE_EVENT_YN").asString(""))) grades.add(3);
    return Optional.of(new NeisEvent(eventDate, name, content.isEmpty() ? null : content, grades));
  }

  // NEIS 를 부르고 받은 글을 돌려줍니다. 실패하면 빈 값입니다.
  private Optional<String> get(String service, String query, int pageSize) {
    String uri =
        "/"
            + service
            + "?Type=json&pIndex=1&pSize="
            + pageSize
            + "&"
            + query
            + (hasApiKey() ? "&KEY=" + apiKey : "");
    try {
      return Optional.ofNullable(restClient.get().uri(uri).retrieve().body(String.class));
    } catch (RestClientException e) {
      // 시간 초과 · 연결 실패 · 4xx/5xx. 주소에 키가 있어서 예외 메시지 대신 종류만 찍습니다.
      log.warn("[NEIS] {} 호출에 실패했어요: {}", service, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 받은 글에서 row 목록을 꺼냅니다.
  //   데이터가 있으면  { "서비스": [ { "head": [...] }, { "row": [ … ] } ] }
  //   데이터가 없으면  { "RESULT": { "CODE": "INFO-200", … } }  → 빈 목록
  //   그 밖의 에러      { "RESULT": { "CODE": "ERROR-…" } }        → 빈 값(실패)
  static Optional<JsonNode> rowsOf(Optional<String> body, String service) {
    if (body.isEmpty()) {
      return Optional.empty();
    }
    try {
      JsonNode root = JSON.readTree(body.get());
      if (root.has(service)) {
        JsonNode rows = root.path(service).path(1).path("row");
        return rows.isArray() ? Optional.of(rows) : Optional.empty();
      }
      if ("INFO-200".equals(root.path("RESULT").path("CODE").asString(""))) {
        return Optional.of(JSON.createArrayNode());
      }
      log.warn("[NEIS] {} 이 처리하지 못했어요: {}", service, root.path("RESULT").path("CODE").asString(""));
      return Optional.empty();
    } catch (JacksonException e) {
      log.warn("[NEIS] {} 답의 모양이 달라요: {}", service, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  // NEIS 학사일정 하나. grades 가 비어 있으면 학년 구분이 없는 행사입니다.
  public record NeisEvent(LocalDate date, String name, String content, List<Integer> grades) {}
}
