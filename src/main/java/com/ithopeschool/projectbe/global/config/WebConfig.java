package com.ithopeschool.projectbe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS 설정입니다. 브라우저는 주소가 다른 서버를 부를 때 서버의 허락이 있어야 응답을 읽을 수 있습니다.
@Configuration
public class WebConfig implements WebMvcConfigurer {

  // 허락할 FE 주소 목록입니다. 환경변수 CORS_ALLOWED_ORIGINS 로 받습니다. (기본값은 application.yml)
  @Value("${app.cors.allowed-origins}")
  private String[] allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowCredentials(true);
  }
}
