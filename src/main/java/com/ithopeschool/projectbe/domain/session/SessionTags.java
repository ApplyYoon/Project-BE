package com.ithopeschool.projectbe.domain.session;

import java.util.Locale;

// 타이머 팝업에서 1탭으로 고르는 태그 목록을 한 곳에 모아 둡니다. (최종 기획서 「4.4 타이머」, 이슈 #224)
// FE 의 src/lib/sessionTags.ts 와 같은 값 · 같은 분류입니다. 한쪽을 고치면 다른 쪽도 같이 고칩니다.
//
// DB 에는 이름 그대로(MIND_WANDER) 저장하고, FE 와 주고받을 때는 FE 글자(mindWander)로 바꿉니다. (MissReason 과 같은 방식)
//   PauseTag.MIND_WANDER.toValue()       -> "mindWander"
//   PauseTag.fromValue("toilet")         -> TOILET
//   PauseTag.PHONE.source()              -> CauseSource.INTERNAL
//
// 이 파일 안의 enum 들은 서로 비슷해서 한 파일에 모았습니다. 바깥에서는 SessionTags.PauseTag 처럼 씁니다.
public final class SessionTags {

  // 값만 모아 둔 곳이라서 객체를 만들 일이 없습니다.
  private SessionTags() {}

  // 원인이 어디서 왔는지 — 귀인(1번 오류) 비율을 셀 때 씁니다. (FE 의 CauseSource)
  //   INTERNAL: 내 행동·선택에서 나옴 (폰 확인, 딴생각 …)
  //   EXTERNAL: 바깥에서 옴 (가족이 부름, 운 …)
  //   BASIC:    화장실·물처럼 누구나 필요한 것. 귀인 비율을 셀 때는 뺍니다
  //   UNKNOWN:  아직 모름. '기타' 를 고르고 글만 쓴 것 (나중에 AI 가 나눕니다)
  public enum CauseSource {
    INTERNAL,
    EXTERNAL,
    BASIC,
    UNKNOWN;

    // FE 와 주고받는 글자로 바꿉니다. (항상 소문자)
    public String toValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  // 일시정지 이유 7개 (「4.4 타이머」 일시정지). FE 의 PauseTag 와 PAUSE_TAGS 입니다.
  public enum PauseTag {
    // 화장실
    TOILET("toilet", CauseSource.BASIC),
    // 물/간식
    SNACK("snack", CauseSource.BASIC),
    // 폰 확인
    PHONE("phone", CauseSource.INTERNAL),
    // 딴생각
    MIND_WANDER("mindWander", CauseSource.INTERNAL),
    // 막힘
    STUCK("stuck", CauseSource.INTERNAL),
    // 외부 방해(가족 등)
    EXTERNAL("external", CauseSource.EXTERNAL),
    // 기타 — 글만 받고, 나중에 AI 가 내부/외부로 나눕니다
    OTHER("other", CauseSource.UNKNOWN);

    // FE 와 주고받는 글자. mindWander 처럼 소문자로만 바꿔서는 안 나오는 글자가 있어서 하나씩 적어 둡니다.
    private final String value;
    // 이 이유가 어디서 왔는지 (내 쪽 / 바깥 / 누구나 필요한 것 / 모름)
    private final CauseSource source;

    PauseTag(String value, CauseSource source) {
      this.value = value;
      this.source = source;
    }

    public String toValue() {
      return value;
    }

    // 이 이유가 어디서 왔는지 돌려줍니다. FE 의 getPauseSource 와 같습니다.
    //   PHONE → INTERNAL,  EXTERNAL → EXTERNAL,  TOILET → BASIC,  OTHER → UNKNOWN
    public CauseSource source() {
      return source;
    }

    // FE 가 보낸 글자를 이유로 바꿉니다. 7가지 중 하나가 아니면 null 입니다.
    public static PauseTag fromValue(String value) {
      for (PauseTag tag : values()) {
        if (tag.value.equals(value)) {
          return tag;
        }
      }
      return null;
    }
  }

  // 체크리스트를 다 못 했을 때 고르는 이유 7개. FE 의 ShortfallReason 과 SHORTFALL_REASONS 입니다.
  public enum ShortfallReason {
    // 계획이 많았음
    TOO_MUCH("tooMuch"),
    // 막힘
    STUCK("stuck"),
    // 폰·SNS
    PHONE("phone"),
    // 딴생각
    MIND_WANDER("mindWander"),
    // 피곤함
    TIRED("tired"),
    // 외부 방해
    EXTERNAL("external"),
    // 기타
    ETC("etc");

    private final String value;

    ShortfallReason(String value) {
      this.value = value;
    }

    public String toValue() {
      return value;
    }

    // FE 가 보낸 글자를 이유로 바꿉니다. 7가지 중 하나가 아니면 null 입니다.
    public static ShortfallReason fromValue(String value) {
      for (ShortfallReason reason : values()) {
        if (reason.value.equals(value)) {
          return reason;
        }
      }
      return null;
    }
  }

  // 잘 된 이유 6개. 성공을 내 덕으로 보는지 밖의 덕으로 보는지 셉니다. FE 의 GoodReason 과 GOOD_REASONS 입니다.
  // 컨디션은 내가 정할 수 없는 것이라 바깥(EXTERNAL)으로 셉니다. (FE 와 같음)
  public enum GoodReason {
    // 집중 잘 됨
    FOCUS("focus", CauseSource.INTERNAL),
    // 계획이 적당함
    GOOD_PLAN("goodPlan", CauseSource.INTERNAL),
    // 컨디션 좋음
    CONDITION("condition", CauseSource.EXTERNAL),
    // 환경이 좋았음
    ENVIRONMENT("environment", CauseSource.EXTERNAL),
    // 누가 도와줌
    HELPED("helped", CauseSource.EXTERNAL),
    // 운이 좋았음
    LUCK("luck", CauseSource.EXTERNAL);

    private final String value;
    private final CauseSource source;

    GoodReason(String value, CauseSource source) {
      this.value = value;
      this.source = source;
    }

    public String toValue() {
      return value;
    }

    // 이 이유가 내 덕(INTERNAL)인지 밖의 덕(EXTERNAL)인지 돌려줍니다.
    public CauseSource source() {
      return source;
    }

    // FE 가 보낸 글자를 이유로 바꿉니다. 6가지 중 하나가 아니면 null 입니다.
    public static GoodReason fromValue(String value) {
      for (GoodReason reason : values()) {
        if (reason.value.equals(value)) {
          return reason;
        }
      }
      return null;
    }
  }

  // 세션 중에 같이 한 것 — 즐거움 묶기를 대신합니다. FE 의 WithWhat 입니다.
  public enum WithWhat {
    // 음악
    MUSIC,
    // 영상 틀어둠
    VIDEO,
    // 없음
    NONE;

    public String toValue() {
      return name().toLowerCase(Locale.ROOT);
    }

    // FE 가 보낸 글자를 값으로 바꿉니다. 3가지 중 하나가 아니면 null 입니다.
    public static WithWhat fromValue(String value) {
      for (WithWhat withWhat : values()) {
        if (withWhat.toValue().equals(value)) {
          return withWhat;
        }
      }
      return null;
    }
  }

  // 노는 기록의 종류 6개. FE 의 PlayTag 와 PLAY_TAGS 입니다.
  public enum PlayTag {
    // 유튜브
    YOUTUBE,
    // 게임
    GAME,
    // SNS
    SNS,
    // 웹툰
    WEBTOON,
    // 쉬기
    REST,
    // 기타
    ETC;

    public String toValue() {
      return name().toLowerCase(Locale.ROOT);
    }

    // FE 가 보낸 글자를 종류로 바꿉니다. 6가지 중 하나가 아니면 null 입니다.
    public static PlayTag fromValue(String value) {
      for (PlayTag tag : values()) {
        if (tag.toValue().equals(value)) {
          return tag;
        }
      }
      return null;
    }
  }
}
