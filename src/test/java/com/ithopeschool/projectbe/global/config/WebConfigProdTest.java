package com.ithopeschool.projectbe.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.health.HealthController;
import com.ithopeschool.projectbe.domain.health.HealthResponse;
import com.ithopeschool.projectbe.domain.health.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ⚠️ 나중에 문제가 될 수 있는 점: 배포 서버에 환경변수 CORS_ALLOWED_ORIGINS 를 깜빡하고 안 넣은 경우입니다.
//
// @ActiveProfiles("prod") 는 배포 설정(application-prod.yml)으로 켭니다. 환경변수는 넣지 않았습니다.
// 이때 서버는 아무 에러 없이 켜지고, 기본값인 개발 주소(localhost:5173)만 허락합니다.
// 그래서 FE 배포 주소에서 오는 요청은 전부 막힙니다. 배포할 때 이 환경변수를 꼭 넣어야 하는 이유입니다. (배포: 이슈 #8)
//
// 나중에 "배포 설정에서는 환경변수가 없으면 서버가 켜지지 않게" 바꾸면 이 테스트도 같이 고칩니다.
@WebMvcTest(HealthController.class)
@ActiveProfiles("prod")
class WebConfigProdTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthService healthService;

  @Test
  void prodWithoutEnvAllowsOnlyDevOrigin() throws Exception {
    given(healthService.check())
        .willReturn(new HealthResponse("ok", "connected", false, false, false));

    // FE 배포 주소(예시)는 막힙니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "https://project-fe.vercel.app"))
        .andExpect(status().isForbidden());

    // 배포 서버인데도 개발 주소는 통과합니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }
}
