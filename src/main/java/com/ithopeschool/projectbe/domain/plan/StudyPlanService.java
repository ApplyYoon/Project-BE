package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.plan.dto.StudyPlanResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 공부 계획 로직입니다. (이슈 #11, docs/API.md "공부 계획")
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 조회에 "내 userId" 조건이 붙어서 남의 계획은 섞이지 않습니다.
@Service
@RequiredArgsConstructor
public class StudyPlanService {

  private final CurrentUserProvider currentUserProvider;
  private final StudyPlanRepository studyPlanRepository;

  // 그날의 내 계획 목록 (만든 순서). 계획이 없는 날은 빈 목록입니다.
  //
  // 정렬: 지금은 계획에 시작 시각이 없어서 "만든 순서" 하나뿐입니다.
  // 시작 시각 칸이 생기면 "시작 시각이 있는 것 먼저 시각 순 → 없는 것은 만든 순" 으로 바꿉니다. (docs/API.md)
  @Transactional(readOnly = true)
  public List<StudyPlanResponse> getPlans(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();
    return studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date).stream()
        .map(StudyPlanResponse::from)
        .toList();
  }
}
