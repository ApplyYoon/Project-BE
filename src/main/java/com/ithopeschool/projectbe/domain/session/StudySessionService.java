package com.ithopeschool.projectbe.domain.session;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 타이머 공부 기록 저장 로직입니다. (이슈 #13, docs/API.md "공부 기록")
//
// currentUserProvider.getCurrentUserId() 로 시작해서, 로그인하지 않은 요청은 401 이 나갑니다.
// 과목·계획은 "내 것" 인지 확인하고, 겹치는 기록도 "내 기록" 끼리만 봅니다.
@Service
@RequiredArgsConstructor
public class StudySessionService {

  // 순공이 이보다 짧으면 실수로 누른 것으로 보고 저장하지 않습니다. (FE 타이머와 같은 규칙: 1분)
  static final long MIN_STUDY_MILLIS = 60_000;

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
