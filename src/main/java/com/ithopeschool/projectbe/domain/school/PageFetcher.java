package com.ithopeschool.projectbe.domain.school;

import java.net.URI;
import java.util.Optional;

// 인터넷에서 페이지 하나를 받아 오는 일입니다. (SchoolHomepageCrawler)
// 진짜는 HttpPageFetcher 이고, 테스트에서는 인터넷에 나가지 않는 가짜로 바꿉니다.
public interface PageFetcher {

  // 페이지 하나를 받습니다. 연결 자체가 안 되면(시간 초과 · 주소 없음) 빈 값입니다.
  // 상태 코드가 404 · 500 이어도 빈 값이 아니라 그 코드가 담긴 Page 를 돌려줍니다. (robots.txt 판단에 필요)
  Optional<Page> get(URI uri);

  // 받은 결과. status 는 HTTP 상태 코드(200 · 404 등), body 는 받은 글입니다.
  record Page(int status, String body) {}
}
