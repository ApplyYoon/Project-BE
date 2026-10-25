package com.ithopeschool.projectbe.domain.manual;

import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentRule;
import com.ithopeschool.projectbe.domain.experiment.ExperimentService;
import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.insight.InsightRepository;
import com.ithopeschool.projectbe.domain.insight.InsightResponse;
import com.ithopeschool.projectbe.domain.manual.dto.ManualItemResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 나 사용설명서 로직입니다. (이슈 #46, docs/API.md "나 사용설명서")
//
// 사용설명서는 따로 저장하지 않고, 부를 때마다 아래 두 곳에서 "검증된 사실" 만 모아 새로 만듭니다.
//   1. 효과 있음으로 판정된 자기 실험 (#54)       → 분류 "효과 있는 방법"
//   2. 확실성 높은 인사이트 (ManualItem 의 규칙)   → 분류 "리듬" · "주의할 날"
// 사용자가 숨긴 항목은 manual_hidden_items 표에 이름표(id)만 적어 두고, 목록에서 뺍니다.
// 모든 조회는 currentUserProvider.getCurrentUserId() 로 얻은 내 번호로만 합니다. 로그인하지 않으면 401 입니다.
// 인사이트 문장은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class ManualService {

  // 인사이트 종류(insightKey) → 사용설명서 분류. 여기에 없는 종류는 올리지 않습니다.
  // ⚠️ 인사이트를 만드는 분석(#38 · #40)이 아직 없어서 진짜 종류 목록이 정해지지 않았습니다.
  //    docs/INSIGHT-PICK.md 의 예시 종류 둘만 넣어 두었으니, 분석이 생기면 팀이 정해서 고칩니다.
  private static final Map<String, ManualCategory> INSIGHT_CATEGORIES =
      Map.of(
          "morning-vs-night", ManualCategory.RHYTHM,
          "monday-drop", ManualCategory.CAUTION_DAY);

  private final CurrentUserProvider currentUserProvider;
  private final ExperimentService experimentService;
  private final InsightRepository insightRepository;
  private final ManualHiddenItemRepository manualHiddenItemRepository;

  // 내 사용설명서 항목들. 내가 숨긴 항목은 빠집니다. 최근에 확인된 것이 앞이고, 하나도 없으면 빈 배열입니다.
  // 끝난 실험을 이때 판정해 남길 수 있어서 읽기 전용(readOnly)이 아닙니다.
  @Transactional
  public List<ManualItemResponse> list() {
    Long userId = currentUserProvider.getCurrentUserId();

    Set<String> hiddenIds = new HashSet<>();
    for (ManualHiddenItem hidden : manualHiddenItemRepository.findByUserId(userId)) {
      hiddenIds.add(hidden.getItemId());
    }

    List<ManualItemResponse> visible = new ArrayList<>();
    for (ManualItemResponse item : collectItems(userId)) {
      if (!hiddenIds.contains(item.id())) {
        visible.add(item);
      }
    }
    return visible;
  }

  // 항목 하나를 숨깁니다. 숨긴 항목은 목록에서 빠집니다. 이미 숨겼으면 아무것도 바꾸지 않고 그대로 성공입니다.
  // 지금 내 사용설명서에 없는 이름표(없는 항목 · 남의 항목 · 아무 글자)는 구별하지 않고 404 입니다.
  @Transactional
  public void hide(String itemId) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 이미 숨긴 항목도 "있는 항목" 이어야 해서, 숨긴 것을 빼지 않은 전체에서 찾습니다.
    boolean exists = false;
    for (ManualItemResponse item : collectItems(userId)) {
      if (item.id().equals(itemId)) {
        exists = true;
        break;
      }
    }
    if (!exists) {
      throw new ApiException(ErrorCode.NOT_FOUND, "사용설명서 항목을 찾을 수 없어요");
    }

    if (!manualHiddenItemRepository.existsByUserIdAndItemId(userId, itemId)) {
      manualHiddenItemRepository.save(new ManualHiddenItem(userId, itemId));
    }
  }

  // 숨긴 것까지 포함한 내 사용설명서 전부. 최근에 확인된 것이 앞입니다.
  private List<ManualItemResponse> collectItems(Long userId) {
    List<ManualItemResponse> items = new ArrayList<>();
    items.addAll(experimentItems(userId));
    items.addAll(insightItems(userId));

    // 같은 날에 확인된 것끼리는 id 순서로 해서, 부를 때마다 같은 순서가 나오게 합니다.
    items.sort(
        Comparator.comparing(ManualItemResponse::confirmedDate)
            .reversed()
            .thenComparing(ManualItemResponse::id));
    return items;
  }

  // 효과 있음으로 판정된 내 실험들. id 는 "experiment-3" 처럼 실험 번호입니다.
  private List<ManualItemResponse> experimentItems(Long userId) {
    List<ManualItemResponse> items = new ArrayList<>();
    for (Experiment experiment : experimentService.getEffectiveExperiments(userId)) {
      // 판정한 날이 확인된 날입니다. 판정 시각이 비어 있는 줄이 있어도 끊기지 않게 실험이 끝난 날로 대신합니다.
      LocalDate confirmedDate =
          experiment.getJudgedAt() == null
              ? ExperimentRule.endDate(experiment.getStartDate())
              : DayRule.dateOf(experiment.getJudgedAt());

      ManualItem item =
          ManualItem.ofCardExperiment(
              experiment.getStrategy().getRule(),
              ExperimentRule.EXPERIMENT_DAYS,
              experiment.getBaselineRate(),
              experiment.getExperimentRate(),
              confirmedDate);
      items.add(ManualItemResponse.of("experiment-" + experiment.getId(), item));
    }
    return items;
  }

  // 확실성 높은 내 인사이트들. id 는 "insight-morning-vs-night" 처럼 종류(key)입니다.
  // 같은 종류를 4주 뒤에 다시 보여 줘도 id 가 바뀌지 않게 하려고, 인사이트 번호 대신 종류를 씁니다.
  private List<ManualItemResponse> insightItems(Long userId) {
    // 최근에 보여 준 것이 앞입니다.
    List<Insight> insights = insightRepository.findByUserIdOrderByShownDateDescIdDesc(userId);

    // 학생이 "틀렸어" 를 누른 종류는 통째로 뺍니다. (인사이트를 고를 때도 틀렸어를 누른 종류는 다시 고르지 않습니다)
    Set<String> wrongKeys = new HashSet<>();
    for (Insight insight : insights) {
      if (insight.getReaction() == InsightResponse.WRONG) {
        wrongKeys.add(insight.getInsightKey());
      }
    }

    // 같은 종류는 가장 최근 것 한 줄만 올립니다.
    Set<String> addedKeys = new HashSet<>();
    List<ManualItemResponse> items = new ArrayList<>();
    for (Insight insight : insights) {
      String key = insight.getInsightKey();
      ManualCategory category = INSIGHT_CATEGORIES.get(key);
      if (category == null
          || wrongKeys.contains(key)
          || addedKeys.contains(key)
          || !ManualItem.canRegisterInsight(insight)) {
        continue;
      }
      addedKeys.add(key);
      items.add(ManualItemResponse.of("insight-" + key, ManualItem.ofInsight(insight, category)));
    }
    return items;
  }
}
