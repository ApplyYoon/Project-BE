package com.ithopeschool.projectbe.domain.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.call.CallKeyClient;
import com.ithopeschool.projectbe.domain.call.ChatAiClient;
import com.ithopeschool.projectbe.domain.school.NeisClient;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: 서버를 켜지 않고, 가짜 DataSource 로 "DB 가 붙은 경우 / 안 붙은 경우" 를 흉내 냅니다.
// AI · 통화 · NEIS 도 가짜로 "켜짐 / 꺼짐" 을 정해 봅니다.
class HealthServiceTest {

  private final DataSource dataSource = mock(DataSource.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ChatAiClient chatAiClient = mock(ChatAiClient.class);
  private final CallKeyClient callKeyClient = mock(CallKeyClient.class);
  private final NeisClient neisClient = mock(NeisClient.class);
  private final HealthService healthService =
      new HealthService(dataSource, aiClient, chatAiClient, callKeyClient, neisClient);

  @Test
  void dbConnectedWhenConnectionIsValid() throws SQLException {
    Connection connection = mock(Connection.class);
    given(dataSource.getConnection()).willReturn(connection);
    given(connection.isValid(2)).willReturn(true);

    HealthResponse response = healthService.check();

    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.db()).isEqualTo("connected");
    // 다 쓴 연결은 닫혀야 합니다. (안 닫으면 연결이 쌓여서 서버가 멈춥니다)
    verify(connection).close();
  }

  @Test
  void dbErrorWhenConnectionIsNotValid() throws SQLException {
    Connection connection = mock(Connection.class);
    given(dataSource.getConnection()).willReturn(connection);
    given(connection.isValid(2)).willReturn(false);

    HealthResponse response = healthService.check();

    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.db()).isEqualTo("error");
  }

  @Test
  void dbErrorWhenCannotConnect() throws SQLException {
    // DATABASE_URL 이 틀려서 연결이 안 되는 상황: getConnection() 이 예외를 던집니다.
    given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));

    HealthResponse response = healthService.check();

    // 예외가 밖으로 나오지 않고, 서버는 "ok" + DB 는 "error" 로 대답합니다.
    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.db()).isEqualTo("error");
  }

  @Test
  void allOnWhenEveryKeyIsSet() throws SQLException {
    given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));
    given(aiClient.isEnabled()).willReturn(true);
    given(chatAiClient.isEnabled()).willReturn(true);
    given(callKeyClient.isEnabled()).willReturn(true);
    given(neisClient.hasApiKey()).willReturn(true);

    HealthResponse response = healthService.check();

    assertThat(response.ai()).isTrue();
    assertThat(response.call()).isTrue();
    assertThat(response.neis()).isTrue();
  }

  @Test
  void allOffWhenNoKey() throws SQLException {
    // 내 컴퓨터처럼 키가 하나도 없으면 모두 false 입니다. (가짜는 따로 정하지 않으면 false 를 돌려줌)
    given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));

    HealthResponse response = healthService.check();

    assertThat(response.ai()).isFalse();
    assertThat(response.call()).isFalse();
    assertThat(response.neis()).isFalse();
  }

  @Test
  void callOffWhenOnlyTextAiIsOn() throws SQLException {
    // OpenAI 키만 넣고 음성 모델(OPENAI_REALTIME_MODEL)을 안 넣으면: 채팅은 되지만 통화는 꺼져 있습니다.
    given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));
    given(aiClient.isEnabled()).willReturn(true);
    given(chatAiClient.isEnabled()).willReturn(true);
    given(callKeyClient.isEnabled()).willReturn(false);

    HealthResponse response = healthService.check();

    assertThat(response.ai()).isTrue();
    assertThat(response.call()).isFalse();
  }

  @Test
  void aiOffWhenOnlyOneTextAiIsOn() throws SQLException {
    // 글 AI 두 곳 중 하나만 켜졌으면 설정이 어긋난 것이라 false 로 알려 줍니다.
    given(dataSource.getConnection()).willThrow(new SQLException("연결 실패"));
    given(aiClient.isEnabled()).willReturn(true);
    given(chatAiClient.isEnabled()).willReturn(false);

    HealthResponse response = healthService.check();

    assertThat(response.ai()).isFalse();
  }
}
