package com.ithopeschool.projectbe.domain.school;

import java.time.Duration;
import org.springframework.stereotype.Component;

// 학교 홈페이지에 요청을 보내는 사이에 잠깐 쉬는 일을 맡습니다. (예의 지키기, SchoolHomepageCrawler · SchoolDailyJob)
// 한 학교 서버에 요청을 몰아서 보내지 않으려고 쉽니다.
// 테스트에서는 기다리지 않는 가짜로 바꿉니다. (테스트가 몇 초씩 걸리지 않게)
@Component
public class PoliteDelay {

  public void pause(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      // 서버가 꺼지는 중이면 쉬기를 그만두고, "멈추라는 신호" 는 다시 세워 둡니다.
      Thread.currentThread().interrupt();
    }
  }
}
