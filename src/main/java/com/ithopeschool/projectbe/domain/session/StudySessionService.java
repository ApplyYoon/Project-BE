package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.PlanProgress;
import com.ithopeschool.projectbe.domain.plan.PlanProgress.PlanSession;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.PlanActualResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.session.dto.StudyTotalResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 타이머 공부 기록 저장·조회 로직입니다. (이슈 #13 저장, 이슈 #14 조회, docs/API.md "공부 기록")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작해서, 로그인하지 않은 요청은 401 이 나갑니다.
// 과목·계획은 "내 것" 인지 확인하고, 겹치는 기록도 조회도 "내 기록" 끼리만 봅니다.
@Service
@RequiredArgsConstructor
public class StudySessionService {

  // 순공이 이보다 짧으면 실수로 누른 것으로 보고 저장하지 않습니다. (FE 타이머와 같은 규칙: 1분)
  static final long MIN_STUDY_MILLIS = 60_000;

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다. (10월 1일 ~ 10월 31일 = 31일)
  // 한 번에 너무 많은 기록을 꺼내지 않도록 정했습니다. (이슈 #14)
  static final int MAX_QUERY_DAYS = 31;

  // 조회할 수 있는 날짜의 범위입니다. 터무니없이 먼 날짜(+999999999-12-31 등)가 DB 까지 가서
  // 500 이 나지 않도록 입구에서 막습니다. 실제 기록은 모두 이 범위 안에 있습니다.
  static final LocalDate EARLIEST_QUERY_DATE = LocalDate.of(1970, 1, 1);
  static final LocalDate LATEST_QUERY_DATE = LocalDate.of(9999, 12, 31);

  // 이보다 앞선 시작 시각은 타이머 기록일 수 없어서 거절합니다. (이 서비스는 2026년에 만들어졌습니다)
  // 폰 시계가 초기화되면 1970년이 찍히는데, 그런 기록이 저장되면 "1970년 ~ 지금" 이 전부 겹쳐서
  // 그 뒤로는 어떤 기록도 저장할 수 없게 됩니다. 운영 DB(PostgreSQL)가 담지 못하는 아주 먼 과거 시각도 여기서 막힙니다.
  static final Instant EARLIEST_START = Instant.parse("2020-01-01T00:00:00Z");

  private final CurrentUserProvider currentUserProvider;
  private final StudySessionRepository studySessionRepository;
  // 기록에 붙일 과목이 "내 과목" 인지 확인할 때 씁니다.
  private final SubjectRepository subjectRepository;
  // 기록에 붙일 계획이 "내 계획" 인지 확인할 때 씁니다.
  private final StudyPlanRepository studyPlanRepository;
  // "지금 몇 시인지" 는 시계에게 묻습니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 공부 기록 하나 저장.
  //
  // 검사 순서: 값의 모양(400) → 내 과목·내 계획인지(404) → 이미 있는 기록과 겹치는지(409)
  // 하나라도 걸리면 저장하지 않습니다.
  @Transactional
  public StudySessionResponse create(StudySessionCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // kind 를 보내지 않으면 공부 기록입니다.
    PlanKind kind = request.kind() == null ? PlanKind.STUDY : parseKind(request.kind());
    Instant startedAt = Instant.ofEpochMilli(request.startedAt());
    Instant endedAt = Instant.ofEpochMilli(request.endedAt());
    // pausedMs 를 보내지 않으면 일시정지한 시간이 없는 것으로 봅니다.
    long pausedMs = request.pausedMs() == null ? 0 : request.pausedMs();

    checkTimes(startedAt, endedAt, pausedMs);
    MissReason stopReason = parseStopReason(request.stopReason());
    Long subjectId = checkSubject(userId, kind, request.subjectId());
    Long planId = checkPlan(userId, request.planId());

    // 내 기록 중에 시간이 겹치는 것이 있으면 거절합니다. (같은 시간을 두 번 세지 않도록)
    // 같은 요청이 동시에 와도 하나만 저장되도록, 겹침을 보기 전에 내 줄을 잠급니다. (StudySessionRepository.lockUser)
    studySessionRepository.lockUser(userId);
    if (studySessionRepository.existsByUserIdAndStartedAtLessThanAndEndedAtGreaterThan(
        userId, endedAt, startedAt)) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 저장된 기록과 시간이 겹쳐요");
    }

    StudySession saved =
        studySessionRepository.save(
            new StudySession(
                userId,
                subjectId,
                startedAt,
                endedAt,
                request.pauseCount(),
                pausedMs,
                planId,
                request.targetMinutes(),
                stopReason,
                kind));
    return StudySessionResponse.from(saved);
  }

  // 기간 안의 내 기록 목록. from ~ to 는 둘 다 포함하는 날짜이고, 없는 기간은 빈 목록입니다.
  //
  // "하루" 는 새벽 4시에 바뀌므로(DayRule) from 날의 04:00 부터 to 다음 날의 04:00 직전까지 "시작한" 기록을 찾습니다.
  // 04:00 를 걸친 기록(03:30 ~ 04:30)은 시작한 날의 기록 하나입니다. 노는 기록도 목록에는 들어 있습니다. (kind 로 구별)
  @Transactional(readOnly = true)
  public List<StudySessionResponse> getSessions(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkQueryDate(from);
    checkQueryDate(to);
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    return studySessionRepository
        .findStartedBetween(userId, DayRule.startOf(from), DayRule.startOf(to.plusDays(1)))
        .stream()
        .map(StudySessionResponse::from)
        .toList();
  }

  // 오늘의 순공시간(분)과 과목별 합계.
  //
  // "오늘" 은 지금 시각을 DayRule 로 바꾼 날짜입니다. 새벽 1시라면 아직 어제입니다. (docs/API.md 2-4)
  // 순공 = 끝 − 시작 − 일시정지한 시간이고, 노는 기록은 넣지 않습니다. (StudyTotal)
  @Transactional(readOnly = true)
  public StudyTotalResponse getTodayTotal() {
    Long userId = currentUserProvider.getCurrentUserId();

    LocalDate today = DayRule.dateOf(clock.instant());
    List<StudySession> sessions =
        studySessionRepository.findStartedBetween(
            userId, DayRule.startOf(today), DayRule.startOf(today.plusDays(1)));
    return StudyTotalResponse.of(today, StudyTotal.of(sessions));
  }

  // 그날의 내 계획마다 "실제 / 목표" 를 계산합니다. 계획 줄의 "32 / 50분" 과 진행 막대에 씁니다.
  //
  // 노는 계획도 포함합니다. (실제로 얼마나 놀았는지도 보여줘야 합니다 — FE 와 같은 규칙)
  // 계획 하나의 실제 시간 = 그 계획으로 켠 기록(planId 가 같은 기록)의 순공 합. 노는 기록도 더합니다.
  // 기록이 없는 계획도 0 으로 계산해서 돌려줍니다. 계획이 없는 날은 빈 목록입니다.
  // 계산은 PlanProgress 가 합니다. (FE 의 getPlanProgress 와 같은 규칙)
  @Transactional(readOnly = true)
  public List<PlanActualResponse> getPlanActuals(LocalDate date) {
    Long userId = currentUserProvider.getCurrentUserId();

    checkQueryDate(date);
    List<StudyPlan> plans = studyPlanRepository.findByUserIdAndPlanDateOrderById(userId, date);
    if (plans.isEmpty()) {
      return List.of();
    }

    List<Long> planIds = new ArrayList<>();
    for (StudyPlan plan : plans) {
      planIds.add(plan.getId());
    }
    // 기록 하나마다 (어느 계획으로 켰는지, 순공 밀리초) 만 꺼내 PlanProgress 에 넘깁니다.
    List<PlanSession> planSessions = new ArrayList<>();
    for (StudySession session : studySessionRepository.findByUserIdAndPlanIdIn(userId, planIds)) {
      long millis =
          StudyTime.studyMillis(
              session.getStartedAt(), session.getEndedAt(), session.getPausedMs());
      planSessions.add(new PlanSession(session.getPlanId(), millis));
    }

    List<PlanActualResponse> actuals = new ArrayList<>();
    for (StudyPlan plan : plans) {
      long actualMillis = PlanProgress.millisOf(plan.getId(), planSessions);
      actuals.add(
          PlanActualResponse.of(
              plan.getId(), PlanProgress.of(plan.getTargetMinutes(), actualMillis)));
    }
    return actuals;
  }

  // 조회하려는 날짜가 말이 되는 범위인지 봅니다. 아니면 400 입니다. (EARLIEST_QUERY_DATE 설명 참고)
  private void checkQueryDate(LocalDate date) {
    if (date.isBefore(EARLIEST_QUERY_DATE) || date.isAfter(LATEST_QUERY_DATE)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "날짜는 " + EARLIEST_QUERY_DATE + " 부터 " + LATEST_QUERY_DATE + " 사이로 보내 주세요");
    }
  }

  // 시각과 일시정지 시간이 말이 되는지 봅니다. 아니면 400 입니다.
  private void checkTimes(Instant startedAt, Instant endedAt, long pausedMs) {
    // 시계가 고장 난 기기에서 온 시각 (EARLIEST_START 설명 참고)
    if (startedAt.isBefore(EARLIEST_START)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 시각이 올바르지 않아요");
    }
    // 끝이 시작보다 앞서거나 같은 기록
    if (!endedAt.isAfter(startedAt)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "끝 시각은 시작 시각보다 뒤여야 해요");
    }
    // 아직 오지 않은 시각에 끝난 기록 (끝이 미래가 아니면 시작도 미래가 아닙니다)
    if (endedAt.isAfter(clock.instant())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "미래 시각은 저장할 수 없어요");
    }
    // 일시정지 시간은 0 이상이고 (끝 − 시작) 보다 작아야 합니다. (0 이상인지는 요청 DTO 가 먼저 봅니다)
    long totalMillis = endedAt.toEpochMilli() - startedAt.toEpochMilli();
    if (pausedMs < 0 || pausedMs >= totalMillis) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "일시정지 시간은 기록 길이보다 짧아야 해요");
    }
    // 1분 미만은 순공(끝 − 시작 − 일시정지) 기준으로 봅니다. 순공 계산은 StudyTime 이 합니다.
    if (StudyTime.studyMillis(startedAt, endedAt, pausedMs) < MIN_STUDY_MILLIS) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "1분 미만의 기록은 저장하지 않아요");
    }
  }

  // 종류에 맞는 과목 번호를 돌려줍니다. (계획과 같은 규칙)
  // - 노는 기록: 과목이 없습니다. 보냈더라도 null 로 저장합니다.
  // - 공부 기록: 과목이 꼭 있어야 하고(400), 내 과목이어야 합니다(404).
  private Long checkSubject(Long userId, PlanKind kind, String subjectId) {
    if (kind == PlanKind.PLAY) {
      return null;
    }
    if (subjectId == null || subjectId.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "공부 기록은 과목을 골라 주세요");
    }
    // 숫자 모양인지는 요청 DTO 의 @Pattern 이 이미 검사했습니다.
    Long id = Long.valueOf(subjectId);
    // 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (과목 API 와 같은 방식)
    if (subjectRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }
    return id;
  }

  // 계획 번호를 돌려줍니다. 안 보냈으면(null 또는 "") 계획 없이 켠 타이머라 null 입니다.
  // 보냈으면 내 계획이어야 합니다. 없는 계획과 남의 계획은 구별하지 않고 똑같이 404 입니다.
  private Long checkPlan(Long userId, String planId) {
    if (planId == null || planId.isEmpty()) {
      return null;
    }
    Long id = Long.valueOf(planId);
    if (studyPlanRepository.findByIdAndUserId(id, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없어요");
    }
    return id;
  }

  // FE 가 보낸 글자("study")를 종류로 바꿉니다. 두 가지가 아니면 400 입니다.
  private PlanKind parseKind(String value) {
    PlanKind kind = PlanKind.fromValue(value);
    if (kind == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "기록 종류는 study, play 중에서 골라 주세요");
    }
    return kind;
  }

  // FE 가 보낸 글자("phone")를 그만둔 이유로 바꿉니다. 안 보냈으면 null, 7가지가 아니면 400 입니다.
  private MissReason parseStopReason(String value) {
    if (value == null) {
      return null;
    }
    MissReason reason = MissReason.fromValue(value);
    if (reason == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "그만둔 이유는 tired, event, phone, tooMuch, unwilling, sick, etc 중에서 골라 주세요");
    }
    return reason;
  }
}
