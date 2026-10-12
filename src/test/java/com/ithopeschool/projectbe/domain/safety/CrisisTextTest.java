package com.ithopeschool.projectbe.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// "위기 표현" 검사 규칙(이슈 #56)을 확인합니다.
// feExample 로 시작하는 4개는 FE 함수(hasCrisisText) 주석의 예시를 그대로 옮긴 것입니다.
class CrisisTextTest {

  // 글 하나만 검사하는 도우미입니다.
  private static boolean hasCrisis(String text) {
    return CrisisText.hasCrisisText(List.of(text));
  }

  @Test
  void feExample1OrdinaryTextIsNotCrisis() {
    // FE 예1) ['오늘 학원에서 힘들었다'] → false
    assertThat(hasCrisis("오늘 학원에서 힘들었다")).isFalse();
  }

  @Test
  void feExample2KeywordInsideALongSentence() {
    // FE 예2) ['이제 그만 살고 싶다는 생각이 들었다'] → true ('그만살고싶')
    assertThat(hasCrisis("이제 그만 살고 싶다는 생각이 들었다")).isTrue();
  }

  @Test
  void feExample3EveryFormIsDetected() {
    // FE 예3) ['죽고싶다'], ['죽고 싶어'], ['사라지고 싶어'], ['자해했어'], ['극단적 선택'] → 모두 true
    assertThat(hasCrisis("죽고싶다")).isTrue();
    assertThat(hasCrisis("죽고 싶어")).isTrue();
    assertThat(hasCrisis("사라지고 싶어")).isTrue();
    assertThat(hasCrisis("자해했어")).isTrue();
    assertThat(hasCrisis("극단적 선택")).isTrue();
  }

  @Test
  void feExample4WantingToLiveIsNotCrisis() {
    // FE 예4) ['오래오래 살고 싶다'] → false ('살고싶' 만으로는 잡지 않습니다)
    assertThat(hasCrisis("오래오래 살고 싶다")).isFalse();
  }

  @Test
  void everyKeywordInTheListIsDetected() {
    // 목록의 9개가 평소 쓰는 띄어쓰기로 적혀도 하나도 빠짐없이 잡히는지 봅니다.
    assertThat(hasCrisis("그냥 죽고 싶다")).isTrue();
    assertThat(hasCrisis("어디론가 사라지고 싶다")).isTrue();
    assertThat(hasCrisis("이제 그만 살고 싶어")).isTrue();
    assertThat(hasCrisis("더는 살고 싶지 않아")).isTrue();
    assertThat(hasCrisis("요즘은 살기 싫다")).isTrue();
    assertThat(hasCrisis("자살 생각이 난다")).isTrue();
    assertThat(hasCrisis("자해를 했다")).isTrue();
    assertThat(hasCrisis("극단적 선택을 생각했다")).isTrue();
    assertThat(hasCrisis("극단적인 선택을 생각했다")).isTrue();

    // 목록 자체가 바뀌면 위의 문장도 같이 고쳐야 한다는 것을 알 수 있게 개수를 확인합니다.
    assertThat(CrisisText.KEYWORDS).hasSize(9);
  }

  @Test
  void wordEndingsDoNotMatter() {
    // 어미를 빼고 적어 둬서, 끝이 달라도 모두 잡습니다.
    assertThat(hasCrisis("죽고 싶다")).isTrue();
    assertThat(hasCrisis("죽고싶어")).isTrue();
    assertThat(hasCrisis("죽고 싶었어")).isTrue();
  }

  @Test
  void negatedSentenceIsStillDetected() {
    // 부정하는 말도 잡힙니다. 놓치는 것보다 한 번 더 안내하는 쪽이 안전하다고 보고 정한 규칙입니다.
    assertThat(hasCrisis("죽고 싶지는 않아")).isTrue();
  }

  @Test
  void spacesTabsAndNewlinesAreIgnored() {
    // 띄어쓰기·탭·줄바꿈이 어디에 몇 개 있든 지우고 봅니다.
    assertThat(hasCrisis("죽 고 싶 다")).isTrue();
    assertThat(hasCrisis("죽고    싶다")).isTrue();
    assertThat(hasCrisis("죽고\t싶다")).isTrue();
    assertThat(hasCrisis("죽고\n싶다")).isTrue();
    assertThat(hasCrisis("죽고\r\n싶다")).isTrue();
  }

  @Test
  void specialSpacesAreIgnoredLikeFe() {
    // FE(자바스크립트)의 \s 가 지우는 특수 빈칸도 똑같이 지웁니다.
    //  : 줄바꿈 없는 공백 / 　: 전각 공백(한글 자판에서 나올 수 있음) /  : 넓은 공백
    assertThat(hasCrisis("죽고 싶다")).isTrue();
    assertThat(hasCrisis("죽고　싶다")).isTrue();
    assertThat(hasCrisis("죽고 싶다")).isTrue();
  }

  @Test
  void oneCrisisTextAmongManyIsEnough() {
    // 여러 글 중 하나에만 있어도 true 입니다.
    assertThat(CrisisText.hasCrisisText(List.of("오늘은 수학을 했다", "사라지고 싶다", "내일은 영어"))).isTrue();
    assertThat(CrisisText.hasCrisisText(List.of("오늘은 수학을 했다", "조금 피곤했다", "내일은 영어"))).isFalse();
  }

  @Test
  void keywordSplitAcrossTwoTextsIsNotDetected() {
    // 글은 하나씩 따로 봅니다. 두 글을 이어 붙여야 만들어지는 표현은 잡지 않습니다.
    assertThat(CrisisText.hasCrisisText(List.of("죽고", "싶다"))).isFalse();
  }

  @Test
  void emptyInputsAreNotCrisis() {
    // 글이 없거나 비어 있으면 false 입니다.
    assertThat(CrisisText.hasCrisisText(List.of())).isFalse();
    assertThat(hasCrisis("")).isFalse();
    assertThat(hasCrisis("   \n ")).isFalse();
  }

  @Test
  void nullTextIsSkipped() {
    // 안 적은 칸(null)이 섞여 있어도 에러 없이 건너뜁니다. (체크인의 약속처럼 비워 둘 수 있는 칸)
    assertThat(CrisisText.hasCrisisText(Arrays.asList(null, "오늘 학원에서 힘들었다"))).isFalse();
    assertThat(CrisisText.hasCrisisText(Arrays.asList(null, "죽고 싶다"))).isTrue();
  }

  @Test
  void knownOverDetectionAcrossWords() {
    // 알려진 한계: 띄어쓰기를 지우기 때문에, 낱말 사이에서 우연히 만들어진 '자살'·'자해' 도 잡힙니다.
    // 위기 표현이 아닌 글이지만 FE 도 똑같이 true 를 돌려줍니다. (혼자살기 / 혼자해냈다)
    // 이 동작을 바꾸려면 FE 의 hasCrisisText 와 같이 바꿔야 합니다.
    assertThat(hasCrisis("혼자 살기 시작했다")).isTrue();
    assertThat(hasCrisis("숙제를 혼자 해냈다")).isTrue();
  }
}
