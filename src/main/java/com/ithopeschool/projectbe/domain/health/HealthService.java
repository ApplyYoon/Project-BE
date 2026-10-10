package com.ithopeschool.projectbe.domain.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 서버와 DB 상태를 확인하는 로직입니다.
@Service
@RequiredArgsConstructor
public class HealthService {

  private static final Logger log = LoggerFactory.getLogger(HealthService.class);

  // DB 가 대답하기를 기다리는 최대 시간(초). DB 가 멈춰 있어도 health 가 오래 걸리지 않게 합니다.
  private static final int DB_CHECK_TIMEOUT_SECONDS = 2;

  // DataSource 는 DB 연결을 만들어 주는 창구입니다. Spring 이 application-*.yml 의 DB 설정으로 만들어 줍니다.
  private final DataSource dataSource;

  public HealthResponse check() {
    // 이 메서드가 실행됐다는 것은 서버가 살아 있다는 뜻이라 status 는 항상 "ok" 입니다.
    return new HealthResponse("ok", checkDb());
  }

  // DB 에 연결해 보고 "connected" 또는 "error" 를 돌려줍니다. 예외를 밖으로 던지지 않아서 DB 가 죽어도 서버는 멀쩡합니다.
  private String checkDb() {
    // try-with-resources: 다 쓴 연결은 자동으로 닫아 줍니다.
    try (Connection connection = dataSource.getConnection()) {
      if (connection.isValid(DB_CHECK_TIMEOUT_SECONDS)) {
        return "connected";
      }
      return "error";
    } catch (SQLException e) {
      // 접속 주소·비밀번호가 로그에 남지 않도록 예외 종류만 찍습니다.
      log.warn("DB 연결 확인 실패: {}", e.getClass().getSimpleName());
      return "error";
    }
  }
}
