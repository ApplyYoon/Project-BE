package com.ithopeschool.projectbe.domain.experiment;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.experiment.dto.ExperimentResponse;
import com.ithopeschool.projectbe.domain.experiment.dto.ExperimentStartRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 자기 실험을 시작 · 조회하고, 14일이 끝난 실험을 판정해 남깁니다. (이슈 #52 · #53 · #54, docs/API.md "자기 실험")
//
// 판정 규칙은 ExperimentRule 에 있고, 여기서는 그 사람의 카드 기록을 꺼내 넘기기만 합니다.
// 끝난 실험은 목록을 볼 때나 새 실험을 시작할 때 판정합니다. 한 번 판정하면 바꾸지 않습니다.
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 남의 실험 · 남의 카드는 보이지 않습니다.
@Service
@RequiredArgsConstructor
public class ExperimentService {

  // 한 번에 진행할 수 있는 실험 수. 많으면 무엇이 효과였는지 나눌 수 없습니다.
  static final int MAX_RUNNING = 2;
  // 시작 날짜는 오늘부터 이만큼 전까지 받습니다. 기기에서 먼저 시작한 실험을 서버에 올릴 수 있게 하려는 것입니다.
  static final int MAX_PAST_START_DAYS = ExperimentRule.EXPERIMENT_DAYS - 1;

  private final CurrentUserProvider currentUserProvider;
  private final ExperimentRepository experimentRepository;
  private final CardRepository cardRepository;
  private final CardResultService cardResultService;
  private final Clock clock;

  // 실험 시작. 전략 · 원인 값 · 날짜가 틀리면 400, 내 카드가 아니면 404, 진행 중인 실험이 꽉 찼거나 카드가 겹치면 409
  @Transactional
  public ExperimentResponse start(ExperimentStartRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);

    ExperimentStrategy strategy = ExperimentStrategy.fromId(request.strategyId());
    if (strategy == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "전략은 정해진 10개 중 하나의 id 로 보내 주세요");
    }
    MissReason cause = null;
    if (request.cause() != null && !request.cause().isBlank()) {
      cause = MissReason.fromValue(request.cause());
      if (cause == null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "원인은 카드 미실행 원인 값으로 보내 주세요");
      }
    }
    LocalDate startDate = request.startDate() == null ? today : request.startDate();
    if (startDate.isAfter(today) || startDate.isBefore(today.minusDays(MAX_PAST_START_DAYS))) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "시작 날짜는 " + MAX_PAST_START_DAYS + "일 전부터 오늘까지만 고를 수 있어요");
    }
    Set<Long> cardIds = myCardIds(userId, request.cardIds());

    // 끝난 실험을 먼저 판정해야 "진행 중" 을 바르게 셉니다.
    judgeEnded(userId, today, now);
    List<Experiment> running =
        experimentRepository.findByUserIdAndStatus(userId, ExperimentStatus.RUNNING);
    if (running.size() >= MAX_RUNNING) {
      throw new ApiException(ErrorCode.CONFLICT, "실험은 한 번에 " + MAX_RUNNING + "개까지 할 수 있어요");
    }
    for (Experiment other : running) {
      for (Long cardId : cardIds) {
        if (other.getCardIds().contains(cardId)) {
          throw new ApiException(ErrorCode.CONFLICT, "그 카드에는 이미 진행 중인 실험이 붙어 있어요");
        }
      }
    }

    int minGainPp =
        request.minGainPp() == null ? ExperimentRule.DEFAULT_MIN_GAIN_PP : request.minGainPp();
    Experiment saved =
        experimentRepository.save(
            new Experiment(
                userId,
                request.hypothesis().trim(),
                cause,
                strategy,
                cardIds,
                startDate,
                minGainPp,
                now));
    List<JudgedCardDay> days = cardResultService.getJudgedDays(userId, startDate, today);
    return ExperimentResponse.from(
        saved, today, ExperimentRule.fulfillment(saved.getCardIds(), startDate, days));
  }

  // 내 실험 목록 (늦게 시작한 것부터). 14일이 끝난 진행 중 실험은 여기서 판정해 남깁니다.
  @Transactional
  public List<ExperimentResponse> list() {
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    judgeEnded(userId, today, now);

    List<Experiment> experiments =
        experimentRepository.findByUserIdOrderByStartDateDescIdDesc(userId);
    if (experiments.isEmpty()) {
      return List.of();
    }
    // 이행률을 셀 카드 기록을 한 번에 읽습니다. (가장 먼저 시작한 실험의 시작일부터 오늘까지)
    LocalDate from = experiments.get(experiments.size() - 1).getStartDate();
    List<JudgedCardDay> days = cardResultService.getJudgedDays(userId, from, today);
    List<ExperimentResponse> items = new ArrayList<>();
    for (Experiment experiment : experiments) {
      items.add(
          ExperimentResponse.from(
              experiment,
              today,
              ExperimentRule.fulfillment(
                  experiment.getCardIds(), experiment.getStartDate(), days)));
    }
    return items;
  }

  // 14일이 끝난 진행 중 실험을 판정합니다. 기준선(시작 전 14일)부터 마지막 날까지의 카드 기록을 씁니다.
  private void judgeEnded(Long userId, LocalDate today, Instant now) {
    for (Experiment experiment :
        experimentRepository.findByUserIdAndStatus(userId, ExperimentStatus.RUNNING)) {
      LocalDate start = experiment.getStartDate();
      if (!ExperimentRule.isJudgeDay(start, today)) {
        continue;
      }
      List<JudgedCardDay> days =
          cardResultService.getJudgedDays(
              userId,
              start.minusDays(ExperimentRule.EXPERIMENT_DAYS),
              ExperimentRule.endDate(start));
      experiment.judge(
          ExperimentRule.judge(experiment.getCardIds(), start, experiment.getMinGainPp(), days),
          now);
    }
  }

  // 보낸 카드 번호들이 모두 내 카드인지 확인하고 번호 묶음으로 돌려줍니다. 같은 번호를 두 번 보내면 하나로 칩니다.
  // 없는 카드와 남의 카드는 구별하지 않고 404 입니다. (계획 · 카드 API 와 같은 방식)
  private Set<Long> myCardIds(Long userId, List<String> requested) {
    Set<Long> ids = new LinkedHashSet<>();
    for (String id : requested) {
      ids.add(Long.valueOf(id));
    }
    List<Card> mine = cardRepository.findByUserIdAndIdIn(userId, ids);
    if (mine.size() != ids.size()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없어요");
    }
    return ids;
  }
}
