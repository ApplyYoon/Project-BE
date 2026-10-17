package com.ithopeschool.projectbe.domain.cardresult;

import com.ithopeschool.projectbe.domain.session.SessionMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

// 판정에 넣는 카드 하루치 (FE src/lib/cardResult.ts 의 CardDay). 이슈 #229
// DB 를 모르는 값 묶음입니다. CardResultService 가 카드 · 세션 · 표시 · 휴식 날로 만들어 CardResultJudge 에 넘깁니다.
// 테스트에서는 이 모양을 직접 만들어서 FE 주석의 예시를 그대로 넣어 봅니다.
public record CardDay(
    // 카드의 시작 시각 (예: 19:00). 시작 시각이 없는 옛 카드면 null
    LocalTime startTime,
    // 카드 길이 (분)
    int durationMinutes,
    // 카드 체크리스트 항목의 id 목록 (ChecklistItem 의 itemKey, 예: "i1")
    List<String> checklistKeys,
    // 예상 달성 개수. null 이면 "체크리스트 전부" 로 봅니다
    Integer expectedDone,
    // 그 카드를 하는 날짜 (하루는 새벽 4시 기준, DayRule)
    LocalDate date,
    // 그 카드 · 그 날짜의 공부 세션 (노는 기록은 넣지 않습니다)
    List<Session> sessions,
    // 사람이 처리한 표시. REST(휴식) · POSTPONED(미루기) · FAILED(실패) 중 하나, 없으면 null
    CardResult mark) {

  // 판정에 넣는 세션 하나. 공부 기록(StudySession)에서 판정에 쓰는 칸만 꺼낸 것입니다.
  public record Session(
      // 시작한 시각
      Instant startedAt,
      // 순공시간 (밀리초, 일시정지 제외). StudyTime.studyMillis 로 계산한 값
      long studyMillis,
      // 잰 방법 (타이머 · 오프라인 · 사후 입력)
      SessionMode mode,
      // 예정 종료 전에 [끝내기] 를 눌렀는지 (중도 종료)
      boolean endedEarly,
      // 종료 팝업에서 체크한 체크리스트 항목 id
      Set<String> checkedKeys,
      // 산출물 증빙(링크나 요약)이 있는지
      boolean hasProof,
      // 관리 4단계라 증빙이 꼭 필요했던 세션인지. 증빙 없이 끝냈으면 실행으로 인정하지 않습니다 (FE #408)
      boolean proofRequired) {}
}
