package com.ithopeschool.projectbe.domain.push;

import com.ithopeschool.projectbe.domain.bias.CognitiveBias;

// 하루 한 번 보내는 "오늘의 작전" 알림 한 개의 내용입니다. (이슈 #32)
// 학생의 생각 습관에 맞춘 해 볼 행동 하나를 보냅니다. 문장은 생각 습관 목록(CognitiveBias)에 정해 둔 것이라 AI 가 지어내지 않습니다.
//
//   계획 오류        →  제목 "오늘의 작전" / 글 "다음 계획은 처음 예상한 시간의 1.5배로 잡아 보기 (계획 오류 대비)"
//   습관을 아직 모름  →  제목 "오늘의 한 걸음" / 글 "오늘 계획 하나, 5분만 먼저 시작해 볼까?"
//
// 이름은 생각에 붙입니다. "너는 ○○형" 같은 말은 쓰지 않습니다.
// title: 알림 제목 / body: 알림 글 (앱의 알림 목록에도 이 글이 남습니다) / url: 누르면 열 앱 안의 주소
public record HabitNudge(String title, String body, String url) {

  // 누르면 타이머로 갑니다. 알림을 보고 바로 시작할 수 있게.
  static final String URL = "/timer";

  public static HabitNudge of(CognitiveBias habit) {
    if (habit == null) {
      return new HabitNudge("오늘의 한 걸음", "오늘 계획 하나, 5분만 먼저 시작해 볼까?", URL);
    }
    return new HabitNudge("오늘의 작전", habit.getSolution() + " (" + habit.getLabel() + " 대비)", URL);
  }

  // 웹 푸시로 보낼 모양
  public WebPushSender.Payload toPayload() {
    return new WebPushSender.Payload(title, body, url);
  }
}
