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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 환경변수 CORS_ALLOWED_ORIGINS 로 허락할 주소 목록을 바꿀 수 있는지 검사합니다.
// 배포할 때는 여기에 FE 배포 주소를 넣습니다. 여러 개는 쉼표(,)로 구분합니다.
// properties 에 적은 값은 이 테스트 동안 환경변수를 넣은 것처럼 쓰입니다. (쉼표 뒤 띄어쓰기도 일부러 넣음)
// project-fe.vercel.app 은 Vercel 배포 주소의 예시입니다. 진짜 배포 주소는 아직 정해지지 않았습니다.
@WebMvcTest(
    controllers = HealthController.class,
    properties = "CORS_ALLOWED_ORIGINS=https://project-fe.vercel.app, https://fe.example.com")
class WebConfigEnvTest {

  private static final String FE_DEPLOY_ORIGIN = "https://project-fe.vercel.app";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthService healthService;

  @Test
  void originFromEnvIsAllowed() throws Exception {
    given(healthService.check())
        .willReturn(new HealthResponse("ok", "connected", false, false, false));

    mockMvc
        .perform(get("/api/v1/health").header("Origin", FE_DEPLOY_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", FE_DEPLOY_ORIGIN));
  }

  @Test
  void secondOriginAfterCommaIsAllowed() throws Exception {
    given(healthService.check())
        .willReturn(new HealthResponse("ok", "connected", false, false, false));

    // 쉼표 뒤에 띄어쓰기가 있어도 두 번째 주소가 제대로 허락됩니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "https://fe.example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://fe.example.com"));
  }

  @Test
  void defaultOriginIsReplaced() throws Exception {
    // 환경변수를 넣으면 기본값(localhost:5173)에 더해지는 게 아니라 통째로 바뀝니다.
    mockMvc
        .perform(get("/api/v1/health").header("Origin", "http://localhost:5173"))
        .andExpect(status().isForbidden());
  }

  @Test
  void vercelPreviewOriginIsRejected() throws Exception {
    // ⚠️ 나중에 문제가 될 수 있는 점: Vercel 미리보기(Preview) 주소는 PR 마다 새로 만들어집니다.
    // 배포 주소와 비슷하게 생겼어도 글자가 하나라도 다르면 다른 주소라서 거절됩니다.
    // 그래서 지금 설정으로는 미리보기 화면에서 BE 를 부를 수 없습니다.
    // 미리보기에서도 부르게 하려면 팀이 방법을 정한 뒤 WebConfig 와 이 테스트를 같이 고칩니다.
    mockMvc
        .perform(
            get("/api/v1/health")
                .header("Origin", "https://project-fe-git-feat-login-team.vercel.app"))
        .andExpect(status().isForbidden());
  }
}
