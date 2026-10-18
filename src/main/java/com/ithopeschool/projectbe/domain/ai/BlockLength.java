package com.ithopeschool.projectbe.domain.ai;

import java.util.ArrayList;
import java.util.List;

// "내가 한 번에 공부하는 길이" (카드 한 장의 길이) 를 내 기록으로 정하는 규칙입니다. (이슈 #237)
// 길이를 말하지 않은 카드 초안(말로 카드 만들기)이나 AI 하루 계획이 이 길이를 씁니다.
// 예: 집중을 오래 못 하는 사람(최근 기록이 짧거나, 심리검사에서 딴생각이 높음)은 짧은 칸을 받습니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. DB 를 읽는 쪽은 BlockLengthService 입니다.
//
// 고르는 순서 (앞의 것이 있으면 그것으로)
//   1. 최근 14일 공부 기록이 5번 이상이면: 한 번 길이의 가운데값
//   2. 처음 설문의 "한 번에 집중할 수 있는 시간"
//   3. 심리검사에서 "공부 중 딴생각" 이 높으면: 30분
//   4. 아무것도 없으면: 50분
// 1 · 2 는 5분 단위로 반올림하고 30 ~ 90분 안으로 맞춥니다. (30분 밑은 공부 블록으로 보지 않음, StudyBlock)
public final class BlockLength {

  // 기록으로 정하려면 있어야 하는 공부 기록 수
  static final int MIN_SESSIONS = 5;
  static final int MIN_MINUTES = StudyBlock.MIN_MINUTES;
  static final int MAX_MINUTES = 90;
  static final int DEFAULT_MINUTES = StudyBlock.DEFAULT_MINUTES;
  static final int DISTRACTED_MINUTES = StudyBlock.MIN_MINUTES;

  // 정한 길이와, 왜 그 길이인지 학생에게 보여 줄 한 문장
  public record Choice(int minutes, String evidence) {}

  private BlockLength() {}

  //   ([20, 25, 30, 25, 40], null, false) → 30분 "최근 14일 공부 기록 5번의 한 번 길이 가운데값이 25분이라 30분으로 …"
  //   ([40, 50, 45, 60, 50], null, false) → 50분
  //   ([], 40, false)                     → 40분 "처음 설문에서 한 번에 40분 …"
  //   ([], null, true)                    → 30분 "심리검사에서 …"
  //   ([], null, false)                   → 50분 "아직 공부 기록이 적어서 …"
  public static Choice of(
      List<Integer> sessionMinutes, Integer selfFocus, boolean distractionHigh) {
    if (sessionMinutes.size() >= MIN_SESSIONS) {
      int median = medianOf(sessionMinutes);
      int minutes = clamp(median);
      return new Choice(
          minutes,
          "최근 14일 공부 기록 "
              + sessionMinutes.size()
              + "번의 한 번 길이 가운데값이 "
              + median
              + "분이라 "
              + minutes
              + "분으로 잡았어요");
    }
    if (selfFocus != null) {
      int minutes = clamp(selfFocus);
      return new Choice(
          minutes, "처음 설문에서 한 번에 " + selfFocus + "분 집중할 수 있다고 해서 " + minutes + "분으로 잡았어요");
    }
    if (distractionHigh) {
      return new Choice(
          DISTRACTED_MINUTES, "심리검사에서 공부 중 흔들리는 편이라고 나와서 " + DISTRACTED_MINUTES + "분으로 짧게 잡았어요");
    }
    return new Choice(DEFAULT_MINUTES, "아직 공부 기록이 적어서 " + DEFAULT_MINUTES + "분으로 잡았어요");
  }

  // 가운데값 (짝수 개면 가운데 둘의 평균, 반올림)   [10, 20, 31, 41] → 26
  static int medianOf(List<Integer> values) {
    List<Integer> sorted = new ArrayList<>(values);
    sorted.sort(null);
    int n = sorted.size();
    if (n % 2 == 1) {
      return sorted.get(n / 2);
    }
    return (int) Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
  }

  // 5분 단위로 반올림하고 30 ~ 90분 안으로   12 → 30,  27 → 30,  43 → 45,  120 → 90
  static int clamp(int minutes) {
    int rounded = (int) Math.round(minutes / 5.0) * 5;
    return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, rounded));
  }
}
