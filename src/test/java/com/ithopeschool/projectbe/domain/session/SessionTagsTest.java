package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.session.SessionTags.CauseSource;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.PauseTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.PlayTag;
import com.ithopeschool.projectbe.domain.session.SessionTags.ShortfallReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.WithWhat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 타이머 태그의 글자와 내부/외부 분류가 FE 의 src/lib/sessionTags.ts 와 같은지 확인합니다. (이슈 #224)
// FE 쪽 목록을 바꾸면 이 테스트의 기대값도 같이 바꿔야 합니다.
class SessionTagsTest {

  @Test
  @DisplayName("일시정지 이유 7개의 글자는 FE PAUSE_TAGS 와 같은 순서·같은 값이다")
  void pauseTagValues() {
    List<String> values = new ArrayList<>();
    for (PauseTag tag : PauseTag.values()) {
      values.add(tag.toValue());
    }
    assertThat(values)
        .containsExactly("toilet", "snack", "phone", "mindWander", "stuck", "external", "other");
  }

  @Test
  @DisplayName("일시정지 이유의 분류: 폰·딴생각·막힘은 내 쪽, 외부 방해는 바깥, 화장실·물은 기본, 기타는 모름")
  void pauseSources() {
    // FE getPauseSource 의 예시와 같습니다.
    assertThat(PauseTag.PHONE.source()).isEqualTo(CauseSource.INTERNAL);
    assertThat(PauseTag.EXTERNAL.source()).isEqualTo(CauseSource.EXTERNAL);
    assertThat(PauseTag.TOILET.source()).isEqualTo(CauseSource.BASIC);
    assertThat(PauseTag.OTHER.source()).isEqualTo(CauseSource.UNKNOWN);
    assertThat(PauseTag.SNACK.source()).isEqualTo(CauseSource.BASIC);
    assertThat(PauseTag.MIND_WANDER.source()).isEqualTo(CauseSource.INTERNAL);
    assertThat(PauseTag.STUCK.source()).isEqualTo(CauseSource.INTERNAL);
    assertThat(CauseSource.UNKNOWN.toValue()).isEqualTo("unknown");
  }

  @Test
  @DisplayName("잘 된 이유: 집중·계획은 내 덕, 컨디션·환경·도움·운은 밖의 덕")
  void goodReasonSources() {
    assertThat(GoodReason.FOCUS.source()).isEqualTo(CauseSource.INTERNAL);
    assertThat(GoodReason.GOOD_PLAN.source()).isEqualTo(CauseSource.INTERNAL);
    assertThat(GoodReason.CONDITION.source()).isEqualTo(CauseSource.EXTERNAL);
    assertThat(GoodReason.ENVIRONMENT.source()).isEqualTo(CauseSource.EXTERNAL);
    assertThat(GoodReason.HELPED.source()).isEqualTo(CauseSource.EXTERNAL);
    assertThat(GoodReason.LUCK.source()).isEqualTo(CauseSource.EXTERNAL);
  }

  @Test
  @DisplayName("FE 글자 ↔ 값: 정해진 글자만 받고, 모르는 글자·대문자는 null")
  void fromValue() {
    assertThat(PauseTag.fromValue("mindWander")).isEqualTo(PauseTag.MIND_WANDER);
    assertThat(PauseTag.fromValue("MIND_WANDER")).isNull();
    assertThat(ShortfallReason.fromValue("tooMuch")).isEqualTo(ShortfallReason.TOO_MUCH);
    assertThat(ShortfallReason.fromValue("lazy")).isNull();
    assertThat(GoodReason.fromValue("goodPlan")).isEqualTo(GoodReason.GOOD_PLAN);
    assertThat(WithWhat.fromValue("video")).isEqualTo(WithWhat.VIDEO);
    assertThat(WithWhat.fromValue("VIDEO")).isNull();
    assertThat(PlayTag.fromValue("webtoon")).isEqualTo(PlayTag.WEBTOON);
    assertThat(SessionMode.fromValue("selfReport")).isEqualTo(SessionMode.SELF_REPORT);
    assertThat(SessionMode.SELF_REPORT.toValue()).isEqualTo("selfReport");
  }
}
