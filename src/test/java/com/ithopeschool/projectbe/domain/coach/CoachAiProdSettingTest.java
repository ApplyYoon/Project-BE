package com.ithopeschool.projectbe.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 배포 설정(application-prod.yml)으로 켰을 때 AI 가 켜지고 꺼지는 조건을 확인합니다.
// @ActiveProfiles("prod") 는 배포 설정으로 켭니다. DB 는 필요 없어서 Controller 만 띄웁니다. (WebConfigProdTest 와 같은 방식)
// ⚠️ 켜졌는지만 봅니다. 진짜 GPT 를 부르지는 않습니다.
class CoachAiProdSettingTest {

  @Nested
  @WebMvcTest(CoachController.class)
  @Import(CoachAiClient.class)
  @ActiveProfiles("prod")
  @TestPropertySource(properties = {"OPENAI_API_KEY=test-key-123", "OPENAI_MODEL=test-model"})
  @DisplayName("배포 서버에 OPENAI_API_KEY 와 OPENAI_MODEL 이 둘 다 있을 때")
  class WithKeyAndModel {

    @MockitoBean private CoachService coachService;
    @Autowired private CoachAiClient coachAiClient;

    @Test
    @DisplayName("AI 가 켜진다")
    void aiIsOn() {
      assertThat(coachAiClient.isEnabled()).isTrue();
    }
  }

  @Nested
  @WebMvcTest(CoachController.class)
  @Import(CoachAiClient.class)
  @ActiveProfiles("prod")
  @TestPropertySource(properties = {"OPENAI_API_KEY=test-key-123"})
  @DisplayName("배포 서버에 키만 있고 모델 이름이 없을 때")
  class WithKeyOnly {

    @MockitoBean private CoachService coachService;
    @Autowired private CoachAiClient coachAiClient;

    @Test
    @DisplayName("AI 가 꺼져 있다")
    void aiIsOff() {
      assertThat(coachAiClient.isEnabled()).isFalse();
    }
  }

  @Nested
  @WebMvcTest(CoachController.class)
  @Import(CoachAiClient.class)
  @ActiveProfiles("prod")
  @DisplayName("배포 서버에 환경변수를 아무것도 넣지 않았을 때")
  class WithNothing {

    @MockitoBean private CoachService coachService;
    @Autowired private CoachAiClient coachAiClient;

    @Test
    @DisplayName("서버는 켜지고, AI 만 꺼져 있다")
    void aiIsOff() {
      assertThat(coachAiClient.isEnabled()).isFalse();
    }
  }
}
