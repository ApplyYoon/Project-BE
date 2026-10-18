package com.ithopeschool.projectbe.domain.school;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.school.dto.SchoolNoticesResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소와 응답 모양을 봅니다. 진짜 로직(SchoolNoticeService)은 가짜입니다.
@WebMvcTest(SchoolNoticeController.class)
class SchoolNoticeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchoolNoticeService schoolNoticeService;

  @Test
  @DisplayName("GET 은 학교 · 받은 시각 · 최근 글(제목 · 날짜 · 종류 · 주소)을 돌려준다")
  void getsNotices() throws Exception {
    given(schoolNoticeService.getMyNotices())
        .willReturn(
            SchoolNoticesResponse.of(
                "7530075",
                "테스트고등학교",
                Instant.ofEpochMilli(1792310400000L),
                List.of(
                    new SchoolNoticesResponse.Notice(
                        "2학기 2차 지필평가 시험 범위 안내",
                        LocalDate.of(2026, 10, 15),
                        "가정통신문",
                        "https://test-h.example.kr/view?nttSn=1"))));

    mockMvc
        .perform(get("/api/v1/school/notices"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"school\":{\"code\":\"7530075\",\"name\":\"테스트고등학교\"},"
                        + "\"fetchedAt\":1792310400000,"
                        + "\"notices\":[{\"title\":\"2학기 2차 지필평가 시험 범위 안내\","
                        + "\"date\":\"2026-10-15\",\"kind\":\"가정통신문\","
                        + "\"url\":\"https://test-h.example.kr/view?nttSn=1\"}]}",
                    true));
  }

  @Test
  @DisplayName("학교를 안 골랐으면 school · fetchedAt 이 null 이고 글은 빈 목록")
  void noSchool() throws Exception {
    given(schoolNoticeService.getMyNotices()).willReturn(SchoolNoticesResponse.noSchool());

    mockMvc
        .perform(get("/api/v1/school/notices"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"school\":null,\"fetchedAt\":null,\"notices\":[]}", true));
  }
}
