package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CheckCallResponse;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.session.StudySessionRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "확인 통화" 에 쓸 오늘 끝낸 공부를 서버의 기록으로 찾습니다. (2026-10-18, 규칙은 CheckCall)
//   - 통화 시작(CallService.startSession): 끝낸 공부가 있으면 대본에 [방금 공부한 것] 을 붙임
//   - 열림 확인(GET /api/v1/realtime/check-call): FE 가 [코치에게 설명하기] 버튼에 씀
// 통화를 여는 조건은 2026-10-22 에 뺐습니다. 문은 늘 열려 있습니다. (CheckCall 맨 위 설명)
//
// ⚠️ 공부 기록은 FE 가 끝나고 10분 뒤에 서버로 보냅니다. (FE docs/SERVER-SYNC.md)
//    그래서 방금 끝낸 공부로 바로 확인 통화를 하려면 FE 가 그 기록을 먼저 보낸 뒤 통화를 걸어야 합니다.
@Service
@RequiredArgsConstructor
public class CheckCallService {

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  private final CardRepository cardRepository;
  private final Clock clock;

  // 내 확인 통화 문 (GET /api/v1/realtime/check-call). 로그인하지 않은 요청은 401 입니다.
  @Transactional(readOnly = true)
  public CheckCallResponse check() {
    Long userId = currentUserProvider.getCurrentUserId();
    return CheckCallResponse.of(gateOf(userId, DayRule.dateOf(clock.instant())));
  }

  // 그 학생의 오늘 문. 늘 열려 있고, 오늘 끝낸 공부가 있으면 같이 돌려줍니다. (없으면 studied 가 null)
  @Transactional(readOnly = true)
  public CheckCall.Gate gateOf(Long userId, LocalDate today) {
    return CheckCall.Gate.open(studiedToday(userId, today).orElse(null));
  }

  // 오늘(새벽 4시 기준) 시작한 기록 중 끝낸 공부로 치는 것의 가장 최근 것.
  // 카드가 있으면 제목 · 체크리스트도, 종료 팝업에 쓴 요약이 있으면 메모로 붙입니다.
  private Optional<CheckCall.Studied> studiedToday(Long userId, LocalDate today) {
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(today), DayRule.startOf(today.plusDays(1)));
    return CheckCall.latestCounted(sessions).map(session -> studiedOf(userId, session));
  }

  private CheckCall.Studied studiedOf(Long userId, StudySession session) {
    String title = null;
    List<String> checklist = new ArrayList<>();
    if (session.getCardId() != null) {
      Optional<Card> card = cardRepository.findByIdAndUserId(session.getCardId(), userId);
      if (card.isPresent()) {
        title = card.get().getTitle();
        for (ChecklistItem item : card.get().getChecklist()) {
          checklist.add(item.getText());
        }
      }
    }
    return new CheckCall.Studied(
        title,
        checklist,
        CheckCall.minutesOf(session),
        session.getEndedAt(),
        session.getProofSummary());
  }
}
