package com.ithopeschool.projectbe.domain.fixedblock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockCreateRequest;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockResponse;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양 · 상태 번호" 를 확인합니다. 진짜 로직(FixedBlockService)은 가짜로 바꿉니다.
@WebMvcTest(FixedBlockController.class)
class FixedBlockControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FixedBlockService fixedBlockService;

  private static final FixedBlockResponse SCHOOL =
      new FixedBlockResponse("3", "학교", List.of(1, 2, 3, 4, 5), "08:00", "16:30", 1790000000000L);

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/fixed-blocks").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(fixedBlockService);
  }

  @Test
  @DisplayName("목록은 200 이고, FE 의 FixedBlock 과 같은 칸 6개를 돌려준다 (userId 없음)")
  void getBlocks() throws Exception {
    given(fixedBlockService.getBlocks()).willReturn(List.of(SCHOOL));

    mockMvc
        .perform(get("/api/v1/fixed-blocks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("3"))
        .andExpect(jsonPath("$[0].title").value("학교"))
        .andExpect(jsonPath("$[0].repeatDays[4]").value(5))
        .andExpect(jsonPath("$[0].startTime").value("08:00"))
        .andExpect(jsonPath("$[0].endTime").value("16:30"))
        .andExpect(jsonPath("$[0].createdAt").value(1790000000000L))
        .andExpect(jsonPath("$[0].length()").value(6))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("만들면 201 이고, 이름의 앞뒤 공백은 지워서 넘긴다")
  void create() throws Exception {
    given(fixedBlockService.create(any())).willReturn(SCHOOL);

    postJson(
            "{\"title\":\" 학교 \",\"repeatDays\":[1,2,3,4,5],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"));
    verify(fixedBlockService)
        .create(new FixedBlockCreateRequest("학교", List.of(1, 2, 3, 4, 5), "08:00", "16:30"));
  }

  @Test
  @DisplayName("이름이 공백뿐이면 400")
  void badTitle() throws Exception {
    expectRejected(
        postJson(
            "{\"title\":\"  \",\"repeatDays\":[1],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}"),
        "무슨 일정인지 적어 주세요");
  }

  @Test
  @DisplayName("이름이 20자를 넘으면 400")
  void longTitle() throws Exception {
    expectRejected(
        postJson(
            "{\"title\":\"가나다라마바사아자차카타파하가나다라마바사\",\"repeatDays\":[1],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}"),
        "일정 이름은 20자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("요일을 하나도 안 고르면 400")
  void badDays() throws Exception {
    expectRejected(
        postJson(
            "{\"title\":\"학교\",\"repeatDays\":[],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}"),
        "요일을 하나 이상 골라 주세요");
  }

  @Test
  @DisplayName("요일이 7 이면 400")
  void dayOutOfRange() throws Exception {
    expectRejected(
        postJson(
            "{\"title\":\"학교\",\"repeatDays\":[7],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}"),
        "반복 요일은 0(일) ~ 6(토) 으로 보내 주세요");
  }

  @Test
  @DisplayName("시각 모양이 틀리면 400 (8:00 처럼 한 자리)")
  void badTime() throws Exception {
    expectRejected(
        postJson(
            "{\"title\":\"학교\",\"repeatDays\":[1],\"startTime\":\"8:00\",\"endTime\":\"16:30\"}"),
        "시작 시각은 08:00 처럼 보내 주세요");
  }

  @Test
  @DisplayName("끝 시각을 안 보내면 400")
  void missingEnd() throws Exception {
    expectRejected(
        postJson("{\"title\":\"학교\",\"repeatDays\":[1],\"startTime\":\"08:00\"}"), "끝 시각을 보내 주세요");
  }

  @Test
  @DisplayName("바꾸기는 200 이고 보낸 것만 넘긴다")
  void update() throws Exception {
    given(fixedBlockService.update(eq(3L), any())).willReturn(SCHOOL);

    mockMvc
        .perform(
            patch("/api/v1/fixed-blocks/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endTime\":\"17:00\"}"))
        .andExpect(status().isOk());
    verify(fixedBlockService).update(3L, new FixedBlockUpdateRequest(null, null, null, "17:00"));
  }

  @Test
  @DisplayName("바꾸기에서 빈 요일 목록([])은 400")
  void updateEmptyDays() throws Exception {
    expectRejected(
        mockMvc.perform(
            patch("/api/v1/fixed-blocks/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repeatDays\":[]}")),
        "요일을 1 ~ 7개 골라 주세요");
  }

  @Test
  @DisplayName("지우기는 204")
  void deleteBlock() throws Exception {
    mockMvc.perform(delete("/api/v1/fixed-blocks/3")).andExpect(status().isNoContent());
    verify(fixedBlockService).delete(3L);
  }
}
