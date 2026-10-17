package com.ithopeschool.projectbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 서버의 시작점입니다. ./gradlew bootRun 을 하면 여기 main 이 실행됩니다.
@SpringBootApplication
public class ProjectBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProjectBeApplication.class, args);
  }
}
