package com.ithopeschool.projectbe.domain.restday;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.restday.dto.RestDayResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소 · 상태 번호 · 응답 모양을 확인합니다. 진짜 로직(RestDayService)은 가짜로 바꿉니다.
@WebMvcTest(RestDayController.class)
class RestDayControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RestDayService restDayService;

  private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

  @Test
  @DisplayName("휴식 처리는 201 이고 { date, createdAt } 를 돌려준다")
  void rest() throws Exception {
    given(restDayService.rest(OCT_12)).willReturn(new RestDayResponse(OCT_12, 1790000000000L));

    mockMvc
        .perform(post("/api/v1/rest-days/2026-10-12"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value("2026-10-12"))
        .andExpect(jsonPath("$.createdAt").value(1790000000000L))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("주소의 날짜 모양이 틀리면 400 이고 Service 는 부르지 않는다")
  void wrongDate() throws Exception {
    mockMvc.perform(post("/api/v1/rest-days/2026-13-01")).andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/v1/rest-days/오늘")).andExpect(status().isBadRequest());
    verifyNoInteractions(restDayService);
  }

  @Test
  @DisplayName("한도를 넘으면 Service 가 던진 409 가 그대로 나간다")
  void conflict() throws Exception {
    given(restDayService.rest(OCT_12))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "휴식 처리는 한 달에 4번까지예요"));

    mockMvc
        .perform(post("/api/v1/rest-days/2026-10-12"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.message").value("휴식 처리는 한 달에 4번까지예요"));
  }

  @Test
  @DisplayName("목록은 200 이고 배열이다")
  void list() throws Exception {
    given(restDayService.getRestDays(OCT_12.withDayOfMonth(1), OCT_12))
        .willReturn(List.of(new RestDayResponse(OCT_12, 1L)));

    mockMvc
        .perform(get("/api/v1/rest-days").param("from", "2026-10-01").param("to", "2026-10-12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].date").value("2026-10-12"));
  }
}
