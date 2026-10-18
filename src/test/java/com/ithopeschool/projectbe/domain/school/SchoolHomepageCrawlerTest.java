package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 학교 홈페이지 읽기 테스트. 인터넷에 나가지 않고, 분당고(2026-10-18) 모양을 본뜬 작은 가짜 HTML 로 봅니다.
// 가짜 HTML 의 작성자는 실제 사람이 아닌 '홍**' · '테스트' 입니다.
class SchoolHomepageCrawlerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
  private static final String HOME = "https://test-h.example.kr/";
  private static final String MAIN = "https://test-h.example.kr/test-h/main.do";
  private static final String LETTERS =
      "https://test-h.example.kr/test-h/na/ntt/selectNttList.do?mi=20164&bbsId=15687";
  private static final String NOTICES =
      "https://test-h.example.kr/test-h/na/ntt/selectNttList.do?mi=20128&bbsId=15665";

  // 첫 화면: 다른 주소로 보내기만 함 (분당고와 같음)
  private static final String ROOT_HTML =
      """
      <html><head><title>교육지원청</title>
      <script type="text/javascript"> location.href="https://test-h.example.kr/test-h/main.do"; </script>
      </head><body></body></html>
      """;

  // 진짜 첫 화면: 메뉴에 학사일정 · 공지사항 · 가정통신문 (같은 이름이 두 번 나오기도 함)
  private static final String MAIN_HTML =
      """
      <html><body>
        <a href="#?mi=20039">학사일정</a>
        <a href="/test-h/na/ntt/selectNttList.do?mi=20128&bbsId=15665">공지사항</a>
        <a href="https://other-site.example.com/notice">공지사항</a>
        <a href="/test-h/na/ntt/selectNttList.do?mi=20164&bbsId=15687">가정통신문</a>
        <a href="/test-h/na/ntt/selectNttList.do?mi=25822&bbsId=19894">가정통신문</a>
      </body></html>
      """;

  // 가정통신문 목록: 맨 위에 고정된 옛 글 + 최근 글들
  private static final String LETTERS_HTML =
      """
      <html><body><table><tbody>
        <tr><td>공지</td><td class="ta_l"><a href="javascript:" data-id="1736446" class="nttInfoBtn">학교 안내(고정)</a></td>
            <td>테스트</td><td>2024.03.14</td></tr>
        <tr><td>881</td><td class="ta_l"><a href="javascript:" data-id="1962073" class="nttInfoBtn">
              2학기 2차 지필평가 시험 범위 안내
            </a></td><td>홍**</td><td>2026.10.15</td></tr>
        <tr><td>880</td><td class="ta_l"><a href="javascript:" data-id="1960001" class="nttInfoBtn">현장체험학습 안내</a></td>
            <td>홍**</td><td>2026.10.08</td></tr>
        <tr><td>879</td><td class="ta_l"><a href="javascript:" data-id="1950000" class="nttInfoBtn">여름 방학 안내</a></td>
            <td>홍**</td><td>2026.07.10</td></tr>
      </tbody></table></body></html>
      """;

  private static final String NOTICES_HTML =
      """
      <html><body><table><tbody>
        <tr><td>12</td><td><a href="javascript:" data-id="2000001" class="nttInfoBtn">급식 식단 변경 안내</a></td>
            <td>홍**</td><td>2026.10.16</td></tr>
      </tbody></table></body></html>
      """;

  // 주소 → 답(상태 코드, 글). 없는 주소는 "연결 안 됨"
  private final Map<String, PageFetcher.Page> pages = new HashMap<>();
  private final List<String> requested = new ArrayList<>();
  private final PageFetcher fetcher =
      uri -> {
        requested.add(uri.toString());
        return Optional.ofNullable(pages.get(uri.toString()));
      };
  private final PoliteDelay delay = mock(PoliteDelay.class);
  private final SchoolHomepageCrawler crawler = new SchoolHomepageCrawler(fetcher, delay);

  private void schoolSiteWorks() {
    pages.put(
        "https://test-h.example.kr/robots.txt",
        new PageFetcher.Page(200, "User-agent: NaverBot\nDisallow: /upload/\n"));
    pages.put(HOME, new PageFetcher.Page(200, ROOT_HTML));
    pages.put(MAIN, new PageFetcher.Page(200, MAIN_HTML));
    pages.put(LETTERS, new PageFetcher.Page(200, LETTERS_HTML));
    pages.put(NOTICES, new PageFetcher.Page(200, NOTICES_HTML));
  }

  @Test
  @DisplayName("robots → 첫 화면 → 옮겨 간 화면 → 게시판 2개 순서로 읽고, 30일 안의 제목 · 날짜 · 주소만 최근 순서로 돌려준다")
  void readsLettersAndNotices() {
    schoolSiteWorks();

    Optional<List<SchoolNotice>> notices = crawler.fetchNotices("test-h.example.kr/", TODAY);

    assertThat(notices).isPresent();
    assertThat(notices.get())
        .containsExactly(
            new SchoolNotice(
                "급식 식단 변경 안내",
                LocalDate.of(2026, 10, 16),
                "공지사항",
                "https://test-h.example.kr/test-h/na/ntt/selectNttInfo.do?mi=20128&bbsId=15665&nttSn=2000001"),
            new SchoolNotice(
                "2학기 2차 지필평가 시험 범위 안내",
                LocalDate.of(2026, 10, 15),
                "가정통신문",
                "https://test-h.example.kr/test-h/na/ntt/selectNttInfo.do?mi=20164&bbsId=15687&nttSn=1962073"),
            new SchoolNotice(
                "현장체험학습 안내",
                LocalDate.of(2026, 10, 8),
                "가정통신문",
                "https://test-h.example.kr/test-h/na/ntt/selectNttInfo.do?mi=20164&bbsId=15687&nttSn=1960001"));
    // 작성자는 어디에도 담기지 않습니다.
    assertThat(notices.get().toString()).doesNotContain("홍**").doesNotContain("테스트");
    assertThat(requested)
        .containsExactly("https://test-h.example.kr/robots.txt", HOME, MAIN, LETTERS, NOTICES);
    // 요청 사이마다 쉽니다. (robots 다음 4번)
    verify(delay, times(4)).pause(SchoolHomepageCrawler.REQUEST_GAP);
  }

  @Test
  @DisplayName("게시판 하나가 500 이면 그날은 그 학교를 빈 값으로 둔다")
  void failsWhenBoardFails() {
    schoolSiteWorks();
    pages.put(NOTICES, new PageFetcher.Page(500, "error"));

    assertThat(crawler.fetchNotices(HOME, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("robots.txt 가 사이트 전체를 막으면 첫 화면도 열지 않는다")
  void respectsRobots() {
    schoolSiteWorks();
    pages.put(
        "https://test-h.example.kr/robots.txt",
        new PageFetcher.Page(200, "User-agent: *\nDisallow: /\n"));

    assertThat(crawler.fetchNotices(HOME, TODAY)).isEmpty();
    assertThat(requested).containsExactly("https://test-h.example.kr/robots.txt");
  }

  @Test
  @DisplayName("robots.txt 가 404 면 다 열린 것으로 보고, 연결이 안 되면 아무것도 읽지 않는다")
  void robotsMissingOrUnreachable() {
    schoolSiteWorks();
    pages.put("https://test-h.example.kr/robots.txt", new PageFetcher.Page(404, "not found"));
    assertThat(crawler.fetchNotices(HOME, TODAY)).isPresent();

    requested.clear();
    pages.remove("https://test-h.example.kr/robots.txt");
    assertThat(crawler.fetchNotices(HOME, TODAY)).isEmpty();
    assertThat(requested).containsExactly("https://test-h.example.kr/robots.txt");
  }

  @Test
  @DisplayName("게시판 메뉴가 없는 모양의 홈페이지는 건너뛴다")
  void skipsUnknownLayout() {
    pages.put("https://test-h.example.kr/robots.txt", new PageFetcher.Page(404, ""));
    pages.put(
        HOME, new PageFetcher.Page(200, "<html><body><a href='/intro.do'>학교 소개</a></body></html>"));

    assertThat(crawler.fetchNotices(HOME, TODAY)).isEmpty();
  }

  @Test
  @DisplayName("메뉴에서 다른 사이트 링크는 고르지 않고, 같은 이름이면 게시판 목록 주소를 먼저 고른다")
  void findsBoardsOnSameSite() {
    Document page = Jsoup.parse(MAIN_HTML, MAIN);

    List<SchoolHomepageCrawler.Board> boards = SchoolHomepageCrawler.findBoards(page);

    assertThat(boards)
        .extracting(SchoolHomepageCrawler.Board::kind, board -> board.uri().toString())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("가정통신문", LETTERS),
            org.assertj.core.groups.Tuple.tuple("공지사항", NOTICES));
  }

  @Test
  @DisplayName("location.href 와 메타 태그로 옮겨 가는 주소를 찾는다")
  void findsRedirect() {
    assertThat(SchoolHomepageCrawler.redirectTarget(Jsoup.parse(ROOT_HTML, HOME)))
        .contains(URI.create(MAIN));
    assertThat(
            SchoolHomepageCrawler.redirectTarget(
                Jsoup.parse(
                    "<html><head><meta http-equiv='Refresh' content='0; url=/test-h/main.do'></head></html>",
                    HOME)))
        .contains(URI.create(MAIN));
    assertThat(SchoolHomepageCrawler.redirectTarget(Jsoup.parse(MAIN_HTML, MAIN))).isEmpty();
  }

  // 글 12개짜리 가짜 목록. idOf 로 글 번호를 정합니다.
  private static Document boardOf(java.util.function.IntUnaryOperator idOf) {
    StringBuilder html = new StringBuilder("<table>");
    for (int i = 0; i < 12; i++) {
      html.append("<tr><td><a href='javascript:' data-id='")
          .append(idOf.applyAsInt(i))
          .append("' class='nttInfoBtn'>글 ")
          .append(i)
          .append("</a></td><td>홍**</td><td>2026.10.1")
          .append(i % 8)
          .append("</td></tr>");
    }
    html.append("</table>");
    return Jsoup.parse(html.toString(), LETTERS);
  }

  @Test
  @DisplayName("게시판마다 10개까지만 담는다")
  void limitsPerBoard() {
    List<SchoolNotice> notices =
        SchoolHomepageCrawler.parseBoard(boardOf(i -> 100 + i), "가정통신문", TODAY);

    assertThat(notices).hasSize(SchoolHomepageCrawler.MAX_NOTICES_PER_BOARD);
  }

  @Test
  @DisplayName("같은 글 번호(고정 글이 한 번 더 나온 것 등)는 한 번만 담는다")
  void dedupesSameId() {
    List<SchoolNotice> notices =
        SchoolHomepageCrawler.parseBoard(boardOf(i -> 100 + i / 2), "가정통신문", TODAY);

    assertThat(notices).hasSize(6);
    assertThat(notices).extracting(SchoolNotice::url).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("홈페이지 주소는 http(s) 이고 사이트 이름이 있어야 한다")
  void toUri() {
    assertThat(SchoolHomepageCrawler.toUri("bundang-h.goesn.kr"))
        .contains(URI.create("https://bundang-h.goesn.kr"));
    assertThat(SchoolHomepageCrawler.toUri("http://a.example.kr/")).isPresent();
    assertThat(SchoolHomepageCrawler.toUri("ftp://a.example.kr/")).isEmpty();
    assertThat(SchoolHomepageCrawler.toUri("  ")).isEmpty();
    assertThat(SchoolHomepageCrawler.toUri(null)).isEmpty();
  }

  @Test
  @DisplayName("주소가 이상하면 인터넷에 나가지 않는다")
  void badHomepage() {
    PageFetcher strict = mock(PageFetcher.class);
    SchoolHomepageCrawler noNetwork = new SchoolHomepageCrawler(strict, delay);

    assertThat(noNetwork.fetchNotices("", TODAY)).isEmpty();
    verify(strict, times(0)).get(any());
  }
}
