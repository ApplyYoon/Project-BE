package com.ithopeschool.projectbe.domain.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// "공부 기록 저장" 요청의 모양입니다. (FE 의 StudySession 에서 id 를 뺀 것)
//   { "subjectId": "3", "startedAt": 1790000000000, "endedAt": 1790003000000,
//     "pauseCount": 2, "pausedMs": 300000, "planId": "7", "targetMinutes": 50,
//     "stopReason": "phone", "kind": "study" }
// 카드로 켠 세션 (이슈 #224, 최종 기획서 「4.4 타이머」):
//   { "subjectId": "", "startedAt": …, "endedAt": …, "pauseCount": 1, "targetMinutes": 60,
//     "kind": "study", "cardId": "7", "mode": "normal", "endedEarly": false,
//     "pauses": [ { "startedAt": …, "endedAt": …, "tag": "toilet" } ],
//     "endReport": { "checked": { "i1": … }, "withWhat": "music" },
//     "memos": [ { "at": …, "text": "엄마한테 문자" } ] }
// - 시각은 밀리초 숫자입니다. (docs/API.md 2-2)
// - pausedMs, planId, targetMinutes, stopReason, kind 와 카드 칸(cardId 아래)은 보내지 않아도 됩니다.
// - pauses 를 보내면 pauseCount · pausedMs 는 보낸 값 대신 pauses 로 계산해서 저장합니다. (FE summarizePauses 와 같음)
// - 노는 기록("kind": "play")은 subjectId 를 보내지 않거나 빈 글자("")로 보냅니다.
// - playTag · overRestMs 는 노는 기록에만 저장합니다.
// - 시각끼리 맞는지, 내 과목·계획·카드인지, 글자 칸들이 정해진 값인지는 Service 에서 검사합니다.
public record StudySessionCreateRequest(
    // FE 의 id 는 글자라서 글자로 받습니다. 숫자만 받습니다. ("3" 은 되고 "abc" 는 안 됨)
    // 18자리까지만 받습니다. 그보다 길면 서버의 숫자(Long)에 안 들어가서 500 이 나기 때문입니다.
    @Pattern(regexp = "\\d{0,18}", message = "과목 번호가 올바르지 않아요") String subjectId,
    @NotNull(message = "시작 시각을 보내 주세요") Long startedAt,
    @NotNull(message = "끝 시각을 보내 주세요") Long endedAt,
    @NotNull(message = "일시정지 횟수를 보내 주세요") @Min(value = 0, message = "일시정지 횟수는 0 이상이어야 해요")
        Integer pauseCount,
    // 안 보내면 0 으로 봅니다. (end − start) 보다 작은지는 Service 에서 봅니다.
    @Min(value = 0, message = "일시정지 시간은 0 이상이어야 해요") Long pausedMs,
    @Pattern(regexp = "\\d{0,18}", message = "계획 번호가 올바르지 않아요") String planId,
    // 계획의 목표 시간과 같은 범위입니다.
    @Min(value = 1, message = "목표 시간은 1분 이상이어야 해요")
        @Max(value = 600, message = "목표 시간은 600분(10시간) 이하여야 해요")
        Integer targetMinutes,
    String stopReason,
    String kind,
    // ---- 카드 기준으로 넓힌 칸 (이슈 #224) ----
    @Pattern(regexp = "\\d{0,18}", message = "카드 번호가 올바르지 않아요") String cardId,
    String mode,
    @Size(max = 200, message = "일시정지는 200번까지 보낼 수 있어요")
        List<@NotNull(message = "일시정지를 보내 주세요") @Valid SessionPauseRequest> pauses,
    Boolean endedEarly,
    @Valid SessionEndReportRequest endReport,
    @Size(max = 50, message = "메모는 50개까지 보낼 수 있어요")
        List<@NotNull(message = "메모를 보내 주세요") @Valid SessionMemoRequest> memos,
    String playTag,
    @Min(value = 0, message = "초과 휴식 시간은 0 이상이어야 해요") Long overRestMs) {

  // 카드 칸이 없는 옛 모양(9칸)으로 만듭니다. 카드 칸은 모두 비어 있습니다. (테스트와 예전 코드가 쓰는 짧은 모양)
  public StudySessionCreateRequest(
      String subjectId,
      Long startedAt,
      Long endedAt,
      Integer pauseCount,
      Long pausedMs,
      String planId,
      Integer targetMinutes,
      String stopReason,
      String kind) {
    this(
        subjectId,
        startedAt,
        endedAt,
        pauseCount,
        pausedMs,
        planId,
        targetMinutes,
        stopReason,
        kind,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
