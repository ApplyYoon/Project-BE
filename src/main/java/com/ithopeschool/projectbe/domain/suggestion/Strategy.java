package com.ithopeschool.projectbe.domain.suggestion;

import com.ithopeschool.projectbe.domain.care.MissReason;

// 전략 카탈로그(StrategyCatalog)에 적힌 전략 하나입니다. (이슈 #50)
// "만약 ~하면(ifText) → ~한다(thenText)" 한 문장입니다.
//
// key: 바뀌지 않는 이름. 예: "phone-easy-1" (이유-강도-번호)
//      나중에 고른 개선안(#52)과 효과 판정(#54)이 이 이름으로 전략을 가리킵니다. 그래서 한 번 정한 이름은 바꾸지 않습니다.
//      문장을 다듬는 것은 괜찮지만, 뜻이 달라지면 이름을 새로 만듭니다.
// cause: 어떤 "못 채운 이유" 에 맞춘 전략인지
// level: 쉬움 · 보통 · 강함
// ifText: "만약 ~하면" 부분. 예: "공부를 시작하면"
// thenText: "~한다" 부분. 예: "알림을 모두 끈다"
public record Strategy(
    String key, MissReason cause, SuggestionLevel level, String ifText, String thenText) {}
