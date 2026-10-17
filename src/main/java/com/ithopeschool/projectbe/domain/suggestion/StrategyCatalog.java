package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.care.MissReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 전략 카탈로그: AI 가 개선안을 백지에서 지어내지 않도록 미리 적어 둔 전략 36개입니다. (이슈 #50, 기획서 v2 4장)
// 개선안 제안(#51)은 여기서 학생의 못 채운 이유에 맞는 전략을 쉬움 · 보통 · 강함 하나씩 고르고, GPT 는 문장만 다듬습니다.
//
// 이유 6가지 × 강도 3가지 × 2개씩입니다.
//   피곤·수면(TIRED) · 약속·행사(EVENT) · 폰·SNS(PHONE) · 계획 과다(TOO_MUCH) · 하기 싫음(UNWILLING) · 미루다
// 보니(PROCRASTINATED, #225) · 기타(ETC)
//   - "시작 지연(미루기)" 은 이유 버튼에 따로 없고 '하기 싫음' 에 들어가서(Weakness 의 미루기 → 하기 싫음), 하기 싫음 전략에 같이 넣었습니다.
//   - 아픔(SICK)은 전략이 없습니다. 아플 때는 방법을 바꾸기보다 쉬고 목표를 낮추는 것이 먼저입니다. (CareDecision 의 목표 하향)
//
// 문장을 쓸 때 지킨 것
//   - 모두 "만약 ~하면(ifText) → ~한다(thenText)" 모양입니다. 언제 할지가 정해져 있어야 지키기 쉽습니다.
//   - 사람을 탓하지 않고 행동 하나만 바꿉니다. (CLAUDE.md 4장)
//   - 강함도 벌이 아니라 "환경을 바꾸거나 남과 약속하기" 입니다.
//
// DB 테이블이 아니라 이 파일 하나에 둔 이유: 성찰일기 질문(ReflectionQuestions) · 생각 습관(CognitiveBias)처럼
// 정해 둔 문장은 코드에 두면 테스트로 모양을 검사할 수 있고, 운영 DB 에 사람이 따로 넣을 일이 없습니다.
// 문장을 고치려면 이 파일만 고치면 됩니다. (이름(key)은 바꾸지 않습니다. Strategy 참고)
public final class StrategyCatalog {

  // 전체 전략. 이유 순서(MissReason) → 강도 순서(쉬움 → 보통 → 강함) 로 적었습니다.
  public static final List<Strategy> ALL =
      List.of(
          // 피곤·수면
          strategy(
              MissReason.TIRED, SuggestionLevel.EASY, 1, "공부하다 눈이 감기면", "5분 일어나서 물을 마시고 몸을 푼다"),
          strategy(
              MissReason.TIRED, SuggestionLevel.EASY, 2, "피곤한 날 공부를 시작하면", "가장 쉬운 과목부터 15분 한다"),
          strategy(
              MissReason.TIRED,
              SuggestionLevel.NORMAL,
              1,
              "하루 계획을 세울 때면",
              "어려운 과목을 컨디션이 좋은 시간대에 넣는다"),
          strategy(MissReason.TIRED, SuggestionLevel.NORMAL, 2, "밤 11시가 되면", "폰을 충전기에 꽂고 잘 준비를 한다"),
          strategy(
              MissReason.TIRED, SuggestionLevel.HARD, 1, "정해 둔 잠자는 시각이 되면", "하던 공부를 멈추고 불을 끈다"),
          strategy(
              MissReason.TIRED, SuggestionLevel.HARD, 2, "주말 아침이 되면", "평일과 1시간 넘게 차이 나지 않게 일어난다"),
          // 약속·행사
          strategy(MissReason.EVENT, SuggestionLevel.EASY, 1, "약속이 있는 날이면", "그날 목표를 평소의 절반으로 줄인다"),
          strategy(MissReason.EVENT, SuggestionLevel.EASY, 2, "약속에서 돌아오면", "10분만 오늘 배운 것을 훑어본다"),
          strategy(MissReason.EVENT, SuggestionLevel.NORMAL, 1, "약속이 잡히면", "그 전날 공부 30분을 미리 당겨 한다"),
          strategy(
              MissReason.EVENT, SuggestionLevel.NORMAL, 2, "일주일 계획을 세울 때면", "약속·행사가 있는 날을 먼저 표시한다"),
          strategy(
              MissReason.EVENT, SuggestionLevel.HARD, 1, "행사가 있는 주가 되면", "행사 전날까지 그 주 계획의 절반을 끝낸다"),
          strategy(MissReason.EVENT, SuggestionLevel.HARD, 2, "약속 제안을 받으면", "공부 계획을 확인한 뒤에 답한다"),
          // 폰·SNS
          strategy(MissReason.PHONE, SuggestionLevel.EASY, 1, "공부를 시작하면", "알림을 모두 끈다"),
          strategy(MissReason.PHONE, SuggestionLevel.EASY, 2, "공부를 시작하면", "폰을 뒤집어 손이 닿지 않는 곳에 둔다"),
          strategy(MissReason.PHONE, SuggestionLevel.NORMAL, 1, "학원에서 돌아오면", "폰을 가방에 넣고 공부를 시작한다"),
          strategy(
              MissReason.PHONE, SuggestionLevel.NORMAL, 2, "쉬는 시간이 되면", "타이머로 10분을 재고 그 안에만 폰을 본다"),
          strategy(MissReason.PHONE, SuggestionLevel.HARD, 1, "공부를 시작하면", "폰을 다른 방에 두고 온다"),
          strategy(MissReason.PHONE, SuggestionLevel.HARD, 2, "계획된 공부 시간이 되면", "집중 모드를 켠다"),
          // 계획 과다
          strategy(MissReason.TOO_MUCH, SuggestionLevel.EASY, 1, "계획을 세울 때면", "꼭 할 것 하나에 별표를 친다"),
          strategy(
              MissReason.TOO_MUCH,
              SuggestionLevel.EASY,
              2,
              "하루 계획을 세울 때면",
              "10분짜리 최소 목표를 하나 같이 정한다"),
          strategy(
              MissReason.TOO_MUCH,
              SuggestionLevel.NORMAL,
              1,
              "하루 계획을 세울 때면",
              "최근 실제로 공부한 시간보다 10%만 더 잡는다"),
          strategy(
              MissReason.TOO_MUCH,
              SuggestionLevel.NORMAL,
              2,
              "계획을 세울 때면",
              "과목마다 걸릴 시간을 적고 합을 확인한다"),
          strategy(
              MissReason.TOO_MUCH,
              SuggestionLevel.HARD,
              1,
              "계획을 다 못 채운 날이 이틀 이어지면",
              "다음 날 계획을 3개 이하로 줄인다"),
          strategy(
              MissReason.TOO_MUCH, SuggestionLevel.HARD, 2, "일주일 계획을 세울 때면", "하루는 비워 두고 밀린 것만 한다"),
          // 하기 싫음 (미루기 · 지루함 포함)
          strategy(
              MissReason.UNWILLING, SuggestionLevel.EASY, 1, "책상에 앉으면", "타이머를 켜고 쉬운 문제 1개만 푼다"),
          strategy(
              MissReason.UNWILLING, SuggestionLevel.EASY, 2, "하기 싫은 마음이 들면", "딱 2분만 하고 그만둘지 정한다"),
          strategy(
              MissReason.UNWILLING, SuggestionLevel.NORMAL, 1, "한 과목이 지루해지면", "30분마다 다른 과목으로 바꾼다"),
          strategy(
              MissReason.UNWILLING,
              SuggestionLevel.NORMAL,
              2,
              "집에서 공부가 안 되면",
              "도서관이나 독서실로 장소를 바꾼다"),
          strategy(
              MissReason.UNWILLING,
              SuggestionLevel.HARD,
              1,
              "공부 시작 시각을 정하면",
              "친구에게 알리고 같은 시각에 함께 시작한다"),
          strategy(
              MissReason.UNWILLING,
              SuggestionLevel.HARD,
              2,
              "미루고 싶은 과제가 생기면",
              "그날 가장 먼저 그 과제부터 한다"),
          // 기타 (이유를 잘 모를 때)
          // 미루다 보니 (#225 에서 더한 이유): 시작 문턱을 낮추고, 미루기 쉬운 공부를 앞에 둡니다.
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.EASY,
              1,
              "공부를 미루고 싶어지면",
              "타이머를 2분만 켜고 첫 줄만 읽는다"),
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.EASY,
              2,
              "시작 시각 10분 전이 되면",
              "책과 필기구를 책상에 펴 둔다"),
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.NORMAL,
              1,
              "딴 일을 먼저 하고 싶어지면",
              "공부 25분을 먼저 하고 그 일을 한다"),
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.NORMAL,
              2,
              "하루 계획을 세울 때면",
              "가장 미루기 쉬운 공부를 맨 앞에 둔다"),
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.HARD,
              1,
              "카드 시작 시각이 되면",
              "하던 일을 멈추고 바로 타이머를 켠다"),
          strategy(
              MissReason.PROCRASTINATED,
              SuggestionLevel.HARD,
              2,
              "같은 공부를 두 번 미루면",
              "다음 날 아침 첫 일정으로 옮긴다"),
          strategy(MissReason.ETC, SuggestionLevel.EASY, 1, "공부가 끝나면", "오늘 잘 안 된 순간을 한 줄 적는다"),
          strategy(MissReason.ETC, SuggestionLevel.EASY, 2, "계획을 세울 때면", "시작 시각을 같이 정해 둔다"),
          strategy(MissReason.ETC, SuggestionLevel.NORMAL, 1, "저녁을 먹고 나면", "바로 25분 타이머를 켠다"),
          strategy(
              MissReason.ETC, SuggestionLevel.NORMAL, 2, "계획을 못 채운 다음 날이면", "체크인에서 이유를 하나 골라 둔다"),
          strategy(
              MissReason.ETC, SuggestionLevel.HARD, 1, "정해 둔 공부 시각이 되면", "매일 같은 자리에서 공부를 시작한다"),
          strategy(
              MissReason.ETC, SuggestionLevel.HARD, 2, "일주일이 끝나면", "가장 잘 된 날과 안 된 날을 견줘 하나를 바꾼다"));

  // 값만 돌려주는 도구라서 객체를 만들 일이 없습니다.
  private StrategyCatalog() {}

  // 그 이유 · 그 강도의 전략들 (적힌 순서대로). 없으면 빈 목록입니다. (아픔은 언제나 빈 목록)
  public static List<Strategy> of(MissReason cause, SuggestionLevel level) {
    List<Strategy> found = new ArrayList<>();
    for (Strategy strategy : ALL) {
      if (strategy.cause() == cause && strategy.level() == level) {
        found.add(strategy);
      }
    }
    return found;
  }

  // 이름(key)으로 전략을 찾습니다. 없는 이름이면 빈 Optional 입니다.
  public static Optional<Strategy> find(String key) {
    for (Strategy strategy : ALL) {
      if (strategy.key().equals(key)) {
        return Optional.of(strategy);
      }
    }
    return Optional.empty();
  }

  // 전략 하나를 만듭니다. 이름은 "이유-강도-번호" 입니다. 예: (PHONE, EASY, 1) -> "phone-easy-1"
  private static Strategy strategy(
      MissReason cause, SuggestionLevel level, int number, String ifText, String thenText) {
    String key = cause.toValue() + "-" + level.toValue() + "-" + number;
    return new Strategy(key, cause, level, ifText, thenText);
  }
}
