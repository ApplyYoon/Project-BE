package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse.Source;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 과목 쌤 웹 검색을 켜고 끄는 것과 출처 검사를 확인합니다. (하루 한도는 2026-10-24 에 없앴습니다)
class TutorWebSearchTest {

  @Test
  @DisplayName("켜져 있으면 검색하고, 꺼져 있으면 검색하지 않는다")
  void enabledOrNot() {
    assertThat(new TutorWebSearch(true).isEnabled()).isTrue();
    assertThat(new TutorWebSearch(false).isEnabled()).isFalse();
  }

  @Test
  @DisplayName("출처는 허락한 사이트의 http(s) 주소만, 같은 주소는 하나만, 3개까지. 제목이 없으면 사이트 주소")
  void cleanSources() {
    List<Source> raw = new ArrayList<>();
    raw.add(new Source("극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"));
    raw.add(new Source("광고", "https://ads.example.com/x"));
    raw.add(new Source("극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"));
    raw.add(new Source("", "https://www.ebsi.co.kr/abc"));
    raw.add(new Source("가짜 위키", "https://wikipedia.org.evil.com/x"));
    raw.add(new Source("파일", "ftp://ncic.re.kr/a"));
    raw.add(null);
    raw.add(new Source("교육과정", "https://ncic.re.kr/a"));
    raw.add(new Source("넷째", "https://moe.go.kr/b"));

    assertThat(TutorWebSearch.cleanSources(raw))
        .containsExactly(
            new Source("극한 - 위키백과", "https://ko.wikipedia.org/wiki/극한"),
            new Source("www.ebsi.co.kr", "https://www.ebsi.co.kr/abc"),
            new Source("교육과정", "https://ncic.re.kr/a"));
    assertThat(TutorWebSearch.cleanSources(null)).isEmpty();
  }

  @Test
  @DisplayName("긴 제목은 60자로 자르고, 너무 긴 주소는 버린다")
  void longTitlesAndUrls() {
    String longTitle = "가".repeat(80);
    String longUrl = "https://ko.wikipedia.org/" + "a".repeat(600);

    List<Source> cleaned =
        TutorWebSearch.cleanSources(
            List.of(
                new Source(longTitle, "https://ko.wikipedia.org/wiki/a"),
                new Source("긴 주소", longUrl)));

    assertThat(cleaned).hasSize(1);
    assertThat(cleaned.get(0).title()).hasSize(TutorWebSearch.MAX_TITLE_LENGTH);
  }
}
