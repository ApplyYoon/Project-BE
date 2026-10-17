package com.ithopeschool.projectbe.domain.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 일시정지 한 번의 요청 모양입니다. (FE 의 SessionPause)
//   { "startedAt": 1790000600000, "endedAt": 1790000900000, "tag": "toilet" }
//   { "startedAt": 1790001200000, "endedAt": 1790001500000, "tag": "other", "note": "택배 받음" }
// - 시각은 밀리초 숫자입니다. 기록의 시작과 끝 사이여야 합니다. (Service 에서 검사)
// - tag 는 toilet · snack · phone · mindWander · stuck · external · other 중 하나입니다. (Service 에서 검사)
public record SessionPauseRequest(
    @NotNull(message = "일시정지를 누른 시각을 보내 주세요") Long startedAt,
    @NotNull(message = "다시 시작한 시각을 보내 주세요") Long endedAt,
    @NotBlank(message = "일시정지 이유를 보내 주세요") String tag,
    @Size(max = 100, message = "일시정지 메모는 100자 이하로 적어 주세요") String note) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
  public SessionPauseRequest {
    note = note == null ? null : note.trim();
  }
}
