package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.subject.dto.SubjectCreateRequest;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectResponse;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectUpdateRequest;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 "요청 모양 검사" 와 "응답 모양·상태 번호" 를 확인합니다. 진짜 로직(SubjectService)은 가짜로 바꿉니다.
@WebMvcTest(SubjectController.class)
class SubjectControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SubjectService subjectService;

  private static final SubjectResponse MATH = new SubjectResponse("3", "수학", "blue");

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/subjects").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions patchJson(String path, String body) throws Exception {
    return mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(ResultActions result, String message) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(subjectService);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 200 이고, 과목마다 id(글자)·name·color 세 개뿐이다")
  void getSubjects() throws Exception {
    given(subjectService.getSubjects())
        .willReturn(List.of(MATH, new SubjectResponse("4", "영어", "red")));

    mockMvc
        .perform(get("/api/v1/subjects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("3"))
        .andExpect(jsonPath("$[0].name").value("수학"))
        .andExpect(jsonPath("$[0].color").value("blue"))
        .andExpect(jsonPath("$[0].length()").value(3))
        .andExpect(jsonPath("$[0].userId").doesNotExist())
        .andExpect(jsonPath("$[1].name").value("영어"));
  }

  @Test
  @DisplayName("과목이 하나도 없으면 404 가 아니라 빈 배열 []")
  void emptyList() throws Exception {
    given(subjectService.getSubjects()).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/subjects"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", true));
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 과 에러 모양")
  void listUnauthorized() throws Exception {
    given(subjectService.getSubjects())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    mockMvc
        .perform(get("/api/v1/subjects"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("로그인이 필요해요"));
  }

  // ---- 추가 ----

  @Test
  @DisplayName("추가에 성공하면 201 과 만들어진 과목을 돌려주고, 이름은 공백을 지운 값으로 넘어간다")
  void create() throws Exception {
    given(subjectService.create(any())).willReturn(MATH);

    postJson("{\"name\":\"  수학 \",\"color\":\"blue\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.name").value("수학"))
        .andExpect(jsonPath("$.color").value("blue"));

    ArgumentCaptor<SubjectCreateRequest> captor =
        ArgumentCaptor.forClass(SubjectCreateRequest.class);
    verify(subjectService).create(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("수학");
    assertThat(captor.getValue().color()).isEqualTo("blue");
  }

  @Test
  @DisplayName("이름 30자는 통과한다")
  void createNameMaxLength() throws Exception {
    given(subjectService.create(any())).willReturn(MATH);

    postJson("{\"name\":\"" + "가".repeat(30) + "\",\"color\":\"red\"}")
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("이름이 없거나 비었거나 31자면, 색이 없거나 비었으면 400 과 그 문장")
  void createRejectsBadValues() throws Exception {
    expectRejected(postJson("{\"color\":\"blue\"}"), "과목 이름을 입력해 주세요");
    expectRejected(postJson("{\"name\":\"\",\"color\":\"blue\"}"), "과목 이름을 입력해 주세요");
    expectRejected(postJson("{\"name\":\"   \",\"color\":\"blue\"}"), "과목 이름을 입력해 주세요");
    expectRejected(
        postJson("{\"name\":\"" + "가".repeat(31) + "\",\"color\":\"blue\"}"),
        "과목 이름은 30자 이하로 적어 주세요");
    expectRejected(postJson("{\"name\":\"수학\"}"), "색을 골라 주세요");
    expectRejected(postJson("{\"name\":\"수학\",\"color\":\"\"}"), "색을 골라 주세요");
    expectRejected(postJson("{\"name\":\"수학\",\"color\":null}"), "색을 골라 주세요");
  }

  @Test
  @DisplayName("JSON 모양이 깨졌거나 본문이 없으면 400")
  void createRejectsBrokenBody() throws Exception {
    postJson("{\"name\":").andExpect(status().isBadRequest());
    postJson("").andExpect(status().isBadRequest());
    postJson("[]").andExpect(status().isBadRequest());
    verifyNoInteractions(subjectService);
  }

  @Test
  @DisplayName("FE 가 id 같은 모르는 필드를 같이 보내도 무시하고 통과한다")
  void createIgnoresUnknownFields() throws Exception {
    given(subjectService.create(any())).willReturn(MATH);

    postJson("{\"id\":\"9\",\"name\":\"수학\",\"color\":\"blue\",\"userId\":2}")
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Service 가 던진 409(같은 이름)·400(틀린 색)은 에러 모양으로 나간다")
  void createServiceErrors() throws Exception {
    given(subjectService.create(any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "이미 같은 이름의 과목이 있어요"));
    postJson("{\"name\":\"수학\",\"color\":\"blue\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이미 같은 이름의 과목이 있어요"));

    // 이미 "예외를 던지게" 해 둔 가짜를 다시 설정할 때는 given(가짜.메서드()) 로 부르면 그 순간 예외가 터집니다.
    // 그래서 이 모양(willThrow ... given ...)으로 설정합니다.
    willThrow(
            new ApiException(
                ErrorCode.VALIDATION_ERROR, "색은 red, orange, green, blue, purple 중에서 골라 주세요"))
        .given(subjectService)
        .create(any());
    postJson("{\"name\":\"수학\",\"color\":\"pink\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("바꾸기에 성공하면 200 과 바뀐 과목을 돌려주고, 보내지 않은 값은 null 로 넘어간다")
  void update() throws Exception {
    given(subjectService.update(eq(3L), any())).willReturn(new SubjectResponse("3", "국어", "blue"));

    patchJson("/api/v1/subjects/3", "{\"name\":\" 국어 \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("3"))
        .andExpect(jsonPath("$.name").value("국어"));

    ArgumentCaptor<SubjectUpdateRequest> captor =
        ArgumentCaptor.forClass(SubjectUpdateRequest.class);
    verify(subjectService).update(eq(3L), captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("국어");
    assertThat(captor.getValue().color()).isNull();
  }

  @Test
  @DisplayName("색만, 또는 이름과 색을 같이 보내도 통과한다")
  void updateColorOnlyOrBoth() throws Exception {
    given(subjectService.update(eq(3L), any())).willReturn(MATH);

    patchJson("/api/v1/subjects/3", "{\"color\":\"red\"}").andExpect(status().isOk());
    patchJson("/api/v1/subjects/3", "{\"name\":\"수학\",\"color\":\"red\"}")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("이름을 보냈는데 비었거나 31자면 400")
  void updateRejectsBadName() throws Exception {
    expectRejected(
        patchJson("/api/v1/subjects/3", "{\"name\":\"\"}"), "과목 이름은 1자 이상 30자 이하로 적어 주세요");
    expectRejected(
        patchJson("/api/v1/subjects/3", "{\"name\":\"   \"}"), "과목 이름은 1자 이상 30자 이하로 적어 주세요");
    expectRejected(
        patchJson("/api/v1/subjects/3", "{\"name\":\"" + "가".repeat(31) + "\"}"),
        "과목 이름은 1자 이상 30자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("번호 자리에 숫자가 아닌 글자가 오면 400 (500 이 아니다)")
  void idMustBeNumber() throws Exception {
    patchJson("/api/v1/subjects/abc", "{\"name\":\"국어\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(delete("/api/v1/subjects/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(subjectService);
  }

  @Test
  @DisplayName("Service 가 던진 404(없는 과목·남의 과목)는 에러 모양으로 나간다")
  void updateNotFound() throws Exception {
    given(subjectService.update(eq(99L), any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요"));

    patchJson("/api/v1/subjects/99", "{\"name\":\"국어\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
  }

  // ---- 삭제 ----

  @Test
  @DisplayName("삭제에 성공하면 204 이고 본문이 없다")
  void deleteSubject() throws Exception {
    mockMvc
        .perform(delete("/api/v1/subjects/3"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(subjectService).delete(3L);
  }

  @Test
  @DisplayName("계획이나 기록이 있어서 못 지우면 409, 없는 과목이면 404, 로그인 안 했으면 401")
  void deleteErrors() throws Exception {
    willThrow(new ApiException(ErrorCode.CONFLICT, "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요"))
        .given(subjectService)
        .delete(3L);
    willThrow(new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요"))
        .given(subjectService)
        .delete(99L);
    willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"))
        .given(subjectService)
        .delete(7L);

    mockMvc
        .perform(delete("/api/v1/subjects/3"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요"));
    mockMvc
        .perform(delete("/api/v1/subjects/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mockMvc
        .perform(delete("/api/v1/subjects/7"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("PUT 처럼 약속하지 않은 요청 방법은 405")
  void methodNotAllowed() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/subjects/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"국어\"}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }
}
