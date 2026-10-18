package com.ithopeschool.projectbe.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.health.HealthController;
import com.ithopeschool.projectbe.domain.health.HealthResponse;
import com.ithopeschool.projectbe.domain.health.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// CORS 설정(WebConfig)을 검사합니다. 환경변수 CORS_ALLOWED_ORIGINS 를 넣지 않았을 때(기본값)입니다.
// 브라우저는 다른 주소의 서버를 부를 때 요청에 Origin(어느 주소에서 왔는지)을 붙이고,
// 서버가 응답에 Access-Control-Allow-Origin 으로 그 주소를 돌려줘야 응답을 읽습니다.
@WebMvcTest(HealthController.class)
class WebConfigTest {

  // FE 개발 서버 주소입니다. (Project-FE 의 vite.config.ts 에서 5173 으로 정해 둠)
  private static final String FE_DEV_ORIGIN = "http://localhost:5173";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthService healthService;

  @Test
  void feDevServerCanCallHealth() throws Exception {
    given(healthService.check())
        .willReturn(new HealthResponse("ok", "connected", false, false, false));

    mockMvc
        .perform(get("/api/v1/health").header("Origin", FE_DEV_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", FE_DEV_ORIGIN));
  }

  @Test
  void feDevServerPassesPreflight() throws Exception {
    // FE 는 모든 요청에 Content-Type·Authorization 헤더를 붙여서, 브라우저가 진짜 요청 전에
    // OPTIONS 로 "이렇게 보내도 되나요?" 를 먼저 묻습니다. (사전 확인, preflight)
    // 여기서 통과해야 진짜 GET 요청이 나갑니다.
    mockMvc
        .perform(
            options("/api/v1/health")
                .header("Origin", FE_DEV_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "content-type,authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", FE_DEV_ORIGIN));
  }

  @Test
  void unknownOriginIsRejected() throws Exception {
    // 목록에 없는 주소는 403 으로 거절합니다. 모든 주소 허용(*)이 아니라는 뜻입니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "https://unknown-site.example.com"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void otherPortIsRejected() throws Exception {
    // 같은 localhost 라도 포트가 다르면 다른 주소입니다.
    // 5173 을 다른 프로그램이 쓰고 있으면 FE 가 5174 로 켜지는데, 그때는 거절됩니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "http://localhost:5174"))
        .andExpect(status().isForbidden());
  }
}
