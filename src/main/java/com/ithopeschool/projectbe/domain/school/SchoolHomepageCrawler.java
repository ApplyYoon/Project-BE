package com.ithopeschool.projectbe.domain.school;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

// 학교 홈페이지에서 가정통신문 · 공지사항의 최근 글 제목 · 날짜를 읽어 오는 곳입니다. (2026-10-18 멘토 요청 "NEIS + 직접 크롤링")
// 홈페이지 주소는 NEIS 학교기본정보의 HMPG_ADRES 에서 얻습니다. (NeisClient.findHomepage)
//
// ■ 읽는 순서 (분당고 2026-10-18 모양 기준)
//   1. robots.txt 를 읽습니다. 막힌 경로면 가지 않습니다. (RobotsRules)
//   2. 홈페이지 첫 화면을 열고 '가정통신문' · '공지사항' 이라는 글자의 메뉴 링크를 찾습니다. (학교마다 주소가 다름)
//      첫 화면이 location.href="…/main.do" 로 다른 주소로 보내기만 하면, 그 주소를 한 번 더 엽니다.
//   3. 게시판 목록 첫 쪽에서 글 링크(a.nttInfoBtn)의 제목 · 글 번호와 같은 줄의 날짜(2026.10.15)만 읽습니다.
//      30일이 넘은 글과 맨 위에 늘 고정된 옛 글은 날짜로 걸러집니다. 게시판마다 10개까지.
//   경기도 등의 "학교 홈페이지 표준" 모양(…/na/ntt/selectNttList.do)이 아니면 못 읽고 빈 값을 돌려줍니다.
//
// ■ 예의 지키기 (자동으로 남의 사이트를 읽을 때 지키는 약속)
//   - robots.txt 에서 막은 곳은 가지 않습니다. 못 읽으면 그날은 아무것도 안 읽습니다.
//   - 요청 사이에 1초씩 쉽니다. (PoliteDelay) 한 학교에 많아야 5번(robots · 첫 화면 · 옮겨 간 화면 · 게시판 2개)
//   - 우리 이름과 저장소 주소를 밝히고, 5초 안에 답이 없으면 그만둡니다. (HttpPageFetcher)
//   - 한 번이라도 실패(4xx · 5xx · 시간 초과)하면 그 학교는 그날 그만 읽습니다. (빈 값 → SchoolNoticeService 가 기억)
//   - 다른 사이트로 가는 링크는 따라가지 않습니다. (홈페이지와 같은 주소의 페이지만)
//   - 작성자 · 본문 · 첨부 파일은 읽지 않습니다. 목록의 제목 · 날짜 · 글 번호만 씁니다.
@Component
@RequiredArgsConstructor
public class SchoolHomepageCrawler {

  public static final String KIND_LETTER = "가정통신문";
  public static final String KIND_NOTICE = "공지사항";
  // 읽을 게시판 (메뉴 글자)
  static final List<String> BOARD_NAMES = List.of(KIND_LETTER, KIND_NOTICE);
  // 요청 사이에 쉬는 시간
  static final Duration REQUEST_GAP = Duration.ofSeconds(1);
  // 게시판마다 가져오는 글 수
  static final int MAX_NOTICES_PER_BOARD = 10;
  // 이보다 오래된 글은 버립니다. (일)
  static final int MAX_AGE_DAYS = 30;
  // 제목이 이보다 길면 자릅니다. (이상하게 긴 글이 들어오지 않게)
  static final int MAX_TITLE_LENGTH = 100;

  // 목록의 날짜 칸. '2026.10.15' · '2026-10-15' · '2026/10/15'
  private static final Pattern DATE =
      Pattern.compile("(20\\d{2})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
  // 첫 화면이 다른 주소로 보내는 글: location.href="https://…/main.do";
  private static final Pattern JS_REDIRECT =
      Pattern.compile("location\\.href\\s*=\\s*[\"']([^\"']+)[\"']");
  // 메타 태그로 보내는 글: <meta http-equiv="refresh" content="0; url=/main.do">
  private static final Pattern META_REDIRECT =
      Pattern.compile("url\\s*=\\s*['\"]?([^'\";]+)", Pattern.CASE_INSENSITIVE);

  private final PageFetcher fetcher;
  private final PoliteDelay delay;

  // 학교 하나의 최근 글들(날짜가 최근인 순서). 못 읽었으면 빈 값입니다.
  public Optional<List<SchoolNotice>> fetchNotices(String homepage, LocalDate today) {
    Optional<URI> start = toUri(homepage);
    if (start.isEmpty()) {
      return Optional.empty();
    }
    RobotsRules robots = readRobots(start.get());

    // 1) 첫 화면에서 게시판 메뉴 찾기 (없으면 옮겨 가는 주소를 한 번 더)
    Optional<Document> page = open(start.get(), robots);
    if (page.isEmpty()) {
      return Optional.empty();
    }
    List<Board> boards = findBoards(page.get());
    if (boards.isEmpty()) {
      Optional<URI> next =
          redirectTarget(page.get()).filter(target -> sameSite(start.get(), target));
      if (next.isEmpty()) {
        return Optional.empty();
      }
      page = open(next.get(), robots);
      if (page.isEmpty()) {
        return Optional.empty();
      }
      boards = findBoards(page.get());
    }
    if (boards.isEmpty()) {
      return Optional.empty();
    }

    // 2) 게시판마다 목록 첫 쪽 읽기. 하나라도 실패하면 그날은 그만둡니다.
    List<SchoolNotice> notices = new ArrayList<>();
    for (Board board : boards) {
      if (!sameSite(start.get(), board.uri())) {
        continue;
      }
      Optional<Document> list = open(board.uri(), robots);
      if (list.isEmpty()) {
        return Optional.empty();
      }
      notices.addAll(parseBoard(list.get(), board.kind(), today));
    }
    notices.sort(Comparator.comparing(SchoolNotice::date).reversed());
    return Optional.of(notices);
  }

  // robots.txt 읽기. 있으면 규칙대로, 없으면(4xx) 모두 허용, 못 읽으면(5xx · 연결 안 됨) 모두 막음.
  private RobotsRules readRobots(URI site) {
    Optional<PageFetcher.Page> page = fetcher.get(site.resolve("/robots.txt"));
    if (page.isEmpty()) {
      return RobotsRules.denyAll();
    }
    int status = page.get().status();
    if (status >= 200 && status < 300) {
      return RobotsRules.parse(page.get().body() == null ? "" : page.get().body());
    }
    if (status >= 400 && status < 500) {
      return RobotsRules.allowAll();
    }
    return RobotsRules.denyAll();
  }

  // 페이지 하나 열기. robots 가 막았거나, 실패(연결 안 됨 · 2xx 가 아님)하면 빈 값입니다. 열기 전에 1초 쉽니다.
  private Optional<Document> open(URI uri, RobotsRules robots) {
    if (!robots.allows(pathOf(uri))) {
      return Optional.empty();
    }
    delay.pause(REQUEST_GAP);
    Optional<PageFetcher.Page> page = fetcher.get(uri);
    if (page.isEmpty() || page.get().status() < 200 || page.get().status() >= 300) {
      return Optional.empty();
    }
    String body = page.get().body() == null ? "" : page.get().body();
    return Optional.of(Jsoup.parse(body, uri.toString()));
  }

  // 메뉴에서 게시판 링크 찾기. 게시판 이름마다 첫 번째 것 하나만.
  //   <a href="/bundang-h/na/ntt/selectNttList.do?mi=20164&bbsId=15687">가정통신문</a> → 가정통신문 게시판
  // 같은 이름이 여러 개면 게시판 목록 주소(selectNttList)인 것을 먼저 고릅니다. 다른 사이트로 가는 링크는 고르지 않습니다.
  static List<Board> findBoards(Document page) {
    Optional<URI> here = toUri(page.location());
    List<Board> boards = new ArrayList<>();
    for (String name : BOARD_NAMES) {
      Optional<URI> found = Optional.empty();
      for (Element link : page.select("a[href]")) {
        if (!link.text().trim().equals(name)) {
          continue;
        }
        Optional<URI> uri = linkUri(link);
        if (uri.isEmpty() || here.isEmpty() || !sameSite(here.get(), uri.get())) {
          continue;
        }
        if (uri.get().toString().contains("selectNttList")) {
          found = uri;
          break;
        }
        if (found.isEmpty()) {
          found = uri;
        }
      }
      found.ifPresent(uri -> boards.add(new Board(name, uri)));
    }
    return boards;
  }

  // 첫 화면이 다른 주소로 보내기만 하는 경우 그 주소. (메타 태그 또는 location.href)
  static Optional<URI> redirectTarget(Document page) {
    Element meta = page.selectFirst("meta[http-equiv~=(?i)refresh]");
    if (meta != null) {
      Matcher matcher = META_REDIRECT.matcher(meta.attr("content"));
      if (matcher.find()) {
        return resolve(page, matcher.group(1).trim());
      }
    }
    for (Element script : page.select("script")) {
      Matcher matcher = JS_REDIRECT.matcher(script.data());
      if (matcher.find()) {
        return resolve(page, matcher.group(1).trim());
      }
    }
    return Optional.empty();
  }

  // 게시판 목록 → 글들. 제목 · 날짜 · 글 번호만 읽고, 같은 줄의 작성자 칸은 읽지 않습니다.
  //   <tr><td>881</td><td><a href="javascript:" data-id="1962073" class="nttInfoBtn">설문 안내</a></td>
  //       <td>김**</td><td>2026.10.15</td></tr>
  //   → SchoolNotice("설문 안내", 2026-10-15, "가정통신문", "…/selectNttInfo.do?mi=…&bbsId=…&nttSn=1962073")
  static List<SchoolNotice> parseBoard(Document board, String kind, LocalDate today) {
    List<SchoolNotice> notices = new ArrayList<>();
    List<String> seenIds = new ArrayList<>();
    for (Element link : board.select("a.nttInfoBtn, a[href*=selectNttInfo]")) {
      if (notices.size() >= MAX_NOTICES_PER_BOARD) {
        break;
      }
      String title = link.text().replaceAll("\\s+", " ").trim();
      Element row = link.closest("tr");
      if (title.isEmpty() || row == null) {
        continue;
      }
      Optional<LocalDate> date = dateOf(row);
      if (date.isEmpty()
          || date.get().isAfter(today)
          || date.get().isBefore(today.minusDays(MAX_AGE_DAYS))) {
        continue;
      }
      String id = idOf(link);
      if (!id.isEmpty() && seenIds.contains(id)) {
        continue;
      }
      seenIds.add(id);
      notices.add(
          new SchoolNotice(cut(title), date.get(), kind, detailUrl(board, link, id).orElse(null)));
    }
    return notices;
  }

  // 한 줄(tr)의 칸(td) 중 날짜 모양인 첫 칸
  private static Optional<LocalDate> dateOf(Element row) {
    for (Element cell : row.select("td")) {
      Matcher matcher = DATE.matcher(cell.text());
      if (!matcher.find()) {
        continue;
      }
      try {
        return Optional.of(
            LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))));
      } catch (RuntimeException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  // 글 번호: data-id="1962073" 또는 주소의 nttSn=1962073
  private static String idOf(Element link) {
    String id = link.attr("data-id").trim();
    if (!id.isEmpty()) {
      return id;
    }
    Matcher matcher = Pattern.compile("nttSn=(\\d+)").matcher(link.attr("href"));
    return matcher.find() ? matcher.group(1) : "";
  }

  // 글을 여는 주소. 링크에 진짜 주소가 있으면 그것을, 'javascript:' 면 목록 주소로 만듭니다.
  //   …/selectNttList.do?mi=20164&bbsId=15687  →
  // …/selectNttInfo.do?mi=20164&bbsId=15687&nttSn=1962073
  private static Optional<String> detailUrl(Document board, Element link, String id) {
    Optional<URI> direct = linkUri(link);
    if (direct.isPresent()) {
      return Optional.of(direct.get().toString());
    }
    String listUrl = board.location();
    if (id.isEmpty() || !listUrl.contains("selectNttList.do")) {
      return Optional.empty();
    }
    return Optional.of(listUrl.replace("selectNttList.do", "selectNttInfo.do") + "&nttSn=" + id);
  }

  // 링크의 진짜 주소(http · https). '#' · 'javascript:' 는 빈 값입니다.
  private static Optional<URI> linkUri(Element link) {
    String href = link.attr("href").trim();
    if (href.isEmpty() || href.startsWith("#") || href.toLowerCase().startsWith("javascript")) {
      return Optional.empty();
    }
    return toUri(link.absUrl("href"));
  }

  private static Optional<URI> resolve(Document page, String target) {
    try {
      return toUri(URI.create(page.location()).resolve(target).toString());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  // 'bundang-h.goesn.kr' 처럼 앞이 빠진 주소는 https:// 를 붙입니다. http · https 이고 사이트 이름이 있어야 합니다.
  static Optional<URI> toUri(String address) {
    if (address == null || address.isBlank()) {
      return Optional.empty();
    }
    String text = address.trim();
    if (text.matches("(?i)^[a-z][a-z0-9+.-]*://.*") && !text.matches("(?i)^https?://.*")) {
      // ftp:// 처럼 다른 방식의 주소는 읽지 않습니다.
      return Optional.empty();
    }
    if (!text.matches("(?i)^https?://.*")) {
      text = "https://" + text;
    }
    try {
      URI uri = URI.create(text);
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        return Optional.empty();
      }
      return Optional.of(uri);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  // 같은 사이트인지 (주소의 사이트 이름이 같은지)
  private static boolean sameSite(URI home, URI target) {
    return target.getHost() != null && target.getHost().equalsIgnoreCase(home.getHost());
  }

  // robots 규칙에 넣을 경로: '/bundang-h/na/ntt/selectNttList.do?mi=20164&bbsId=15687'
  private static String pathOf(URI uri) {
    String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
  }

  private static String cut(String text) {
    return text.length() <= MAX_TITLE_LENGTH ? text : text.substring(0, MAX_TITLE_LENGTH);
  }

  // 찾은 게시판 하나 (이름 + 목록 주소)
  record Board(String kind, URI uri) {}
}
