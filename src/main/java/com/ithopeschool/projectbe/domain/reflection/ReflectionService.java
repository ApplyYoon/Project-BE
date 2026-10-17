package com.ithopeschool.projectbe.domain.reflection;

import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.call.CallRecordRepository;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.reflection.ReflectionRule.ReasonFact;
import com.ithopeschool.projectbe.domain.reflection.dto.ReflectionCurrentResponse;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak;
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

// 성찰일기 처방 로직입니다. (이슈 #61, docs/API.md "성찰일기 처방")
//
// 지금 처방을 물으면 이 순서로 봅니다.
//   1. 안전 브레이크가 걸렸으면 처방 없음. 진행 중인 처방이 있어도 보여 주지 않습니다.
//   2. 진행 중인 처방(오늘이 처방 기간 안)이 있으면 그것
//   3. 없으면 규칙(ReflectionRule)으로 새로 처방할지 정하고, 처방하면 저장해서 돌려줍니다.
//
// 3번 때문에 "조회(GET)" 인데 줄을 저장할 때가 있습니다. 처방은 처음 물어본 날 정해지고,
// 그 뒤 5일 동안은 같은 줄을 돌려줘서 질문이 바뀌지 않습니다. 몇 번을 불러도 결과는 같습니다.
@Service
@RequiredArgsConstructor
public class ReflectionService {

  private final CurrentUserProvider currentUserProvider;
  private final ReflectionPrescriptionRepository prescriptionRepository;
  private final CheckInRepository checkInRepository;
  private final StudySessionRepository studySessionRepository;
  private final CallRecordRepository callRecordRepository;
  private final Clock clock;

  // 지금 내 성찰일기 처방. 없으면 prescription 이 null 입니다.
  @Transactional
  public ReflectionCurrentResponse getCurrent() {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    // 이유 · 위기 표현 · 기분을 모두 최근 7일(오늘 포함)에서 봅니다.
    LocalDate from = today.minusDays(ReflectionRule.LOOKBACK_DAYS - 1);
    List<CheckIn> checkIns =
        checkInRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
            userId, from, today);

    // 1. 안전 브레이크는 어떤 규칙보다 먼저입니다. (CLAUDE.md 4장)
    if (isSafetyBrakeOn(userId, today, from, checkIns)) {
      return ReflectionCurrentResponse.none(today);
    }

    // 2. 진행 중인 처방
    Optional<ReflectionPrescription> latest =
        prescriptionRepository.findFirstByUserIdOrderByIdDesc(userId);
    if (latest.isPresent() && !today.isAfter(latest.get().getEndDate())) {
      return ReflectionCurrentResponse.of(today, latest.get());
    }

    // 3. 새로 처방할지
    LocalDate lastEndDate = latest.map(ReflectionPrescription::getEndDate).orElse(null);
    ReflectionRule.Decision decision =
        ReflectionRule.decide(today, reasonFactsOf(userId, from, today, checkIns), lastEndDate);
    if (decision == null) {
      return ReflectionCurrentResponse.none(today);
    }
    ReflectionPrescription saved =
        prescriptionRepository.save(
            new ReflectionPrescription(
                userId,
                today,
                today.plusDays(ReflectionRule.PRESCRIPTION_DAYS - 1),
                decision.reason(),
                decision.reasonDays(),
                ReflectionQuestions.of(decision.reason())));
    return ReflectionCurrentResponse.of(today, saved);
  }

  // 최근 7일에 위기 표현이 있었거나, 기분 저하가 6일 연속이면 true 입니다.
  // (AI 통화도 최근 7일 위기 표현이면 위로하는 통화로 바꿉니다. CallService.CRISIS_CARE_DAYS)
  private boolean isSafetyBrakeOn(
      Long userId, LocalDate today, LocalDate from, List<CheckIn> checkIns) {
    for (CallRecord call :
        callRecordRepository.findByUserIdAndCallDateGreaterThanEqualOrderByStartedAtDescIdDesc(
            userId, from)) {
      if (call.isCrisisDetected()) {
        return true;
      }
    }

    // 체크인의 글 칸(기타 이유 · 약속)과 기분
    List<String> texts = new ArrayList<>();
    List<LowMoodStreak.MoodRecord> moods = new ArrayList<>();
    for (CheckIn checkIn : checkIns) {
      texts.add(checkIn.getMissReasonNote());
      texts.add(checkIn.getPromiseIf());
      texts.add(checkIn.getPromiseThen());
      if (checkIn.getMood() != null) {
        moods.add(new LowMoodStreak.MoodRecord(checkIn.getCheckInDate(), checkIn.getMood()));
      }
    }
    return CrisisText.hasCrisisText(texts) || LowMoodStreak.hasLowMoodStreak(today, moods);
  }

  // 최근의 "못 채운 이유" 들: 체크인에서 고른 이유 + 타이머를 목표 전에 그만두며 고른 이유
  private List<ReasonFact> reasonFactsOf(
      Long userId, LocalDate from, LocalDate today, List<CheckIn> checkIns) {
    List<ReasonFact> facts = new ArrayList<>();
    for (CheckIn checkIn : checkIns) {
      // 체크인의 이유는 "어제" 계획을 못 채운 이유라서 하루 전 날짜로 셉니다.
      // (AI 통화에서 말한 이유도 통화 마무리 때 체크인에 들어옵니다. CallCheckIn)
      if (checkIn.getMissReason() != null) {
        facts.add(new ReasonFact(checkIn.getCheckInDate().minusDays(1), checkIn.getMissReason()));
      }
    }
    for (StudySession session :
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(from), DayRule.startOf(today.plusDays(1)))) {
      // 노는 기록은 공부가 아니라서 뺍니다.
      if (session.getKind() == PlanKind.STUDY && session.getStopReason() != null) {
        facts.add(new ReasonFact(DayRule.dateOf(session.getStartedAt()), session.getStopReason()));
      }
    }
    return facts;
  }
}
