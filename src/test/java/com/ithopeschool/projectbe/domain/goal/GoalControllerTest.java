package com.ithopeschool.projectbe.domain.goal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.goal.dto.GoalCreateRequest;
import com.ithopeschool.projectbe.domain.goal.dto.GoalResponse;
import com.ithopeschool.projectbe.domain.goal.dto.GoalUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양 · 상태 번호" 를 확인합니다. 진짜 로직(GoalService)은 가짜로 바꿉니다.
@WebMvcTest(GoalController.class)
class GoalControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GoalService goalService;

  private static final LocalDate EXAM_DAY = LocalDate.of(2026, 11, 19);

  private static final GoalResponse.Snapshot OLD =
      new GoalResponse.Snapshot("수학 3등급", EXAM_DAY, "5등급", null, null, 1790000000000L, "시간이 모자라서");

  private static final GoalResponse GOAL =
      new GoalResponse(
          "4",
          "score",
          "수학 4등급",
          EXAM_DAY,
          "5등급",
          null,
          null,
          "자신감",
          "후회",
          "폰을 봐요",
          "폰을 다른 방에",
          "active",
          null,
          null,
          List.of(OLD),
          1789000000000L);

  private static final String CREATE_BODY =
      """
      {"kind":"score","title":" 수학 3등급 ","dDay":"2026-11-19","currentScore":"5등급",
       "benefit":"자신감","cost":"후회","obstacle":"폰을 봐요","plan":"폰을 다른 방에"}
      """;

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(goalService);
  }

  @Test
  @DisplayName("목록은 200 이고 FE 의 Goal 과 같은 이름(dDay · history · createdAt …)을 쓴다")
  void getGoals() throws Exception {
    given(goalService.getGoals()).willReturn(List.of(GOAL));

    mockMvc
        .perform(get("/api/v1/goals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("4"))
        .andExpect(jsonPath("$[0].kind").value("score"))
        .andExpect(jsonPath("$[0].dDay").value("2026-11-19"))
        .andExpect(jsonPath("$[0].status").value("active"))
        .andExpect(jsonPath("$[0].history[0].title").value("수학 3등급"))
        .andExpect(jsonPath("$[0].history[0].changedAt").value(1790000000000L))
        .andExpect(jsonPath("$[0].history[0].reason").value("시간이 모자라서"))
        .andExpect(jsonPath("$[0].createdAt").value(1789000000000L))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("만들면 201 이고, dDay 를 날짜로 읽고 글의 앞뒤 공백은 지워서 넘긴다")
  void create() throws Exception {
    given(goalService.create(any())).willReturn(GOAL);

    postJson(CREATE_BODY).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("4"));
    verify(goalService)
        .create(
            new GoalCreateRequest(
                "score", "수학 3등급", EXAM_DAY, "5등급", null, null, "자신감", "후회", "폰을 봐요", "폰을 다른 방에"));
  }

  @Test
  @DisplayName("목표 한 줄이 비면 400 (FE 와 같은 문장)")
  void blankTitle() throws Exception {
    expectRejected(postJson(CREATE_BODY.replace(" 수학 3등급 ", "  ")), "목표를 한 줄로 적어 주세요");
  }

  @Test
  @DisplayName("D-day 가 없으면 400")
  void missingDDay() throws Exception {
    expectRejected(
        postJson(CREATE_BODY.replace("\"dDay\":\"2026-11-19\",", "")), "D-day 는 내일 이후로 골라 주세요");
  }

  @Test
  @DisplayName("WOOP 글이 비면 400 (방해물)")
  void blankObstacle() throws Exception {
    expectRejected(postJson(CREATE_BODY.replace("\"폰을 봐요\"", "\"\"")), "내 안의 방해물을 하나 골라 주세요");
  }

  @Test
  @DisplayName("고치기는 200 이고 보낸 것만 넘긴다")
  void update() throws Exception {
    given(goalService.update(eq(4L), any())).willReturn(GOAL);

    mockMvc
        .perform(
            patch("/api/v1/goals/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"givenUp\",\"givenUpReason\":\" 다른 목표로 \"}"))
        .andExpect(status().isOk());
    verify(goalService)
        .update(
            4L,
            new GoalUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, "givenUp", null,
                "다른 목표로"));
  }

  @Test
  @DisplayName("고치기에서 공백뿐인 목표 한 줄은 400")
  void updateBlankTitle() throws Exception {
    expectRejected(
        mockMvc.perform(
            patch("/api/v1/goals/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"reason\":\"x\"}")),
        "목표는 1자 이상 40자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("이력은 200 이고 오래된 것부터 배열로 준다")
  void history() throws Exception {
    given(goalService.getHistory(4L)).willReturn(List.of(OLD));

    mockMvc
        .perform(get("/api/v1/goals/4/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("수학 3등급"))
        .andExpect(jsonPath("$[0].dDay").value("2026-11-19"))
        .andExpect(jsonPath("$[0].length()").value(7));
  }
}
