package com.ithopeschool.projectbe.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ithopeschool.projectbe.domain.health.HealthController;
import com.ithopeschool.projectbe.domain.health.HealthService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ⚠️ 나중에 문제가 될 수 있는 점: 누군가 환경변수 CORS_ALLOWED_ORIGINS 에 * (모든 주소 허용)를 넣는 경우입니다.
//
// 지금은 WebConfig 의 allowCredentials(true) 때문에 Spring 이 * 를 거부해서, 요청이 에러로 끝납니다.
// 즉 "모든 주소 허용" 이 되지는 않습니다. 이 테스트는 그걸 지킵니다.
// 다만 서버는 켜지고 요청이 올 때마다 500 에러가 나서, 배포 서버에서는 바로 알아차리기 어렵습니다.
//
// 나중에 allowCredentials(true) 를 빼면 * 가 정말 모든 주소를 허락하게 되고 이 테스트가 실패합니다.
// 그때는 * 를 막는 코드를 WebConfig 에 따로 넣어야 합니다. (이슈 #7 완료 조건: * 를 쓰지 않는다)
@WebMvcTest(controllers = HealthController.class, properties = "CORS_ALLOWED_ORIGINS=*")
class WebConfigWildcardTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HealthService healthService;

  @Test
  void wildcardDoesNotAllowEveryOrigin() {
    // assertThatThrownBy: 괄호 안의 코드가 에러를 던져야 통과합니다.
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    get("/api/v1/health").header("Origin", "https://unknown-site.example.com")))
        .isInstanceOf(ServletException.class)
        // 에러 원인이 "allowCredentials 가 true 면 * 를 쓸 수 없다" 인지 확인합니다.
        .hasMessageContaining("allowCredentials");
  }
}
