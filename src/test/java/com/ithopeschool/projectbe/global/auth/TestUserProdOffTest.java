package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.auth.LoginTokenRepository;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 서버 전체를 켜서 "테스트 사용자와 테스트 토큰은 내 컴퓨터(local)에서만 통한다" 를 확인합니다. (이슈 #20)
//
//   배포 설정(prod)  →  테스트 토큰 값이 비어 있고, 테스트 사용자를 만드는 코드가 없고, 테스트 토큰을 보내면 401
//   내 컴퓨터(local) →  같은 토큰으로 테스트 사용자가 로그인한 것으로 본다 (비교용)
//
// ⚠️ 설정 파일과 코드를 확인하는 테스트입니다. 실제 배포 서버(Render)에 넣은 환경변수는 이 테스트로 볼 수 없습니다.
class TestUserProdOffTest {

  // 내 컴퓨터용 테스트 토큰입니다. (application-local.yml 의 app.auth.test-token 과 같은 값)
  private static final String LOCAL_TEST_TOKEN = "local-test-token";

  // 로그인이 필요한 API 하나를 테스트 토큰으로 불러 봅니다.
  // "어제 한 줄" 을 고른 이유: DB 를 쓰지 않는 API 라서, 운영 DB 가 없는 테스트에서도 토큰 검사 결과만 볼 수 있습니다.
  private static ResultActions callWithLocalTestToken(MockMvc mockMvc) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coach/daily")
            .header("Authorization", "Bearer " + LOCAL_TEST_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"numbers\":[{\"label\":\"어제 순공시간\",\"value\":\"1시간\",\"basis\":\"타이머 기록\"}],"
                    + "\"maxCareLevel\":2,\"maxHarshness\":3}"));
  }

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("prod")
  // 배포 설정은 DB 주소를 환경변수에서 받습니다. 테스트에서는 아무 데도 닿지 않는 값을 넣습니다.
  // (배포 설정은 서버가 켜질 때 DB 에 접속하지 않아서, 주소가 틀려도 켜집니다. 이슈 #4)
  @TestPropertySource(
      properties = {
        "DATABASE_URL=jdbc:postgresql://localhost:1/not-used",
        "DATABASE_USERNAME=not-used",
        "DATABASE_PASSWORD=not-used"
      })
  @DisplayName("배포 설정(prod)으로 켰을 때")
  class OnProd {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    // DB 를 읽는 창구는 가짜로 바꿔 끼웁니다. 진짜 운영 DB 에 접속하지 않고, 어떤 메서드가 불렸는지도 볼 수 있습니다.
    @MockitoBean private LoginTokenRepository loginTokenRepository;
    @MockitoBean private UserRepository userRepository;

    @Value("${app.auth.test-token:}")
    private String testToken;

    @Test
    @DisplayName("테스트 토큰 값이 비어 있다")
    void testTokenIsBlank() {
      assertThat(testToken).isBlank();
    }

    @Test
    @DisplayName("테스트 사용자를 만드는 코드(LocalTestUserInitializer)가 만들어지지 않는다")
    void testUserInitializerIsNotCreated() {
      assertThat(context.getBeansOfType(LocalTestUserInitializer.class)).isEmpty();
      // 서버가 켜지는 동안 사용자를 세거나 저장한 적이 없습니다.
      verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("내 컴퓨터용 테스트 토큰을 보내도 401 이다")
    void localTestTokenIsRejected() throws Exception {
      callWithLocalTestToken(mockMvc)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
          .andExpect(jsonPath("$.error.message").value("다시 로그인해 주세요"));

      // 테스트 토큰으로 알아본 것이 아니라, 보통 토큰처럼 DB 에서 찾아 보고 없어서 거절한 것입니다.
      verify(loginTokenRepository).findByTokenHash(anyString());
    }
  }

  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  @ActiveProfiles("local")
  @DisplayName("내 컴퓨터(local)에서 켰을 때 — 위와 견주어 보는 용도")
  class OnLocal {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("테스트 사용자를 만드는 코드가 있고, 같은 토큰으로 테스트 사용자가 로그인한 것으로 본다")
    void localTestTokenWorks() throws Exception {
      assertThat(context.getBeansOfType(LocalTestUserInitializer.class)).hasSize(1);

      // 배포 설정에서 401 이던 것과 똑같은 요청입니다.
      callWithLocalTestToken(mockMvc).andExpect(status().isOk());
    }
  }
}
