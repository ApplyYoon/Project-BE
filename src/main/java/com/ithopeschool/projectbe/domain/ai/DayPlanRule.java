package com.ithopeschool.projectbe.domain.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// AI 하루 계획(POST /ai/day-plan)에서 "숫자" 를 정하는 규칙입니다. 숫자는 모두 내 기록으로 코드가 정하고, AI 는 문장만 씁니다.
// 값만 계산하는 도구라서 객체를 만들 일이 없습니다. 고를 때마다 "왜 그랬는지" 근거 문장을 같이 돌려줍니다.
//
// ■ 한 칸의 길이 (blockMinutesOf) — 집중을 오래 못 하는 사람은 짧게, 자주
//   1. 최근 14일 공부 기록이 MIN_SESSIONS(5번) 이상이면, 한 번 길이(일시정지 뺀 순공)의 가운데값
//   2. 아니면 온보딩에서 답한 "한 번에 집중할 수 있는 시간"
//   3. 그것도 없고 심리검사의 주의 분산이 높으면 30분, 아니면 50분
//   어느 쪽이든 5분 단위로 반올림하고 15 ~ 90분 안으로 맞춥니다.
//
// ■ 하루 총량 (totalMinutesOf)
//   1. 최근 14일 중 공부한 날이 MIN_STUDY_DAYS(3일) 이상이면, 그날들 순공 평균의 1.1배 (조금씩 늘리기)
//   2. 아니면 온보딩의 "평일 하루 공부 가능 시간", 그것도 없으면 120분
//   3. 최근 계획 완료율이 60% 미만이거나 심리검사의 계획 낙관이 높으면 0.8배로 줄입니다. (계획을 크게 잡는 사람을 위해)
//   4. 10분 단위로 반올림하고 30 ~ 600분 안으로 맞춘 뒤, 그날 이미 세운 공부 계획 시간을 뺍니다.
//
// ■ 과목 순서 (subjectOrder)
//   14일 안에 시험이 있는 과목이 먼저(가까운 시험부터), 그다음 최근 14일 가장 덜 한 과목부터.
//   시험 과목은 돌아가며 넣을 때 두 번씩 넣어서 더 많이 들어갑니다. (subjectRotation)
public final class DayPlanRule {

  static final int MIN_SESSIONS = 5;
  static final int MIN_STUDY_DAYS = 3;
  static final int MIN_BLOCK = 15;
  static final int MAX_BLOCK = 90;
  static final int DEFAULT_BLOCK = 50;
  static final int DISTRACTED_BLOCK = 30;
  static final int MIN_TOTAL = 30;
  static final int MAX_TOTAL = 600;
  static final int DEFAULT_TOTAL = 120;
  static final double GROW = 1.1;
  static final double SHRINK = 0.8;
  static final int LOW_PLAN_RATE = 60;
  static final int EXAM_SOON_DAYS = 14;
  // 한 번에 만드는 칸의 최대 수
  static final int MAX_BLOCKS = 12;

  // 고른 숫자와 그 근거 문장
  public record Choice(int minutes, String evidence) {}

  // 하루 총량(이미 세운 계획을 뺀 것)과 그 근거 문장들
  public record Total(int minutes, List<String> evidence) {}

  // 과목 하나의 재료. examDaysLeft: 14일 안에 있는 가장 가까운 시험까지 남은 날 (없으면 null)
  public record SubjectInfo(Long id, String name, Integer examDaysLeft, int studiedMinutes) {}

  private DayPlanRule() {}

  // 한 칸의 길이와 근거
  //   sessionMinutes: 최근 14일 공부 기록마다의 순공(분) / selfFocus: 온보딩 "한 번에 집중 가능한 시간"(없으면 null)
  //   distractionHigh: 심리검사 주의 분산이 높은지
  public static Choice blockMinutesOf(
      List<Integer> sessionMinutes, Integer selfFocus, boolean distractionHigh) {
    if (sessionMinutes.size() >= MIN_SESSIONS) {
      int median = medianOf(sessionMinutes);
      int block = clampBlock(median);
      return new Choice(
          block,
          "최근 14일 공부 기록 "
              + sessionMinutes.size()
              + "번의 한 번 길이 가운데값이 "
              + median
              + "분이라 "
              + block
              + "분씩 나눴어요");
    }
    if (selfFocus != null) {
      int block = clampBlock(selfFocus);
      return new Choice(block, "처음 설문에서 한 번에 " + selfFocus + "분 집중할 수 있다고 해서 " + block + "분씩 나눴어요");
    }
    if (distractionHigh) {
      return new Choice(
          DISTRACTED_BLOCK, "심리검사에서 공부 중 흔들리는 편이라고 나와서 " + DISTRACTED_BLOCK + "분씩 짧게 나눴어요");
    }
    return new Choice(DEFAULT_BLOCK, "아직 공부 기록이 적어서 " + DEFAULT_BLOCK + "분씩 나눴어요");
  }

  // 하루 총량(이미 세운 계획을 뺀 것)과 근거들
  //   dailyMinutes: 최근 14일 중 공부한 날마다의 순공(분) / selfDaily: 온보딩 "평일 하루 공부 가능 시간"(없으면 null)
  //   planRate: 최근 14일 공부 계획 완료율(%, 계획이 없으면 null) / planOptimismHigh: 심리검사 계획 낙관이 높은지
  //   alreadyPlanned: 그날 이미 세운 공부 계획 시간(분)
  public static Total totalMinutesOf(
      List<Integer> dailyMinutes,
      Integer selfDaily,
      Integer planRate,
      boolean planOptimismHigh,
      int alreadyPlanned) {
    List<String> evidence = new ArrayList<>();
    double total;
    if (dailyMinutes.size() >= MIN_STUDY_DAYS) {
      int sum = 0;
      for (int minutes : dailyMinutes) {
        sum += minutes;
      }
      int average = (int) Math.round((double) sum / dailyMinutes.size());
      total = average * GROW;
      evidence.add("최근 14일 공부한 " + dailyMinutes.size() + "일의 평균이 " + average + "분이라 조금 더 잡았어요");
    } else if (selfDaily != null) {
      total = selfDaily;
      evidence.add("처음 설문에서 하루 " + selfDaily + "분 공부할 수 있다고 해서 그만큼 잡았어요");
    } else {
      total = DEFAULT_TOTAL;
      evidence.add("아직 기록이 적어서 하루 " + DEFAULT_TOTAL + "분으로 잡았어요");
    }
    if (planRate != null && planRate < LOW_PLAN_RATE) {
      total *= SHRINK;
      evidence.add("최근 계획의 " + planRate + "%만 끝내서 양을 줄였어요");
    } else if (planOptimismHigh) {
      total *= SHRINK;
      evidence.add("심리검사에서 계획을 크게 잡는 편이라고 나와서 양을 줄였어요");
    }
    int rounded = Math.max(MIN_TOTAL, Math.min(MAX_TOTAL, (int) Math.round(total / 10.0) * 10));
    if (alreadyPlanned > 0) {
      evidence.add("그날 이미 세운 계획 " + alreadyPlanned + "분을 뺐어요");
    }
    return new Total(Math.max(0, rounded - alreadyPlanned), evidence);
  }

  // 과목 순서: 시험이 가까운 과목 → 최근 덜 한 과목 → 번호 순
  public static List<SubjectInfo> subjectOrder(List<SubjectInfo> subjects) {
    List<SubjectInfo> ordered = new ArrayList<>(subjects);
    ordered.sort(
        Comparator.comparing(
                (SubjectInfo s) -> s.examDaysLeft() == null ? Integer.MAX_VALUE : s.examDaysLeft())
            .thenComparingInt(SubjectInfo::studiedMinutes)
            .thenComparing(SubjectInfo::id));
    return ordered;
  }

  // 칸에 넣을 과목들: 순서대로 돌아가며 넣고, 시험 과목은 한 바퀴에 두 번 넣습니다.
  public static List<SubjectInfo> subjectRotation(List<SubjectInfo> ordered, int blockCount) {
    List<SubjectInfo> round = new ArrayList<>();
    for (SubjectInfo subject : ordered) {
      round.add(subject);
      if (subject.examDaysLeft() != null) {
        round.add(subject);
      }
    }
    List<SubjectInfo> picked = new ArrayList<>();
    for (int i = 0; i < blockCount && !round.isEmpty(); i++) {
      picked.add(round.get(i % round.size()));
    }
    return picked;
  }

  // 칸마다의 시간: 한 칸 길이로 채우고, 남는 시간이 15분(MIN_BLOCK) 이상이면 짧은 칸 하나를 더합니다. 많아도 12칸.
  //   (130, 25) → [25, 25, 25, 25, 25]          남는 5분은 버림
  //   (30, 50)  → [30]                          총량이 한 칸보다 짧으면 짧은 칸 하나
  //   (0, 50)   → []                            그날 이미 충분히 계획함
  public static List<Integer> blocksOf(int totalMinutes, int blockMinutes) {
    List<Integer> blocks = new ArrayList<>();
    int left = totalMinutes;
    while (left >= blockMinutes && blocks.size() < MAX_BLOCKS) {
      blocks.add(blockMinutes);
      left -= blockMinutes;
    }
    if (left >= MIN_BLOCK && blocks.size() < MAX_BLOCKS) {
      blocks.add(left);
    }
    return blocks;
  }

  // 시험 과목과 덜 한 과목에 대한 근거 문장 (없으면 빈 목록)
  public static List<String> subjectEvidence(List<SubjectInfo> ordered) {
    List<String> lines = new ArrayList<>();
    for (SubjectInfo subject : ordered) {
      if (subject.examDaysLeft() != null) {
        lines.add(subject.examDaysLeft() + "일 뒤 시험이 있는 " + subject.name() + "을(를) 먼저 · 더 많이 넣었어요");
        break;
      }
    }
    SubjectInfo least =
        ordered.stream()
            .filter(s -> s.examDaysLeft() == null)
            .min(
                Comparator.comparingInt(SubjectInfo::studiedMinutes).thenComparing(SubjectInfo::id))
            .orElse(null);
    if (least != null && ordered.size() > 1) {
      lines.add("최근 14일 가장 덜 한 " + least.name() + "(" + least.studiedMinutes() + "분)을(를) 넣었어요");
    }
    return lines;
  }

  // 가운데값 (짝수 개면 가운데 둘의 평균, 반올림)
  static int medianOf(List<Integer> values) {
    List<Integer> sorted = new ArrayList<>(values);
    sorted.sort(null);
    int n = sorted.size();
    if (n % 2 == 1) {
      return sorted.get(n / 2);
    }
    return (int) Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
  }

  // 5분 단위로 반올림하고 15 ~ 90분 안으로
  private static int clampBlock(int minutes) {
    int rounded = (int) Math.round(minutes / 5.0) * 5;
    return Math.max(MIN_BLOCK, Math.min(MAX_BLOCK, rounded));
  }
}
