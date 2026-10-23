package com.ithopeschool.projectbe.domain.school;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
//   https://open.neis.go.kr/hub/mealServiceDietInfo  급식 — 조식 · 중식 · 석식 메뉴
//   https://open.neis.go.kr/hub/hisTimetable  고등학교 시간표 — 학년 · 반 · 교시 · 과목
//   https://open.neis.go.kr/hub/schulAflcoinfo · schoolMajorinfo  학교 계열 · 학과 — 가입 때 "무슨 과야?"
// (2026-10-19)
//   https://open.neis.go.kr/hub/classInfo  학급정보 — 한 학년에 몇 반까지 있는지 (2026-10-23)
//
// ■ 인증키 (환경변수 NEIS_API_KEY)
//   있으면 한 번에 1000건까지 받습니다. 없으면 NEIS 가 한 번에 5건만 주고 쪽 넘기기도 안 돼서,
//   기간을 한 주씩 잘라 여러 번 받고, 5건이 꽉 찬 주는 하루씩 다시 받습니다. (2026-10-17 확인)
//   그래서 키 없이 받는 기간은 짧게 잡습니다. (SchoolEventService)
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

  // 학교 번호로 학교 홈페이지 주소를 찾습니다. (학교기본정보의 HMPG_ADRES, 예: 'https://bundang-h.goesn.kr/')
  // 홈페이지가 적혀 있지 않거나 NEIS 가 멈추면 빈 값입니다. (SchoolNoticeService 가 가정통신문을 읽을 때 씀)
  public Optional<String> findHomepage(String schoolCode) {
    Optional<JsonNode> rows =
        rowsOf(get("schoolInfo", "SD_SCHUL_CODE=" + schoolCode, 1), "schoolInfo");
    if (rows.isEmpty() || rows.get().isEmpty()) {
      return Optional.empty();
    }
    String homepage = rows.get().get(0).path("HMPG_ADRES").asString("").trim();
    return homepage.isBlank() ? Optional.empty() : Optional.of(homepage);
  }

  // 학교 번호로 학교 종류를 찾습니다. (학교기본정보의 교육청 코드 · 고등학교 구분 · 일반계/전문계) 2026-10-19 확인:
  //   한국디지털미디어고 → (J10, 특성화고, 전문계)   분당고 → (J10, 일반고, 일반계)
  //   수원하이텍고(마이스터고) → (J10, 특목고, 전문계)   경기과학고 → (J10, 특목고, 일반계)
  // NEIS 가 멈추면 빈 값, 그런 번호의 학교가 없으면 빈 목록입니다. (번호가 같은 학교는 하나뿐이라 0개 또는 1개)
  public Optional<List<NeisSchool>> findSchool(String schoolCode) {
    Optional<JsonNode> rows =
        rowsOf(get("schoolInfo", "SD_SCHUL_CODE=" + schoolCode, 1), "schoolInfo");
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    List<NeisSchool> schools = new ArrayList<>();
    for (JsonNode row : rows.get()) {
      String officeCode = row.path("ATPT_OFCDC_SC_CODE").asString("").trim();
      if (!officeCode.isEmpty()) {
        schools.add(
            new NeisSchool(
                officeCode,
                row.path("HS_SC_NM").asString("").trim(),
                row.path("HS_GNRL_BUSNS_SC_NM").asString("").trim()));
      }
    }
    return Optional.of(schools);
  }

  // 학교의 학과 이름을 받습니다. (학교학과정보 schoolMajorinfo) 같은 이름은 한 번만, NEIS 가 준 순서대로 둡니다.
  //   한국디지털미디어고 → [웹프로그래밍과, 해킹방어과, e-비즈니스과, 디지털콘텐츠과]
  // ⚠️ 키가 없으면 한 번에 5줄만 와서, 계열(학교계열정보 schulAflcoinfo)마다 나눠 받습니다. (2026-10-19 확인)
  //    그래도 한 계열에 학과 줄이 5개를 넘으면 뒤가 잘립니다. (서울로봇고는 계열마다 14 · 17줄)
  //    다 받았는지는 complete 로 알려 줍니다. 한 번이라도 실패하면 빈 값입니다.
  public Optional<NeisMajors> fetchMajors(String officeCode, String schoolCode) {
    String school = "ATPT_OFCDC_SC_CODE=" + officeCode + "&SD_SCHUL_CODE=" + schoolCode;
    if (hasApiKey()) {
      Optional<JsonNode> rows =
          rowsOf(get("schoolMajorinfo", school, PAGE_SIZE_WITH_KEY), "schoolMajorinfo");
      return rows.map(
          found -> new NeisMajors(majorNamesOf(List.of(found)), found.size() < PAGE_SIZE_WITH_KEY));
    }

    Optional<JsonNode> trackRows =
        rowsOf(get("schulAflcoinfo", school, PAGE_SIZE_WITHOUT_KEY), "schulAflcoinfo");
    if (trackRows.isEmpty()) {
      return Optional.empty();
    }
    List<String> tracks = textsOf(List.of(trackRows.get()), "ORD_SC_NM");
    // 계열 정보가 없는 학교는 계열 없이 한 번만 부릅니다.
    List<String> queries = new ArrayList<>();
    if (tracks.isEmpty()) {
      queries.add(school);
    }
    for (String track : tracks) {
      // 한글 계열 이름은 RestClient 가 주소에 넣을 때 알아서 바꿔(인코딩) 줍니다.
      queries.add(school + "&ORD_SC_NM=" + track);
    }
    // 계열이 5줄 꽉 차서 왔으면 계열이 더 있을 수 있습니다.
    boolean complete = trackRows.get().size() < PAGE_SIZE_WITHOUT_KEY;
    List<JsonNode> parts = new ArrayList<>();
    for (String query : queries) {
      Optional<JsonNode> rows =
          rowsOf(get("schoolMajorinfo", query, PAGE_SIZE_WITHOUT_KEY), "schoolMajorinfo");
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      if (rows.get().size() >= PAGE_SIZE_WITHOUT_KEY) {
        complete = false;
      }
      parts.add(rows.get());
    }
    return Optional.of(new NeisMajors(majorNamesOf(parts), complete));
  }

  // 여러 번 받은 학과 줄에서 학과 이름(DDDEP_NM)만 겹치지 않게 꺼냅니다.
  static List<String> majorNamesOf(List<JsonNode> parts) {
    return textsOf(parts, "DDDEP_NM");
  }

  // 줄들에서 한 칸의 글자를 겹치지 않게, 처음 나온 순서대로 꺼냅니다. 빈 칸은 버립니다.
  private static List<String> textsOf(List<JsonNode> parts, String field) {
    Set<String> texts = new LinkedHashSet<>();
    for (JsonNode rows : parts) {
      for (JsonNode row : rows) {
        String text = row.path(field).asString("").trim();
        if (!text.isEmpty()) {
          texts.add(text);
        }
      }
    }
    return new ArrayList<>(texts);
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
      // 5건이 꽉 차서 왔으면 더 있을 수 있습니다. (예: 시험 3일 + 행사 3개인 주) 그 주는 하루씩 다시 받습니다.
      if (week.get().size() >= PAGE_SIZE_WITHOUT_KEY) {
        week = fetchDayByDay(officeCode, schoolCode, start, end);
        if (week.isEmpty()) {
          return Optional.empty();
        }
      }
      events.addAll(week.get());
    }
    return Optional.of(events);
  }

  // from ~ to 를 하루씩 받습니다. (하루에 행사가 6개 넘는 날은 거의 없어서 여기서 더 쪼개지 않습니다)
  private Optional<List<NeisEvent>> fetchDayByDay(
      String officeCode, String schoolCode, LocalDate from, LocalDate to) {
    List<NeisEvent> events = new ArrayList<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      Optional<List<NeisEvent>> oneDay =
          fetchRange(officeCode, schoolCode, day, day, PAGE_SIZE_WITHOUT_KEY);
      if (oneDay.isEmpty()) {
        return Optional.empty();
      }
      events.addAll(oneDay.get());
    }
    return Optional.of(events);
  }

  // 그날의 급식(조식 · 중식 · 석식)을 받습니다. 급식이 없는 날(주말 · 방학)은 빈 목록, 실패하면 빈 값입니다.
  // 하루에 많아야 3줄이라, 키가 없어도(한 번에 5줄) 다 옵니다. (2026-10-18 확인)
  public Optional<List<NeisMeal>> fetchMeals(String officeCode, String schoolCode, LocalDate day) {
    String query =
        "ATPT_OFCDC_SC_CODE="
            + officeCode
            + "&SD_SCHUL_CODE="
            + schoolCode
            + "&MLSV_YMD="
            + day.format(NEIS_DATE);
    Optional<JsonNode> rows =
        rowsOf(get("mealServiceDietInfo", query, PAGE_SIZE_WITHOUT_KEY), "mealServiceDietInfo");
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    List<NeisMeal> meals = new ArrayList<>();
    for (JsonNode row : rows.get()) {
      toMeal(row, day).ifPresent(meals::add);
    }
    return Optional.of(meals);
  }

  // NEIS 의 급식 한 줄 → 끼니 하나. 끼니 이름이나 메뉴가 없으면 버립니다.
  static Optional<NeisMeal> toMeal(JsonNode row, LocalDate day) {
    String kind = row.path("MMEAL_SC_NM").asString("").trim();
    List<String> dishes = dishesOf(row.path("DDISH_NM").asString(""));
    if (kind.isEmpty() || dishes.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new NeisMeal(day, kind, dishes));
  }

  // 메뉴 글을 메뉴 목록으로 바꿉니다. 메뉴는 <br/> 로 이어져 오고, 뒤의 (1.5.6) 같은 숫자는 알레르기 번호라 뗍니다.
  //   '흑미밥 <br/>나주곰탕 (1.5.6.16)<br/>배추김치 (9)' → ['흑미밥', '나주곰탕', '배추김치']
  static List<String> dishesOf(String text) {
    List<String> dishes = new ArrayList<>();
    for (String part : text.split("<br\\s*/?>")) {
      String dish = part.replaceAll("\\([0-9.\\s]*\\)", "").trim();
      if (!dish.isEmpty()) {
        dishes.add(dish);
      }
    }
    return dishes;
  }

  // 그날 한 반의 시간표를 교시 순서로 받습니다. 수업이 없는 날은 빈 목록, 실패하면 빈 값입니다.
  // ⚠️ 키가 없으면 NEIS 가 5줄(1~5교시)만 줍니다. 쪽 넘기기(pIndex)나 교시(PERIO)로 나눠 받아도
  //    같은 5줄이 옵니다. (2026-10-18 확인) 그래서 키가 없으면 뒤 교시는 잘립니다.
  public Optional<List<NeisPeriod>> fetchTimetable(
      String officeCode, String schoolCode, int grade, int classNo, LocalDate day) {
    String query =
        "ATPT_OFCDC_SC_CODE="
            + officeCode
            + "&SD_SCHUL_CODE="
            + schoolCode
            + "&ALL_TI_YMD="
            + day.format(NEIS_DATE)
            + "&GRADE="
            + grade
            + "&CLASS_NM="
            + classNo;
    int pageSize = hasApiKey() ? PAGE_SIZE_WITH_KEY : PAGE_SIZE_WITHOUT_KEY;
    Optional<JsonNode> rows = rowsOf(get("hisTimetable", query, pageSize), "hisTimetable");
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    List<NeisPeriod> periods = new ArrayList<>();
    for (JsonNode row : rows.get()) {
      toPeriod(row).ifPresent(periods::add);
    }
    periods.sort((a, b) -> Integer.compare(a.period(), b.period()));
    return Optional.of(periods);
  }

  // 한 학년의 반 번호를 작은 것부터 받습니다. (학급정보 classInfo, 2026-10-23)
  //   분당고 2학년 → [1, 2, …, 12]
  // 예전에는 반 고르기 칸이 학교와 상관없이 1~30반이라, 없는 반을 고르면 시간표가 늘 비었습니다.
  // ⚠️ 키가 없으면 NEIS 가 5줄만 주고 쪽 넘기기도 안 됩니다. (분당고 2학년 12개 반 중 5개만 옴, 2026-10-23 확인)
  //    그래도 머리(head)의 전체 개수(list_total_count)는 맞게 와서, 그때는 1반부터 전체 개수까지 채웁니다.
  //    (반 번호는 거의 1부터 빈틈없이 붙습니다) 이렇게 채운 목록이면 complete 가 false 입니다.
  // 반 이름이 숫자가 아닌 줄(예: 'A')은 버립니다. 시간표를 반 번호(숫자)로 묻기 때문입니다.
  // 실패하면 빈 값, 그 학년에 반이 없으면 빈 목록입니다.
  public Optional<NeisClasses> fetchClasses(
      String officeCode, String schoolCode, int year, int grade) {
    String query =
        "ATPT_OFCDC_SC_CODE="
            + officeCode
            + "&SD_SCHUL_CODE="
            + schoolCode
            + "&AY="
            + year
            + "&GRADE="
            + grade;
    int pageSize = hasApiKey() ? PAGE_SIZE_WITH_KEY : PAGE_SIZE_WITHOUT_KEY;
    Optional<String> body = get("classInfo", query, pageSize);
    Optional<JsonNode> rows = rowsOf(body, "classInfo");
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    // TreeSet: 겹치지 않게 모으면서 작은 번호부터 늘어섭니다.
    Set<Integer> classNos = new TreeSet<>();
    for (JsonNode row : rows.get()) {
      try {
        classNos.add(Integer.parseInt(row.path("CLASS_NM").asString("").trim()));
      } catch (NumberFormatException e) {
        // 숫자가 아닌 반 이름은 버립니다.
      }
    }
    int total = totalCountOf(body, "classInfo");
    boolean complete = rows.get().size() >= total;
    if (!complete) {
      for (int classNo = 1; classNo <= total; classNo++) {
        classNos.add(classNo);
      }
    }
    return Optional.of(new NeisClasses(new ArrayList<>(classNos), complete));
  }

  // NEIS 의 시간표 한 줄 → 교시 하나. 교시가 숫자가 아니거나 과목이 없으면 버립니다.
  static Optional<NeisPeriod> toPeriod(JsonNode row) {
    String subject = row.path("ITRT_CNTNT").asString("").trim();
    int period;
    try {
      period = Integer.parseInt(row.path("PERIO").asString("").trim());
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
    if (subject.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new NeisPeriod(period, subject));
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

  // 받은 글의 머리(head)에 적힌 전체 줄 수입니다. 키가 없어 5줄만 왔어도 이 숫자는 전체를 셉니다.
  //   { "classInfo": [ { "head": [ { "list_total_count": 12 }, … ] }, { "row": [ …5줄 ] } ] } → 12
  // 머리가 없거나(데이터 없음 INFO-200 포함) 모양이 다르면 0 입니다.
  static int totalCountOf(Optional<String> body, String service) {
    if (body.isEmpty()) {
      return 0;
    }
    try {
      return JSON.readTree(body.get())
          .path(service)
          .path(0)
          .path("head")
          .path(0)
          .path("list_total_count")
          .asInt(0);
    } catch (JacksonException e) {
      return 0;
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

  // NEIS 급식 한 끼. kind 는 '조식' · '중식' · '석식' 입니다.
  public record NeisMeal(LocalDate date, String kind, List<String> dishes) {}

  // NEIS 시간표 한 칸. period 는 교시(1, 2, …)
  public record NeisPeriod(int period, String subject) {}

  // NEIS 학교 하나의 종류. schoolType 은 '일반고' · '특성화고' · '특목고' · '자율고', course 는 '일반계' · '전문계'
  public record NeisSchool(String officeCode, String schoolType, String course) {

    // 학과를 물어볼 학교인지. 특성화고와 마이스터고(특목고 중 전문계)는 학과가 있습니다.
    public boolean asksMajor() {
      return "특성화고".equals(schoolType) || "전문계".equals(course);
    }
  }

  // NEIS 학과 목록. complete 가 false 면 키가 없어서 뒤가 잘렸을 수 있습니다.
  public record NeisMajors(List<String> names, boolean complete) {}

  // NEIS 한 학년의 반 번호 목록. complete 가 false 면 키가 없어서 전체 개수로 1반부터 채운 것입니다.
  public record NeisClasses(List<Integer> classNos, boolean complete) {}
}
