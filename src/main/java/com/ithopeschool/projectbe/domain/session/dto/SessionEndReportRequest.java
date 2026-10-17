package com.ithopeschool.projectbe.domain.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

// 종료 팝업에서 받은 것의 요청 모양입니다. (FE 의 SessionEndReport)
//   { "checked": { "i1": 1790001000000, "i2": 1790002000000 }, "shortfallReason": "tooMuch",
//     "withWhat": "music", "proof": { "link": "https://…", "summary": "42~45쪽 풀었음" },
//     "goodReasons": ["focus"] }
// - checked: 체크한 체크리스트 항목의 id → 체크한 시각(밀리초). 20개까지. 안 보내면 하나도 체크 안 한 것입니다.
// - shortfallReason · withWhat · goodReasons 의 글자가 정해진 값인지는 Service 에서 검사합니다.
// - 모든 칸을 비워도 됩니다. 그래도 "팝업에 답했다" 는 것은 남습니다. (팝업을 닫아 버렸으면 endReport 를 안 보냅니다)
public record SessionEndReportRequest(
    @Size(max = 20, message = "체크한 항목은 20개까지 보낼 수 있어요") Map<String, Long> checked,
    String shortfallReason,
    String withWhat,
    @Valid Proof proof,
    @Size(max = 6, message = "잘 된 이유는 6개까지 고를 수 있어요") List<String> goodReasons) {

  // 산출물 증빙 — 링크 또는 2~3줄 요약 (사진은 저장소가 정해진 뒤)
  public record Proof(
      @Size(max = 300, message = "증빙 링크는 300자 이하로 보내 주세요") String link,
      @Size(max = 300, message = "증빙 요약은 300자 이하로 적어 주세요") String summary) {

    // 값이 들어오는 순간에 앞뒤 공백을 지웁니다.
    public Proof {
      link = link == null ? null : link.trim();
      summary = summary == null ? null : summary.trim();
    }
  }
}
