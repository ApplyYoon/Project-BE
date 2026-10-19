package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 과목 쌤 방의 "찾아보기"(웹 검색) 규칙입니다. (2026-10-18 "어느 정도 지식은 너가 찾는 방식으로")
// 과목 쌤 방에서만, OpenAI Responses API 의 내장 도구 web_search 를 붙여 AI 가 필요할 때 스스로 찾게 합니다. (ChatAiClient)
//
// ■ 언제 붙이나: 과목 쌤 방 + 켜져 있음(AI_TUTOR_WEB_SEARCH_ENABLED) + 오늘 검색 한도가 남음
//   위기 표현이 있으면 그 전에 정해 둔 안내로 답하고 AI 를 부르지 않아서 검색도 하지 않습니다. (ChatService)
//   검색할지 말지는 AI 가 정합니다. 대본([찾아보기])에 "확실히 아는 것이면 검색하지 마" 라고 적었습니다.
// ■ 하루 한도: 학생마다 하루 SEARCH_DAILY_LIMIT 번 (실제로 검색한 답만 셈). 서버 메모리에만 세서 서버가 다시 켜지면 0 부터입니다.
//   비용 기록 표(gpt_usages)의 종류(GptUsageKind)를 늘리면 운영 DB 의 허용값을 사람이 고쳐야 해서 메모리로 셉니다.
//   (검색이 붙은 답도 채팅 한 번이라 채팅 하루 한도는 그대로 씁니다)
// ■ 찾는 곳: 교육 · 공공 · 백과사전 사이트만 (ALLOWED_DOMAINS, 고등학생용). OpenAI 의 filters.allowed_domains 로 거릅니다.
// ■ 출처: AI 답에 붙은 링크(url_citation)를 최대 MAX_SOURCES 개, 검사해서 응답의 sources 로 돌려줍니다. (cleanSources)
@Component
public class TutorWebSearch {

  // 고등학생에게 보여 줄 만한 교육 · 공공 · 백과사전 사이트. 하위 주소(ko.wikipedia.org 등)도 포함됩니다.
  // OpenAI 문서: 주소 앞의 https:// 는 빼고 적고, 100개까지.
  static final List<String> ALLOWED_DOMAINS =
      List.of(
          "ncic.re.kr", // 국가교육과정정보센터
          "moe.go.kr", // 교육부
          "kice.re.kr", // 한국교육과정평가원
          "edunet.net", // 에듀넷
          "ebs.co.kr",
          "ebsi.co.kr",
          "kofac.re.kr", // 한국과학창의재단
          "korean.go.kr", // 국립국어원
          "kosis.kr", // 국가통계포털
          "encykorea.aks.ac.kr", // 한국민족문화대백과사전
          "wikipedia.org",
          "britannica.com",
          "khanacademy.org");

  // 응답에 넣는 출처 수
  static final int MAX_SOURCES = 3;
  // 출처 제목 글자 수 (넘으면 자름)
  static final int MAX_TITLE_LENGTH = 60;
  // 출처 주소 글자 수 (넘으면 버림)
  static final int MAX_URL_LENGTH = 500;

  private final boolean enabled;
  private final int dailyLimit;
  private final Clock clock;
  // 학생 번호 → (그날, 검색한 횟수)
  private final Map<Long, Count> countByUser = new HashMap<>();

  public TutorWebSearch(
      @Value("${app.ai.tutor-web-search-enabled:true}") boolean enabled,
      @Value("${app.ai.tutor-web-search-daily-limit:5}") int dailyLimit,
      Clock clock) {
    this.enabled = enabled;
    this.dailyLimit = dailyLimit;
    this.clock = clock;
  }

  private record Count(LocalDate day, int used) {}

  // 이번 답에 검색 도구를 붙여도 되는지. 꺼져 있거나 오늘 한도를 다 썼으면 false
  public synchronized boolean canSearch(Long userId) {
    return enabled && usedToday(userId) < dailyLimit;
  }

  // AI 가 실제로 검색한 답이면 한 번 셉니다.
  public synchronized void recordSearch(Long userId) {
    countByUser.put(userId, new Count(today(), usedToday(userId) + 1));
  }

  private int usedToday(Long userId) {
    Count count = countByUser.get(userId);
    return count == null || !count.day().equals(today()) ? 0 : count.used();
  }

  private LocalDate today() {
    return DayRule.dateOf(clock.instant());
  }

  // AI 답에 붙은 링크를 검사합니다.
  //   - http · https 주소만, 500자 넘으면 버림, 허락한 사이트(ALLOWED_DOMAINS)가 아니면 버림
  //   - 같은 주소는 하나만, MAX_SOURCES 개까지
  //   - 제목이 비었으면 사이트 주소(host)로, 60자 넘으면 자름, 깎아내리는 말이 있으면 버림
  //
  //   [("수열 - 위키백과", "https://ko.wikipedia.org/wiki/수열"), ("광고", "http://ads.example.com"),
  //    ("수열 - 위키백과", "https://ko.wikipedia.org/wiki/수열")]
  //     → [("수열 - 위키백과", "https://ko.wikipedia.org/wiki/수열")]
  public static List<ChatReplyResponse.Source> cleanSources(List<ChatReplyResponse.Source> raw) {
    List<ChatReplyResponse.Source> sources = new ArrayList<>();
    if (raw == null) {
      return sources;
    }
    List<String> seenUrls = new ArrayList<>();
    for (ChatReplyResponse.Source source : raw) {
      if (sources.size() == MAX_SOURCES) {
        break;
      }
      if (source == null || source.url() == null) {
        continue;
      }
      String url = source.url().trim();
      String host = allowedHostOf(url);
      if (host == null || url.length() > MAX_URL_LENGTH || seenUrls.contains(url)) {
        continue;
      }
      String title = source.title() == null ? "" : source.title().trim();
      if (title.isEmpty()) {
        title = host;
      }
      if (title.length() > MAX_TITLE_LENGTH) {
        title = title.substring(0, MAX_TITLE_LENGTH);
      }
      if (ToneGuide.hasBannedExpression(title)) {
        continue;
      }
      seenUrls.add(url);
      sources.add(new ChatReplyResponse.Source(title, url));
    }
    return sources;
  }

  // 허락한 사이트의 http · https 주소면 사이트 주소(host), 아니면 null
  static String allowedHostOf(String url) {
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (host == null || !("https".equals(scheme) || "http".equals(scheme))) {
        return null;
      }
      String lower = host.toLowerCase();
      for (String domain : ALLOWED_DOMAINS) {
        if (lower.equals(domain) || lower.endsWith("." + domain)) {
          return lower;
        }
      }
      return null;
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
