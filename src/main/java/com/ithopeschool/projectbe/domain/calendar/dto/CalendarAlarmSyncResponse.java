package com.ithopeschool.projectbe.domain.calendar.dto;

// 일정 올리기의 답입니다. 알림을 보낼 차례를 기다리는 일정이 몇 개 저장됐는지 알려 줍니다.   { "saved": 3 }
// (지난 일정 · 8일보다 먼 일정 · 같은 id 를 두 번 보낸 것은 세지 않습니다)
public record CalendarAlarmSyncResponse(int saved) {}
