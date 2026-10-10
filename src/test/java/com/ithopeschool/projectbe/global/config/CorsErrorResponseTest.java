package com.ithopeschool.projectbe.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ⚠️ 나중에 문제가 될 수 있는 점: 에러 응답에도 CORS 허락 표시(Access-Control-Allow-Origin)가 붙는지 검사합니다.
//
// FE 의 api.ts 는 에러 응답의 메시지를 읽어서 화면에 보여줍니다.
// 에러 응답에 허락 표시가 빠지면 브라우저가 응답을 감춰서, FE 에는 "서버에 연결하지 못했어요" 라고 잘못 보입니다.
//
// 에러 응답은 MockMvc 가 아니라 진짜 서버를 켜야 제대로 확인할 수 있습니다.
// (진짜 서버는 에러가 나면 /error 로 한 번 더 넘겨서 응답을 만드는데, MockMvc 는 그 과정을 흉내 내지 않습니다)
// RANDOM_PORT: 빈 포트 하나를 골라 진짜 서버를 켭니다. 내 컴퓨터에 켜 둔 8080 서버와 부딪히지 않습니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CorsErrorResponseTest.FakeController.class)
class CorsErrorResponseTest {

  private static final String FE_DEV_ORIGIN = "http://localhost:5173";

  // 테스트 서버가 고른 포트 번호가 여기에 들어옵니다.
  @LocalServerPort private int port;

  // 진짜 서버에 HTTP 요청을 보내는 테스트 도구입니다. (사용법은 MockMvc 와 비슷합니다)
  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void notFoundResponseHasCorsHeader() {
    // 없는 주소 → 404
    client
        .get()
        .uri("/api/v1/no-such-api")
        .header("Origin", FE_DEV_ORIGIN)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", FE_DEV_ORIGIN);
  }

  @Test
  void badRequestResponseHasCorsHeader() {
    // 입력 검사(@Valid)에 걸림 → 400. 앞으로 만들 API 에서 가장 자주 생기는 에러입니다.
    client
        .post()
        .uri("/api/v1/cors-test")
        .header("Origin", FE_DEV_ORIGIN)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"name\":\"\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", FE_DEV_ORIGIN);
  }

  @Test
  void serverErrorResponseHasCorsHeader() {
    // 서버 코드에서 에러가 남 → 500
    client
        .get()
        .uri("/api/v1/cors-test/crash")
        .header("Origin", FE_DEV_ORIGIN)
        .exchange()
        .expectStatus()
        .isEqualTo(500)
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", FE_DEV_ORIGIN);
  }

  @Test
  void methodNotAllowedResponseHasNoCorsHeader() {
    // ⚠️ 지금 동작을 기록하는 테스트입니다. 좋은 동작이라서가 아니라, 알고 있자는 뜻입니다.
    // GET 만 되는 /health 를 POST 로 부르면 405 인데, 이때는 허락 표시가 붙지 않습니다.
    // Spring 이 "어느 API 인지" 찾다가 실패해서, CORS 처리를 하기 전에 멈추기 때문입니다.
    // 그래서 FE 에서는 405 대신 "서버에 연결하지 못했어요" 로 보입니다.
    // FE 가 요청 방법(GET·POST 등)을 잘못 썼을 때만 생기는 일이라 지금은 그대로 둡니다.
    // 이 동작을 고치면 이 테스트도 "허락 표시가 있다" 로 바꿉니다.
    client
        .post()
        .uri("/api/v1/health")
        .header("Origin", FE_DEV_ORIGIN)
        .exchange()
        .expectStatus()
        .isEqualTo(405)
        .expectHeader()
        .doesNotExist("Access-Control-Allow-Origin");
  }

  // 이 테스트에서만 쓰는 가짜 API 입니다. 400·500 을 일부러 만들어 냅니다.
  // 테스트 폴더에 있어서 진짜 서버(배포)에는 들어가지 않습니다.
  @RestController
  @RequestMapping("/api/v1/cors-test")
  static class FakeController {

    record NameRequest(@NotBlank String name) {}

    @PostMapping
    public String echo(@Valid @RequestBody NameRequest request) {
      return request.name();
    }

    @GetMapping("/crash")
    public String crash() {
      throw new IllegalStateException("테스트용으로 일부러 낸 에러");
    }
  }
}
