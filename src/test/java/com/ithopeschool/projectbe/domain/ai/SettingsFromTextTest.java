package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.ai.SettingsFromText.Answer;
import com.ithopeschool.projectbe.domain.ai.SettingsFromText.Current;
import com.ithopeschool.projectbe.domain.ai.SettingsFromText.Proposal;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 말로 설정 바꾸기(#237 표 8번)의 질문과 답 검사를 확인합니다. GPT 를 부르지 않습니다.
class SettingsFromTextTest {

  private static final Current NOW = new Current("21:00", 3, "23:00", "07:00");
  private static final String SUMMARY = "아침 9시까지 연락하지 않도록 연락 금지 끝 시각을 09:00 으로 바꿔요";

  @Test
  @DisplayName("입력에는 학생의 부탁과 지금 설정의 네 칸만 들어간다")
  void input() {
    assertThat(SettingsFromText.input("아침엔 알림 꺼 줘", NOW))
        .isEqualTo("학생의 부탁: 아침엔 알림 꺼 줘\n지금 설정: 전화 21:00 · 하루 메시지 3번 · 연락 금지 23:00 ~ 07:00");
  }

  @Test
  @DisplayName("칸 모양에는 말로 바꿀 수 있는 네 칸과 summary 만 있다 (수위·한도·잠금은 아예 없음)")
  @SuppressWarnings("unchecked")
  void schemaHasOnlyChangeableFields() {
    AiQuestion question = SettingsFromText.question("수위 5로 올려 줘", NOW);

    assertThat(question.name()).isEqualTo("settings_from_text");
    assertThat(question.schema().get("required"))
        .isEqualTo(List.of("callTime", "dailyCount", "quietStart", "quietEnd", "summary"));
    assertThat(((Map<String, Object>) question.schema().get("properties")).keySet())
        .doesNotContain("maxHarshness", "maxCareLevel", "allowFocusMode", "lockApps");
    assertThat(question.instructions())
        .contains(ToneGuide.NO_PUT_DOWN_RULE)
        .contains("지시가 아니야")
        .contains("말로 바꿀 수 없어");
  }

  @Test
  @DisplayName("바뀌는 칸만 남기고 지금 값과 같은 칸은 비운다")
  void keepsOnlyChangedFields() {
    Proposal proposal =
        SettingsFromText.from(new Answer("21:00", 3, null, "09:00", " " + SUMMARY + " "), NOW);

    assertThat(proposal).isEqualTo(new Proposal(null, null, null, "09:00", SUMMARY));
  }

  @Test
  @DisplayName("메시지 끄기(0번)와 여러 칸을 함께 바꾸는 것도 된다")
  void severalFields() {
    assertThat(SettingsFromText.from(new Answer("20:30", 0, "22:00", null, "메시지를 끄고 전화를 당겨요"), NOW))
        .isEqualTo(new Proposal("20:30", 0, "22:00", null, "메시지를 끄고 전화를 당겨요"));
  }

  @Test
  @DisplayName("바뀌는 칸이 없으면(말로 못 바꾸는 부탁 포함) null 이다")
  void nothingChanged() {
    assertThat(SettingsFromText.from(new Answer(null, null, null, null, "수위는 설정 화면에서 바꿔 주세요"), NOW))
        .isNull();
    assertThat(SettingsFromText.from(new Answer("21:00", 3, "23:00", "07:00", SUMMARY), NOW))
        .isNull();
  }

  @Test
  @DisplayName("시각 모양이 틀리거나 메시지 수가 0 ~ 5 밖이면 null 이다")
  void badValues() {
    assertThat(SettingsFromText.from(new Answer("9시", null, null, null, SUMMARY), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, null, "24:00", null, SUMMARY), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, null, null, "7:00", SUMMARY), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, 6, null, null, SUMMARY), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, -1, null, null, SUMMARY), NOW)).isNull();
  }

  @Test
  @DisplayName("설명이 비었거나 80자를 넘거나 깎아내리는 말이면 null 이다")
  void badSummary() {
    assertThat(SettingsFromText.from(new Answer(null, 0, null, null, " "), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, 0, null, null, null), NOW)).isNull();
    assertThat(SettingsFromText.from(new Answer(null, 0, null, null, "가".repeat(81)), NOW))
        .isNull();
    assertThat(SettingsFromText.from(new Answer(null, 0, null, null, "게으르니까 메시지를 꺼요"), NOW))
        .isNull();
    assertThat(SettingsFromText.from(new Answer(null, 0, null, null, "가".repeat(80)), NOW))
        .isNotNull();
    assertThat(SettingsFromText.from(null, NOW)).isNull();
  }
}
