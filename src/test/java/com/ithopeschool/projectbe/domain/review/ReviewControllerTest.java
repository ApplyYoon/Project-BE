package com.ithopeschool.projectbe.domain.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.review.dto.ReviewResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
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

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(ReviewService)은 가짜입니다.
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReviewService reviewService;

  private ResultActions save(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    save(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(reviewService);
  }

  @Test
  @DisplayName("시험 후 회고를 보내면 200 과 저장된 회고를 돌려준다")
  void savesExamReview() throws Exception {
    given(reviewService.save(any()))
        .willReturn(
            new ReviewResponse(
                "3", "exam", "7", null, List.of("4", "시간 분배", "시간 재고 풀기"), 1792234800000L));

    save("{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"4\",\"시간 분배\",\"시간 재고 풀기\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.type").value("exam"))
        .andExpect(jsonPath("$.examId").value("7"))
        .andExpect(jsonPath("$.answers[1]").value("시간 분배"))
        .andExpect(jsonPath("$.savedAt").value(1792234800000L));
  }

  @Test
  @DisplayName("주간 회고를 보내면 200 이고 weekStart 가 날짜 글자로 나간다")
  void savesWeeklyReview() throws Exception {
    given(reviewService.save(any()))
        .willReturn(
            new ReviewResponse(
                "4",
                "weekly",
                null,
                LocalDate.parse("2026-10-12"),
                List.of("매일 계획 지킴", "", ""),
                1792234800000L));

    save("{\"type\":\"weekly\",\"weekStart\":\"2026-10-12\",\"answers\":[\"매일 계획 지킴\",\"\",\"\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value("2026-10-12"))
        .andExpect(jsonPath("$.answers[2]").value(""));
  }

  @Test
  @DisplayName("답이 딱 200자면 통과한다 (201자는 아래 rejectsBadRequests 에서 400)")
  void answerLengthLimit() throws Exception {
    given(reviewService.save(any()))
        .willReturn(new ReviewResponse("3", "exam", "7", null, List.of("a", "b", "c"), 0L));
    String ok = "가".repeat(200);
    save("{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"" + ok + "\",\"b\",\"c\"]}")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("종류가 없거나, 답이 3개가 아니거나, 답이 200자를 넘거나, 시험 번호 모양이 틀리면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    String tooLong = "가".repeat(201);
    expectRejected("{\"examId\":\"7\",\"answers\":[\"a\",\"b\",\"c\"]}", "회고 종류를 골라 주세요");
    expectRejected("{\"type\":\"exam\",\"examId\":\"7\"}", "회고 답을 보내 주세요");
    expectRejected(
        "{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"a\",\"b\"]}",
        "회고 답은 질문 순서대로 3개를 보내 주세요");
    expectRejected(
        "{\"type\":\"weekly\",\"weekStart\":\"2026-10-12\",\"answers\":[\"a\",\"b\",\"c\",\"d\"]}",
        "회고 답은 질문 순서대로 3개를 보내 주세요");
    expectRejected(
        "{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"a\",null,\"c\"]}", "회고 답을 보내 주세요");
    expectRejected(
        "{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"a\",\"" + tooLong + "\",\"c\"]}",
        "회고 답은 하나에 200자까지 적을 수 있어요");
    expectRejected(
        "{\"type\":\"exam\",\"examId\":\"abc\",\"answers\":[\"a\",\"b\",\"c\"]}",
        "시험 번호가 올바르지 않아요");
  }

  @Test
  @DisplayName("앞뒤 공백은 지운 뒤에 글자 수를 센다 (공백 때문에 201자가 돼도 통과)")
  void trimsBeforeCounting() throws Exception {
    given(reviewService.save(any()))
        .willReturn(new ReviewResponse("3", "exam", "7", null, List.of("a", "b", "c"), 0L));
    String padded = " " + "가".repeat(200) + " ";
    save("{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"" + padded + "\",\"b\",\"c\"]}")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("서비스가 401 을 던지면 그대로 401 이다 (로그인 필요)")
  void requiresLogin() throws Exception {
    given(reviewService.save(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    save("{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"a\",\"b\",\"c\"]}")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("서비스가 404 를 던지면 그대로 404 다 (없는 시험 · 남의 시험)")
  void passesNotFound() throws Exception {
    given(reviewService.save(any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요"));
    save("{\"type\":\"exam\",\"examId\":\"7\",\"answers\":[\"a\",\"b\",\"c\"]}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("시험을 찾을 수 없어요"));
  }
}
