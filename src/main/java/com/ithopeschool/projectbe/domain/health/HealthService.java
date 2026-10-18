package com.ithopeschool.projectbe.domain.health;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.call.CallKeyClient;
import com.ithopeschool.projectbe.domain.call.ChatAiClient;
import com.ithopeschool.projectbe.domain.school.NeisClient;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 서버와 DB 상태, 그리고 AI · 통화 · NEIS 가 켜져 있는지를 확인하는 로직입니다.
// AI · 통화 · NEIS 칸은 로그인 없이 "배포 서버의 환경변수가 다 들어갔는지" 를 확인하려고 둡니다. (2026-10-18)
//   예) 통화를 Vercel 임시 길(/api/voice-key)에서 BE 길(/realtime/session)로 옮기기 전에 call 이 true 인지 봅니다.
// 켜짐 여부(참/거짓)는 공개돼도 괜찮습니다. 키 값 · 모델 이름은 내보내지 않으니 이것으로 키를 알아낼 수 없습니다.
@Service
@RequiredArgsConstructor
public class HealthService {

  private static final Logger log = LoggerFactory.getLogger(HealthService.class);

  // DB 가 대답하기를 기다리는 최대 시간(초). DB 가 멈춰 있어도 health 가 오래 걸리지 않게 합니다.
  private static final int DB_CHECK_TIMEOUT_SECONDS = 2;

  // DataSource 는 DB 연결을 만들어 주는 창구입니다. Spring 이 application-*.yml 의 DB 설정으로 만들어 줍니다.
  private final DataSource dataSource;
  // 작은 AI 기능들(계획 · 문제 · 알림 문장 등)이 쓰는 글 AI
  private final AiClient aiClient;
  // 상담 채팅이 쓰는 글 AI
  private final ChatAiClient chatAiClient;
  // AI 음성 통화의 임시 키를 만드는 곳
  private final CallKeyClient callKeyClient;
  // 학교 정보(학사일정 · 급식 · 시간표)를 받는 곳
  private final NeisClient neisClient;

  public HealthResponse check() {
    // 이 메서드가 실행됐다는 것은 서버가 살아 있다는 뜻이라 status 는 항상 "ok" 입니다.
    // 글 AI 는 두 곳이 같은 키 · 모델을 쓰므로, 둘 다 켜져 있어야 true 입니다. (하나만 켜졌으면 설정이 어긋난 것)
    boolean ai = aiClient.isEnabled() && chatAiClient.isEnabled();
    return new HealthResponse(
        "ok", checkDb(), ai, callKeyClient.isEnabled(), neisClient.hasApiKey());
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
