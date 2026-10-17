package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
}
