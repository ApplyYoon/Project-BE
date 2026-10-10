package com.ithopeschool.projectbe.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 시계(Clock) 설정입니다. "지금 몇 시인지" 를 코드 곳곳에서 System 으로 바로 묻지 않고, 이 시계에게 묻습니다.
// 이렇게 하면 테스트에서 시계를 원하는 시각으로 고정할 수 있습니다. (예: 새벽 3시 59분일 때를 흉내 내기)
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    // UTC 기준의 진짜 시계입니다. 순간(Instant)은 시간대와 상관없는 값이고,
    // 날짜로 바꿀 때만 한국 시간(DayRule)을 씁니다.
    return Clock.systemUTC();
  }
}
