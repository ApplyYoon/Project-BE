package com.ithopeschool.projectbe.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExceptionPrivacyTest {
  @Test
  void unexpectedExceptionDoesNotExposeMessageOrCause() {
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    logger.addAppender(captured);
    try {
      String privateText = "테스트용 마음 기록 원문";
      String connection = "jdbc:postgresql://example.invalid/private";
      var response =
          new GlobalExceptionHandler()
              .handleOthers(
                  new IllegalStateException(privateText, new RuntimeException(connection)));
      assertThat(response.getStatusCode().value()).isEqualTo(500);
      assertThat(response.getBody().error().message()).doesNotContain(privateText, connection);
      assertThat(captured.list).hasSize(1);
      assertThat(captured.list.getFirst().getFormattedMessage())
          .contains("IllegalStateException")
          .doesNotContain(privateText, connection);
      // Throwable를 로거에 넘기면 중첩 원인과 메시지가 다시 출력되므로 함께 확인합니다.
      assertThat(captured.list.getFirst().getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(captured);
      captured.stop();
    }
  }
}
