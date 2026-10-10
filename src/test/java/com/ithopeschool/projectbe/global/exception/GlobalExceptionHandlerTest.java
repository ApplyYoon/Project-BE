package com.ithopeschool.projectbe.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 어떤 에러가 나도 docs/API.md 3장의 모양으로 나가는지 확인합니다.
//   { "error": { "code": "...", "message": "..." } }
// 아직 에러를 내는 진짜 API 가 없어서, 맨 아래에 이 테스트에서만 쓰는 가짜 API(FakeController)를 만들었습니다.
@WebMvcTest(GlobalExceptionHandlerTest.FakeController.class)
@Import(GlobalExceptionHandlerTest.FakeController.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void apiExceptionBecomesErrorShape() throws Exception {
    // Service 에서 ApiException(NOT_FOUND) 를 던지면 → 404 와 에러 모양
    mockMvc
        .perform(get("/api/v1/error-test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("계획을 찾을 수 없어요"));
  }

  @Test
  void statusFollowsTheErrorCode() throws Exception {
    // 에러 종류가 CONFLICT 면 상태 번호는 409
    mockMvc
        .perform(get("/api/v1/error-test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("이미 같은 이름의 과목이 있어요"));
  }

  @Test
  void invalidBodyUsesTheMessageOnTheAnnotation() throws Exception {
    // @NotBlank(message = "이름을 입력해 주세요") 인데 빈 값을 보내면 → 400 과 그 문장
    mockMvc
        .perform(
            post("/api/v1/error-test/names")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("이름을 입력해 주세요"));
  }

  @Test
  void brokenJsonIsValidationError() throws Exception {
    // JSON 모양이 깨져 있으면 → 400
    mockMvc
        .perform(
            post("/api/v1/error-test/names")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("보낸 값을 다시 확인해 주세요"));
  }

  @Test
  void wrongTypeInPathIsValidationError() throws Exception {
    // 숫자 자리에 글자를 보내면(/items/abc) → 400
    mockMvc
        .perform(get("/api/v1/error-test/items/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void missingParameterIsValidationError() throws Exception {
    // 꼭 필요한 값(?keyword=)을 빼고 부르면 → 400
    mockMvc
        .perform(get("/api/v1/error-test/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void unknownAddressIsNotFound() throws Exception {
    // 없는 주소를 부르면 → 404 와 에러 모양 (Spring 기본 모양이 아님)
    mockMvc
        .perform(get("/api/v1/error-test/no-such-address"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("찾을 수 없어요"));
  }

  @Test
  void wrongMethodKeepsStatus405() throws Exception {
    // GET 만 되는 주소를 POST 로 부르면 → 405 그대로, 본문만 에러 모양으로
    mockMvc
        .perform(post("/api/v1/error-test/not-found"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", containsString("GET")))
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unexpectedErrorDoesNotLeakInternalInfo() throws Exception {
    // 예상 못 한 에러는 500. 예외에 들어 있던 내부 정보(DB 주소 등)는 응답에 나가면 안 됩니다.
    mockMvc
        .perform(get("/api/v1/error-test/crash"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.error.message").value("잠시 후 다시 시도해 주세요"))
        .andExpect(content().string(not(containsString("secret-db"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
  }

  // 이 테스트에서만 쓰는 가짜 API 입니다. (진짜 서버에는 들어가지 않습니다)
  @RestController
  @RequestMapping("/api/v1/error-test")
  static class FakeController {

    record NameRequest(@NotBlank(message = "이름을 입력해 주세요") String name) {}

    @GetMapping("/not-found")
    public String notFound() {
      throw new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요");
    }

    @GetMapping("/conflict")
    public String conflict() {
      throw new ApiException(ErrorCode.CONFLICT, "이미 같은 이름의 과목이 있어요");
    }

    @PostMapping("/names")
    public String create(@Valid @RequestBody NameRequest request) {
      return request.name();
    }

    @GetMapping("/items/{id}")
    public String item(@PathVariable Long id) {
      return "item " + id;
    }

    @GetMapping("/search")
    public String search(@RequestParam String keyword) {
      return keyword;
    }

    @GetMapping("/crash")
    public String crash() {
      throw new IllegalStateException("jdbc:postgresql://secret-db 접속 실패");
    }
  }
}
