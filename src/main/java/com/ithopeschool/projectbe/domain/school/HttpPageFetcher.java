package com.ithopeschool.projectbe.domain.school;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 진짜 인터넷에서 페이지를 받아 오는 곳입니다. 자바 기본 HttpClient 를 씁니다. (PageFetcher)
//
// ■ 예의 지키기 (SchoolHomepageCrawler 의 규칙 중 "요청 한 번" 에 해당하는 것)
//   - 우리가 누구인지 밝힙니다: User-Agent 에 서비스 이름과 저장소 주소를 적습니다.
//   - 연결 5초 · 응답 5초가 넘으면 그만둡니다. (학교 서버가 느릴 때 붙잡고 있지 않게)
//   - 쿠키는 학교 홈페이지가 처음 들어올 때 주는 것만 받아 둡니다. (메인 → 게시판으로 갈 때 필요한 학교가 있음)
// ⚠️ 받은 글이나 주소는 로그에 찍지 않습니다. 실패한 "종류" 만 남깁니다.
@Component
public class HttpPageFetcher implements PageFetcher {

  // 우리가 누구인지. 학교 홈페이지 관리자가 로그에서 보고 연락할 수 있게 저장소 주소를 같이 적습니다.
  static final String USER_AGENT =
      "StarflowStudyBot/1.0 (+https://github.com/Tmax-IT-Hope-School/Project-BE)";
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private static final Logger log = LoggerFactory.getLogger(HttpPageFetcher.class);

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          // http → https 처럼 같은 쪽으로 옮겨 가는 것만 따라갑니다. (https → http 로 내려가는 것은 안 따라감)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .cookieHandler(new CookieManager())
          .build();

  @Override
  public Optional<Page> get(URI uri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(READ_TIMEOUT)
              .header("User-Agent", USER_AGENT)
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return Optional.of(new Page(response.statusCode(), response.body()));
    } catch (IOException | IllegalArgumentException e) {
      log.warn("[학교 홈페이지] 페이지를 받지 못했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
