package com.ithopeschool.projectbe.domain.job;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 문지기(JobKeyInterceptor)를 /api/v1/jobs/** 주소에 연결하는 설정입니다.
// "** " 는 그 아래 어떤 주소든 이라는 뜻이라, 앞으로 작업 주소가 늘어나도 자동으로 보호됩니다.
@Configuration
@RequiredArgsConstructor
public class JobWebConfig implements WebMvcConfigurer {

  private final JobKeyInterceptor jobKeyInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(jobKeyInterceptor).addPathPatterns("/api/v1/jobs/**");
  }
}
