package com.ithopeschool.projectbe.domain.school;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.school.dto.SchoolTodayResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소와 응답 모양을 봅니다. 진짜 로직(SchoolTodayService)은 가짜입니다.
@WebMvcTest(SchoolTodayController.class)
class SchoolTodayControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchoolTodayService schoolTodayService;

  @Test
  @DisplayName("GET 은 오늘 날짜 · 학교 · 급식 · 학사일정을 돌려준다")
  void getsToday() throws Exception {
    LocalDate today = LocalDate.of(2026, 10, 19);
    given(schoolTodayService.getMyToday())
        .willReturn(
            SchoolTodayResponse.of(
                today,
                "7530560",
                "한국디지털미디어고등학교",
                List.of(new SchoolTodayResponse.Meal(today, "중식", List.of("흑미밥", "나주곰탕"))),
                List.of(
                    new SchoolTodayResponse.Event(
                        LocalDate.of(2026, 11, 2), "2학기 2차 정기시험", List.of(1, 2), true, 14))));

    mockMvc
        .perform(get("/api/v1/school/today"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"date\":\"2026-10-19\","
                        + "\"school\":{\"code\":\"7530560\",\"name\":\"한국디지털미디어고등학교\"},"
                        + "\"meals\":[{\"date\":\"2026-10-19\",\"kind\":\"중식\",\"dishes\":[\"흑미밥\",\"나주곰탕\"]}],"
                        + "\"events\":[{\"date\":\"2026-11-02\",\"name\":\"2학기 2차 정기시험\","
                        + "\"grades\":[1,2],\"exam\":true,\"dDay\":14}]}",
                    true));
  }

  @Test
  @DisplayName("학교를 안 골랐으면 school: null 과 빈 목록")
  void noSchool() throws Exception {
    given(schoolTodayService.getMyToday())
        .willReturn(SchoolTodayResponse.noSchool(LocalDate.of(2026, 10, 19)));

    mockMvc
        .perform(get("/api/v1/school/today"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"date\":\"2026-10-19\",\"school\":null,\"meals\":[],\"events\":[]}", true));
  }
}
