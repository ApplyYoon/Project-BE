package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 가짜 NEIS 서버로 응답 해석과 호출 방식(키가 있을 때 · 없을 때)을 봅니다. 진짜 NEIS 는 부르지 않습니다.
class NeisClientTest {

  private static final String SCHEDULE_ROWS =
      """
      {"SchoolSchedule":[{"head":[{"list_total_count":2},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},
       {"row":[
         {"AA_YMD":"20261209","EVENT_NM":"2학기 2차 정기시험","EVENT_CNTNT":"2학기 2차 정기시험",
          "ONE_GRADE_EVENT_YN":"Y","TW_GRADE_EVENT_YN":"Y","THREE_GRADE_EVENT_YN":"N"},
         {"AA_YMD":"20261231","EVENT_NM":"동계방학식","EVENT_CNTNT":null,
          "ONE_GRADE_EVENT_YN":"N","TW_GRADE_EVENT_YN":"N","THREE_GRADE_EVENT_YN":"N"},
         {"AA_YMD":"","EVENT_NM":"날짜 없는 줄"}
       ]}]}
      """;

  private static final String NO_DATA =
      """
      {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
      """;

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer neis = MockRestServiceServer.bindTo(builder).build();

  @Test
  @DisplayName("학교 번호로 교육청 코드를 찾는다 (학교기본정보)")
  void findsOfficeCode() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolInfo")))
        .andExpect(queryParam("SD_SCHUL_CODE", "7530560"))
        .andRespond(
            withSuccess(
                """
                {"schoolInfo":[{"head":[{"list_total_count":1}]},{"row":[{"ATPT_OFCDC_SC_CODE":"J10","SD_SCHUL_CODE":"7530560"}]}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.findOfficeCode("7530560")).contains("J10");
    neis.verify();
  }

  @Test
  @DisplayName("학교 번호로 홈페이지 주소를 찾고, 비어 있으면 빈 값 (학교기본정보 HMPG_ADRES)")
  void findsHomepage() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolInfo")))
        .andExpect(queryParam("SD_SCHUL_CODE", "7530075"))
        .andRespond(
            withSuccess(
                """
                {"schoolInfo":[{"head":[{"list_total_count":1}]},{"row":[{"ATPT_OFCDC_SC_CODE":"J10","HMPG_ADRES":"https://test-h.example.kr/"}]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolInfo")))
        .andRespond(
            withSuccess(
                """
                {"schoolInfo":[{"head":[{"list_total_count":1}]},{"row":[{"ATPT_OFCDC_SC_CODE":"J10","HMPG_ADRES":" "}]}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.findHomepage("7530075")).contains("https://test-h.example.kr/");
    assertThat(client.findHomepage("7530076")).isEmpty();
    neis.verify();
  }

  @Test
  @DisplayName("키가 있으면 한 번에 1000건으로 받고, 날짜 · 이름 · 내용 · 학년을 꺼낸다 (날짜 없는 줄은 버림)")
  void fetchesWithKey() {
    NeisClient client = new NeisClient("secret-key", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule")))
        .andExpect(queryParam("pSize", "1000"))
        .andExpect(queryParam("KEY", "secret-key"))
        .andExpect(queryParam("AA_FROM_YMD", "20260301"))
        .andExpect(queryParam("AA_TO_YMD", "20270228"))
        .andRespond(withSuccess(SCHEDULE_ROWS, MediaType.APPLICATION_JSON));

    Optional<List<NeisClient.NeisEvent>> events =
        client.fetchSchedule("J10", "7530560", LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28));

    assertThat(events)
        .contains(
            List.of(
                new NeisClient.NeisEvent(
                    LocalDate.of(2026, 12, 9), "2학기 2차 정기시험", "2학기 2차 정기시험", List.of(1, 2)),
                new NeisClient.NeisEvent(LocalDate.of(2026, 12, 31), "동계방학식", null, List.of())));
    neis.verify();
  }

  @Test
  @DisplayName("키가 없으면 NEIS 가 5건만 줘서 한 주씩 나눠 받는다 (14일 → 2번)")
  void fetchesWeeklyWithoutKey() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(times(2), requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule")))
        .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON));

    Optional<List<NeisClient.NeisEvent>> events =
        client.fetchSchedule(
            "J10", "7530560", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));

    assertThat(events).contains(List.of());
    neis.verify();
  }

  @Test
  @DisplayName("키가 없을 때 한 주가 5건으로 꽉 차서 오면 그 주를 하루씩 다시 받는다 (6번째 행사가 빠지지 않게)")
  void fullWeekIsFetchedDayByDay() {
    NeisClient client = new NeisClient("", builder);
    // 실제 NEIS 에서 본 시험 주 (2026-12-07 ~ 12-13): 행사 6개인데 키가 없으면 5개만 옴
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule")))
        .andExpect(queryParam("AA_FROM_YMD", "20261207"))
        .andExpect(queryParam("AA_TO_YMD", "20261213"))
        .andRespond(
            withSuccess(
                scheduleOf(
                    "20261208|신입생 예비소집",
                    "20261209|2학기 2차 정기시험",
                    "20261210|2학기 2차 정기시험",
                    "20261211|진학 진로 설명회",
                    "20261211|2학기 2차 정기시험"),
                MediaType.APPLICATION_JSON));
    for (int day = 7; day <= 13; day++) {
      String ymd = "202612" + (day < 10 ? "0" + day : day);
      String body =
          switch (day) {
            case 8 -> scheduleOf("20261208|신입생 예비소집");
            case 9 -> scheduleOf("20261209|2학기 2차 정기시험");
            case 10 -> scheduleOf("20261210|2학기 2차 정기시험");
            case 11 -> scheduleOf("20261211|진학 진로 설명회", "20261211|2학기 2차 정기시험");
            case 12 -> scheduleOf("20261212|학교 축제");
            default -> NO_DATA;
          };
      neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule")))
          .andExpect(queryParam("AA_FROM_YMD", ymd))
          .andExpect(queryParam("AA_TO_YMD", ymd))
          .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    Optional<List<NeisClient.NeisEvent>> events =
        client.fetchSchedule(
            "J10", "7530560", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 13));

    assertThat(events).isPresent();
    assertThat(events.get()).hasSize(6);
    assertThat(events.get()).extracting(NeisClient.NeisEvent::name).contains("학교 축제");
    neis.verify();
  }

  // NEIS 학사일정 응답을 만듭니다. 줄 하나 = "날짜|이름"
  private static String scheduleOf(String... rows) {
    StringBuilder body = new StringBuilder();
    for (String row : rows) {
      String[] parts = row.split("\\|");
      if (!body.isEmpty()) {
        body.append(',');
      }
      body.append("{\"AA_YMD\":\"")
          .append(parts[0])
          .append("\",\"EVENT_NM\":\"")
          .append(parts[1])
          .append("\"}");
    }
    return "{\"SchoolSchedule\":[{\"head\":[{\"list_total_count\":"
        + rows.length
        + "}]},{\"row\":["
        + body
        + "]}]}";
  }

  @Test
  @DisplayName("NEIS 가 에러를 주거나 멈추면 빈 값이다 (받아 둔 일정을 덮어쓰지 않게)")
  void failureIsEmpty() {
    NeisClient client = new NeisClient("k", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/SchoolSchedule")))
        .andRespond(withServerError());
    assertThat(
            client.fetchSchedule(
                "J10", "7530560", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)))
        .isEmpty();

    assertThat(
            NeisClient.rowsOf(
                Optional.of(
                    "{\"RESULT\":{\"CODE\":\"ERROR-290\",\"MESSAGE\":\"인증키가 유효하지 않습니다.\"}}"),
                "SchoolSchedule"))
        .isEmpty();
    assertThat(NeisClient.rowsOf(Optional.of("<html>"), "SchoolSchedule")).isEmpty();
  }

  @Test
  @DisplayName("급식은 그날 하루를 받고, <br/> 로 나눈 메뉴에서 알레르기 번호를 뗀다")
  void fetchesMeals() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo")))
        .andExpect(queryParam("ATPT_OFCDC_SC_CODE", "J10"))
        .andExpect(queryParam("SD_SCHUL_CODE", "7530560"))
        .andExpect(queryParam("MLSV_YMD", "20261012"))
        .andRespond(
            withSuccess(
                """
                {"mealServiceDietInfo":[{"head":[{"list_total_count":2}]},{"row":[
                  {"MMEAL_SC_NM":"중식","MLSV_YMD":"20261012","DDISH_NM":"흑미밥 <br/>나주곰탕 (1.5.6.16)<br/>배추김치 (9)"},
                  {"MMEAL_SC_NM":"석식","MLSV_YMD":"20261012","DDISH_NM":""}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));

    Optional<List<NeisClient.NeisMeal>> meals =
        client.fetchMeals("J10", "7530560", LocalDate.of(2026, 10, 12));

    // 메뉴가 빈 석식은 버립니다.
    assertThat(meals)
        .contains(
            List.of(
                new NeisClient.NeisMeal(
                    LocalDate.of(2026, 10, 12), "중식", List.of("흑미밥", "나주곰탕", "배추김치"))));
    neis.verify();
  }

  @Test
  @DisplayName("급식이 없는 날(INFO-200)은 빈 목록, NEIS 가 멈추면 빈 값(실패)")
  void mealsNoDataAndFailure() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo")))
        .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/mealServiceDietInfo")))
        .andRespond(withServerError());

    assertThat(client.fetchMeals("J10", "7530560", LocalDate.of(2026, 10, 17))).contains(List.of());
    assertThat(client.fetchMeals("J10", "7530560", LocalDate.of(2026, 10, 19))).isEmpty();
    neis.verify();
  }

  @Test
  @DisplayName("시간표는 학년 · 반 · 날짜로 받고 교시 순서로 정리한다 (교시가 숫자가 아니거나 과목이 없으면 버림)")
  void fetchesTimetable() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/hisTimetable")))
        .andExpect(queryParam("ALL_TI_YMD", "20261016"))
        .andExpect(queryParam("GRADE", "2"))
        .andExpect(queryParam("CLASS_NM", "1"))
        .andExpect(queryParam("pSize", "5"))
        .andRespond(
            withSuccess(
                """
                {"hisTimetable":[{"head":[{"list_total_count":7}]},{"row":[
                  {"PERIO":"2","ITRT_CNTNT":"마케팅과 광고"},
                  {"PERIO":"1","ITRT_CNTNT":"미적분Ⅰ"},
                  {"PERIO":"","ITRT_CNTNT":"교시 없음"},
                  {"PERIO":"3","ITRT_CNTNT":" "}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchTimetable("J10", "7530560", 2, 1, LocalDate.of(2026, 10, 16)))
        .contains(
            List.of(new NeisClient.NeisPeriod(1, "미적분Ⅰ"), new NeisClient.NeisPeriod(2, "마케팅과 광고")));
    neis.verify();
  }

  @Test
  @DisplayName("학교 번호로 학교 종류를 찾고, 없는 번호면 빈 목록 (학교기본정보 HS_SC_NM · HS_GNRL_BUSNS_SC_NM)")
  void findsSchoolType() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolInfo")))
        .andExpect(queryParam("SD_SCHUL_CODE", "7530900"))
        .andRespond(
            withSuccess(
                """
                {"schoolInfo":[{"head":[{"list_total_count":1}]},{"row":[
                  {"ATPT_OFCDC_SC_CODE":"J10","HS_SC_NM":"특목고","HS_GNRL_BUSNS_SC_NM":"전문계"}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolInfo")))
        .andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON));

    List<NeisClient.NeisSchool> meister = client.findSchool("7530900").orElseThrow();
    assertThat(meister).containsExactly(new NeisClient.NeisSchool("J10", "특목고", "전문계"));
    // 마이스터고는 '특목고' 로 나오지만 전문계라서 학과를 묻습니다.
    assertThat(meister.get(0).asksMajor()).isTrue();
    assertThat(client.findSchool("9999999")).contains(List.of());
    neis.verify();
  }

  @Test
  @DisplayName("학과를 묻는 학교: 특성화고 · 전문계만 true, 일반고 · 과학고(일반계)는 false")
  void asksMajorOnlyForVocationalSchools() {
    assertThat(new NeisClient.NeisSchool("J10", "특성화고", "전문계").asksMajor()).isTrue();
    assertThat(new NeisClient.NeisSchool("J10", "일반고", "일반계").asksMajor()).isFalse();
    assertThat(new NeisClient.NeisSchool("J10", "특목고", "일반계").asksMajor()).isFalse();
  }

  @Test
  @DisplayName("키가 없으면 계열마다 나눠 학과를 받고, 겹치는 학과 이름은 한 번만 (계열 이름은 주소에 맞게 인코딩)")
  void fetchesMajorsByTrackWithoutKey() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schulAflcoinfo")))
        .andExpect(queryParam("SD_SCHUL_CODE", "7530560"))
        .andRespond(
            withSuccess(
                """
                {"schulAflcoinfo":[{"head":[{"list_total_count":2}]},{"row":[
                  {"ORD_SC_NM":"공업계"},{"ORD_SC_NM":"상업계"}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolMajorinfo")))
        .andExpect(queryParam("ORD_SC_NM", encoded("공업계")))
        .andRespond(
            withSuccess(
                """
                {"schoolMajorinfo":[{"head":[{"list_total_count":2}]},{"row":[
                  {"DDDEP_NM":"웹프로그래밍과"},{"DDDEP_NM":"해킹방어과"}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolMajorinfo")))
        .andExpect(queryParam("ORD_SC_NM", encoded("상업계")))
        .andRespond(
            withSuccess(
                """
                {"schoolMajorinfo":[{"head":[{"list_total_count":3}]},{"row":[
                  {"DDDEP_NM":"e-비즈니스과"},{"DDDEP_NM":"웹프로그래밍과"},{"DDDEP_NM":" "}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchMajors("J10", "7530560"))
        .contains(new NeisClient.NeisMajors(List.of("웹프로그래밍과", "해킹방어과", "e-비즈니스과"), true));
    neis.verify();
  }

  @Test
  @DisplayName("키가 없을 때 한 계열이 5줄 꽉 차서 오면 잘렸을 수 있어서 complete 가 false")
  void marksMajorsIncompleteWhenTrackIsFull() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schulAflcoinfo")))
        .andRespond(
            withSuccess(
                """
                {"schulAflcoinfo":[{"head":[{"list_total_count":1}]},{"row":[{"ORD_SC_NM":"공업계"}]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolMajorinfo")))
        .andRespond(
            withSuccess(
                """
                {"schoolMajorinfo":[{"head":[{"list_total_count":17}]},{"row":[
                  {"DDDEP_NM":"e-로봇과"},{"DDDEP_NM":"로봇설계과"},{"DDDEP_NM":"로봇소프트웨어과"},
                  {"DDDEP_NM":"로봇재료과"},{"DDDEP_NM":"로봇정보통신과"}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));

    NeisClient.NeisMajors majors = client.fetchMajors("B10", "7010738").orElseThrow();
    assertThat(majors.names()).hasSize(5);
    assertThat(majors.complete()).isFalse();
    neis.verify();
  }

  @Test
  @DisplayName("키가 있으면 학과를 한 번에 1000줄까지 받고, 계열은 부르지 않는다")
  void fetchesAllMajorsAtOnceWithKey() {
    NeisClient client = new NeisClient("secret-key", builder);
    neis.expect(times(1), requestTo(startsWith("https://open.neis.go.kr/hub/schoolMajorinfo")))
        .andExpect(queryParam("pSize", "1000"))
        .andExpect(queryParam("KEY", "secret-key"))
        .andRespond(
            withSuccess(
                """
                {"schoolMajorinfo":[{"head":[{"list_total_count":2}]},{"row":[
                  {"DDDEP_NM":"로봇설계과"},{"DDDEP_NM":"로봇설계과"}
                ]}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchMajors("B10", "7010738"))
        .contains(new NeisClient.NeisMajors(List.of("로봇설계과"), true));
    neis.verify();
  }

  @Test
  @DisplayName("학과를 받다가 한 번이라도 실패하면 빈 값 (일부만 받은 목록을 주지 않음)")
  void returnsEmptyWhenMajorCallFails() {
    NeisClient client = new NeisClient("", builder);
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schulAflcoinfo")))
        .andRespond(
            withSuccess(
                """
                {"schulAflcoinfo":[{"head":[{"list_total_count":1}]},{"row":[{"ORD_SC_NM":"공업계"}]}]}
                """,
                MediaType.APPLICATION_JSON));
    neis.expect(requestTo(startsWith("https://open.neis.go.kr/hub/schoolMajorinfo")))
        .andRespond(withServerError());

    assertThat(client.fetchMajors("J10", "7530560")).isEmpty();
    neis.verify();
  }

  // 주소에 들어간 한글은 %EA%B3%B5… 처럼 바뀌어 있어서, 비교할 값도 같은 모양으로 바꿉니다.
  private static String encoded(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
