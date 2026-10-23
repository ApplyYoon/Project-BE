package com.ithopeschool.projectbe.domain.school;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.school.dto.SchoolClassesResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller 만 띄워서 주소와 응답 모양을 봅니다. 진짜 로직(SchoolTodayService)은 가짜입니다.
@WebMvcTest(SchoolClassController.class)
class SchoolClassControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchoolTodayService schoolTodayService;

  @Test
  @DisplayName("GET 은 학년을 서비스에 넘기고 반 번호 목록을 돌려준다")
  void getsClasses() throws Exception {
    given(schoolTodayService.getMyClasses(2))
        .willReturn(SchoolClassesResponse.of(2, List.of(1, 2, 3), false));

    mockMvc
        .perform(get("/api/v1/school/classes").param("grade", "2"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"grade\":2,\"classNos\":[1,2,3],\"complete\":false}", true));
  }

  @Test
  @DisplayName("학년이 숫자가 아니면 400")
  void rejectsNonNumberGrade() throws Exception {
    mockMvc
        .perform(get("/api/v1/school/classes").param("grade", "가"))
        .andExpect(status().isBadRequest());
  }
}
