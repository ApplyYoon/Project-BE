package com.ithopeschool.projectbe.domain.restday.dto;

import com.ithopeschool.projectbe.domain.restday.RestDay;
import java.time.LocalDate;

// 휴식 처리한 날 하나를 FE 에 알려 주는 모양입니다.
//   { "date": "2026-10-12", "createdAt": 1790000000000 }
// - 누구의 휴식인지(userId)는 담지 않습니다.
public record RestDayResponse(LocalDate date, long createdAt) {

  public static RestDayResponse from(RestDay restDay) {
    return new RestDayResponse(restDay.getRestDate(), restDay.getCreatedAt().toEpochMilli());
  }
}
