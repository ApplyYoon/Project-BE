package com.ithopeschool.projectbe.domain.diary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.diary.dto.DiaryResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Instant;
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

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(DiaryService)은 가짜입니다.
@WebMvcTest(DiaryController.class)
class DiaryControllerTest {

  private static final LocalDate DATE = LocalDate.parse("2026-10-24");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DiaryService diaryService;

  private static DiaryResponse sample() {
    return new DiaryResponse(
        "7",
        DATE,
        "폰을 보다가 수학을 못 했다",
        Instant.parse("2026-10-24T09:00:00Z"),
        true,
        List.of("TIRED"),
        List.of("phone"),
        List.of("수학"),
        false,
        List.of());
  }

  private ResultActions create(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/diaries").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("일기를 쓰면 201 과 저장된 일기·뽑은 값을 돌려준다")
  void creates() throws Exception {
    given(diaryService.create(any())).willReturn(sample());

    create("{\"content\":\"폰을 보다가 수학을 못 했다\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("7"))
        .andExpect(jsonPath("$.date").value("2026-10-24"))
        .andExpect(jsonPath("$.analyzed").value(true))
        .andExpect(jsonPath("$.emotions[0]").value("TIRED"))
        .andExpect(jsonPath("$.causes[0]").value("phone"))
        .andExpect(jsonPath("$.keywords[0]").value("수학"))
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.helpMessage").isEmpty());
  }

  @Test
  @DisplayName("내용이 비었거나 2000자를 넘으면 400 이고 Service 를 부르지 않는다")
  void rejectsBadContent() throws Exception {
    create("{\"content\":\"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create("{\"content\":\"" + "가".repeat(2001) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(diaryService);
  }

  @Test
  @DisplayName("목록은 200 과 배열, 날짜 모양이 틀리면 400")
  void lists() throws Exception {
    given(diaryService.getDiaries(DATE.minusDays(6), DATE)).willReturn(List.of(sample()));

    mockMvc
        .perform(get("/api/v1/diaries").param("from", "2026-10-18").param("to", "2026-10-24"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].content").value("폰을 보다가 수학을 못 했다"));

    mockMvc
        .perform(get("/api/v1/diaries").param("from", "10월").param("to", "2026-10-24"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("지우면 204, 없는 번호면 404")
  void deletes() throws Exception {
    mockMvc.perform(delete("/api/v1/diaries/7")).andExpect(status().isNoContent());
    verify(diaryService).delete(7L);

    willThrow(new ApiException(ErrorCode.NOT_FOUND, "일기를 찾을 수 없어요"))
        .given(diaryService)
        .delete(99L);
    mockMvc
        .perform(delete("/api/v1/diaries/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("일기를 찾을 수 없어요"));
  }
}
