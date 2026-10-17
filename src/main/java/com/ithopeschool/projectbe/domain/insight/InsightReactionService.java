package com.ithopeschool.projectbe.domain.insight;

import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionRequest;
import com.ithopeschool.projectbe.domain.insight.dto.InsightReactionResponse;
import com.ithopeschool.projectbe.domain.insight.dto.InsightStatsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 보여 준 인사이트를 기록하고, 반응 버튼을 저장하고, 다음 선정에 쓸 기록을 만드는 로직입니다. (이슈 #43 · #41)
//
// 사용자가 부르는 메서드(반응·통계)는 currentUserProvider.getCurrentUserId() 로 시작합니다.
// 내 번호로만 찾기 때문에 다른 사람의 인사이트에는 반응할 수 없습니다. (남의 것은 "없다" 고 답합니다)
// 인사이트 문장은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class InsightReactionService {

  private final CurrentUserProvider currentUserProvider;
  private final InsightRepository insightRepository;

  // 내 인사이트에 반응을 저장합니다. 이미 눌렀으면 새 반응으로 바꿉니다.
  @Transactional
  public InsightReactionResponse react(Long insightId, InsightReactionRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    InsightResponse reaction = InsightResponse.fromValue(request.response());
    if (reaction == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "반응은 surprised, alreadyKnew, wrong 중에서 골라 주세요");
    }

    Insight insight =
        insightRepository
            .findByIdAndUserId(insightId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "인사이트를 찾을 수 없어요"));
    // 값을 바꾸기만 하면, 이 메서드가 끝날 때 JPA 가 바뀐 것을 알아채고 DB 에 반영합니다.
    insight.react(reaction);
    return InsightReactionResponse.from(insight);
  }

  // 내 [몰랐는데 맞아] 비율. 반응을 누른 것 중에서 셉니다.
  //
  //   4개에 반응, 그중 3개가 몰랐는데 맞아  →  75
  //   3개에 반응, 그중 1개                 →  33   (33.3… 에서 소수점 버림)
  //   아직 하나도 안 누름                   →  null
  @Transactional(readOnly = true)
  public InsightStatsResponse getStats() {
    Long userId = currentUserProvider.getCurrentUserId();

    long reacted = insightRepository.countByUserIdAndReactionIsNotNull(userId);
    long surprised = insightRepository.countByUserIdAndReaction(userId, InsightResponse.SURPRISED);
    Integer percent = reacted == 0 ? null : (int) (surprised * 100 / reacted);
    return new InsightStatsResponse(reacted, surprised, percent);
  }

  // ---- 서버 안에서 부르는 메서드 (매일 밤 인사이트를 고르는 작업이 부릅니다) ----
  //
  // ⚠️ 주소(API)로 열지 않습니다. 요청한 사람이 없는 상황에서 부르므로, 받을 사람의 번호를 직접 받습니다.

  // 고른 인사이트를 "보여 준 인사이트" 로 남깁니다. 반응 칸은 비어 있습니다.
  @Transactional
  public List<Insight> recordShown(
      Long userId, List<InsightPicker.Picked> picked, LocalDate today) {
    List<Insight> saved = new ArrayList<>();
    for (InsightPicker.Picked one : picked) {
      InsightPicker.Candidate candidate = one.candidate();
      saved.add(
          insightRepository.save(
              new Insight(
                  userId,
                  candidate.sentence(),
                  candidate.key(),
                  one.isHypothesis(),
                  today,
                  candidate.evidenceDates())));
    }
    return saved;
  }

  // 다음 인사이트를 고를 때(InsightPicker.pick) 넘길 기록을 종류마다 하나씩 만듭니다. (#41 "반응이 다음 선정에 반영된다")
  //
  //   lastShownDate: 그 종류를 마지막으로 보여 준 날
  //   lastResponse : 그 종류에 가장 최근에 누른 반응. 마지막에 보여 준 것에 아직 안 눌렀으면, 그 전에 누른 반응입니다.
  //                  (한 번 "틀렸어" 를 누른 종류는 다시 보여 주지 않으므로, 그 반응이 계속 남아야 합니다)
  //
  //   morning 10/01 surprised, morning 10/15 (안 누름)  →  morning: 10/15, surprised
  @Transactional(readOnly = true)
  public List<InsightPicker.History> historiesOf(Long userId) {
    // 최근에 보여 준 것이 앞이라, 종류마다 처음 만나는 것이 가장 최근입니다.
    Map<String, LocalDate> lastShown = new LinkedHashMap<>();
    Map<String, InsightResponse> lastResponse = new LinkedHashMap<>();
    for (Insight insight : insightRepository.findByUserIdOrderByShownDateDescIdDesc(userId)) {
      lastShown.putIfAbsent(insight.getInsightKey(), insight.getShownDate());
      if (insight.getReaction() != null) {
        lastResponse.putIfAbsent(insight.getInsightKey(), insight.getReaction());
      }
    }

    List<InsightPicker.History> histories = new ArrayList<>();
    for (Map.Entry<String, LocalDate> entry : lastShown.entrySet()) {
      histories.add(
          new InsightPicker.History(
              entry.getKey(), entry.getValue(), lastResponse.get(entry.getKey())));
    }
    return histories;
  }
}
