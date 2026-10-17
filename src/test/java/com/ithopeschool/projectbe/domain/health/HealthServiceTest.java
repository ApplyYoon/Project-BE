package com.ithopeschool.projectbe.domain.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

// Service 단위 테스트: 서버를 켜지 않고, 가짜 DataSource 로 "DB 가 붙은 경우 / 안 붙은 경우" 를 흉내 냅니다.
class HealthServiceTest {

  private final DataSource dataSource = mock(DataSource.class);
  private final HealthService healthService = new HealthService(dataSource);

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
}
