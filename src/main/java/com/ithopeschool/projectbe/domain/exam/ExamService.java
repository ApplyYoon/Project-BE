package com.ithopeschool.projectbe.domain.exam;

import com.ithopeschool.projectbe.domain.exam.dto.ExamActualScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamCreateRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamExpectedScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamGoalRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResponse;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResultRequest;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 시험 목록·등록·실제 점수 입력 로직입니다. (이슈 #16, docs/API.md "시험 점수")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 모든 조회·수정에 "내 userId" 조건이 붙어서 남의 시험은 보이지도, 바뀌지도 않습니다.
// (남의 시험 번호를 보내면 "그런 번호가 없다" 는 것과 똑같이 404 로 답합니다)
@Service
@RequiredArgsConstructor
public class ExamService {

  private final CurrentUserProvider currentUserProvider;
  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  // 점수를 적을 과목이 "내 과목" 인지 확인할 때 씁니다.
  private final SubjectRepository subjectRepository;
  // 시험을 이을 목표가 "내 점수형 목표" 인지 확인할 때 씁니다. (이슈 #228)
  private final GoalRepository goalRepository;
  // "오늘" 이 며칠인지 알 때 씁니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 내 시험 목록. 시험 날짜가 늦은 것부터입니다. 시험이 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<ExamResponse> getExams() {
    return getExams(null);
  }

  // 내 시험 목록. goalId 를 주면 그 목표에 이어진 시험만 돌려줍니다. (이슈 #228, 목표 상세의 시험 목록)
  // 남의 목표 · 없는 목표면 404 입니다.
  @Transactional(readOnly = true)
  public List<ExamResponse> getExams(Long goalId) {
    Long userId = currentUserProvider.getCurrentUserId();
    List<Exam> exams;
    if (goalId == null) {
      exams = examRepository.findByUserIdOrderByExamDateDescIdDesc(userId);
    } else {
      findMyGoal(userId, goalId);
      exams = examRepository.findByUserIdAndGoalIdOrderByExamDateDescIdDesc(userId, goalId);
    }
    if (exams.isEmpty()) {
      return List.of();
    }

    // 시험마다 따로 묻지 않고, 모든 시험의 점수를 한 번에 가져와서 시험 번호별로 나눕니다.
    List<Long> examIds = exams.stream().map(Exam::getId).toList();
    Map<Long, List<ExamScore>> scoresByExam =
        examScoreRepository.findByUserIdAndExamIdInOrderById(userId, examIds).stream()
            .collect(Collectors.groupingBy(ExamScore::getExamId));

    return exams.stream()
        .map(exam -> ExamResponse.from(exam, scoresByExam.getOrDefault(exam.getId(), List.of())))
        .toList();
  }

  // 시험 등록: 시험 한 줄과 과목별 예상 점수를 같이 저장합니다.
  //
  // 저장하기 전에 모든 것을 먼저 검사합니다. 하나라도 틀리면 시험도 점수도 저장하지 않습니다.
  // (혹시 저장하는 도중에 에러가 나도, @Transactional 이 그때까지 저장한 것을 모두 되돌립니다)
  @Transactional
  public ExamResponse create(ExamCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    ExamType type = parseType(request.type());

    Set<Long> seen = new HashSet<>();
    for (ExamExpectedScoreRequest item : request.subjectScores()) {
      Long subjectId = Long.valueOf(item.subjectId());
      if (!seen.add(subjectId)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 과목이 두 번 들어 있어요");
      }
      checkMySubject(userId, subjectId);
    }

    // 목표를 보냈으면 내 점수형 목표인지 먼저 봅니다. (저장하기 전에 모두 검사)
    Long goalId = request.goalId() == null ? null : checkMyScoreGoal(userId, request.goalId());
    Exam newExam = new Exam(userId, request.name(), type, request.date());
    newExam.linkGoal(goalId);
    Exam exam = examRepository.save(newExam);
    List<ExamScore> scores = new ArrayList<>();
    for (ExamExpectedScoreRequest item : request.subjectScores()) {
      // 점수는 요청 검사가 "0~100 의 정수" 임을 이미 확인해서, intValueExact() 로 안전하게 정수가 됩니다.
      scores.add(
          new ExamScore(
              userId,
              exam.getId(),
              Long.valueOf(item.subjectId()),
              item.expectedScore().intValueExact()));
    }
    return ExamResponse.from(exam, examScoreRepository.saveAll(scores));
  }

  // 실제 점수 입력: 시험 날짜가 지난 뒤(다음 날부터)에만 됩니다.
  // 보낸 과목의 실제 점수만 넣고, 보내지 않은 과목은 그대로입니다. 이미 넣은 점수는 다시 보내서 고칠 수 있습니다.
  @Transactional
  public ExamResponse enterActualScores(Long id, ExamResultRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Exam exam = findMyExam(userId, id);

    // 시험 전에 적은 예상 점수를 결과를 보고 나서 고치지 못하게, 시험 당일에도 아직 못 넣습니다. (ExamDday 규칙 3)
    // "오늘" 은 하루의 기준(새벽 4시)으로 정합니다.
    LocalDate today = DayRule.dateOf(clock.instant());
    if (!ExamDday.canEnterActualScore(exam.getExamDate(), today)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요");
    }

    List<ExamScore> scores =
        examScoreRepository.findByUserIdAndExamIdOrderById(userId, exam.getId());
    Map<Long, ExamScore> scoreBySubject = new HashMap<>();
    for (ExamScore score : scores) {
      scoreBySubject.put(score.getSubjectId(), score);
    }

    // 먼저 모두 검사하고, 다 맞을 때만 값을 바꿉니다. 하나라도 틀리면 어느 과목도 바뀌지 않습니다.
    Set<Long> seen = new HashSet<>();
    for (ExamActualScoreRequest item : request.subjectScores()) {
      Long subjectId = Long.valueOf(item.subjectId());
      if (!seen.add(subjectId)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 과목이 두 번 들어 있어요");
      }
      // 이 시험에 예상 점수를 적어 둔 과목만 됩니다. (남의 과목이든 없는 과목이든 똑같이 이 문장입니다)
      if (!scoreBySubject.containsKey(subjectId)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "이 시험에 예상 점수가 없는 과목이에요");
      }
    }
    for (ExamActualScoreRequest item : request.subjectScores()) {
      // 점수는 요청 검사가 "0~100 의 정수" 임을 이미 확인해서, intValueExact() 로 안전하게 정수가 됩니다.
      scoreBySubject
          .get(Long.valueOf(item.subjectId()))
          .changeActualScore(item.actualScore().intValueExact());
    }

    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    return ExamResponse.from(exam, scores);
  }

  // 시험을 목표에 잇거나(goalId), 떼어 냅니다(null). (이슈 #228)
  // 점수형 목표에만 이을 수 있습니다. 완주형 목표(영단어장 1회독 등)는 시험 점수와 상관이 없어서 400 입니다.
  @Transactional
  public ExamResponse linkGoal(Long id, ExamGoalRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Exam exam = findMyExam(userId, id);
    exam.linkGoal(request.goalId() == null ? null : checkMyScoreGoal(userId, request.goalId()));
    return ExamResponse.from(
        exam, examScoreRepository.findByUserIdAndExamIdOrderById(userId, exam.getId()));
  }

  // 내 목표 중에서 번호로 찾습니다. 없는 번호이거나 남의 목표면 똑같이 404 입니다.
  private Goal findMyGoal(Long userId, Long goalId) {
    return goalRepository
        .findByIdAndUserId(goalId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "목표를 찾을 수 없어요"));
  }

  // 시험을 이을 수 있는 목표인지 봅니다. 내 점수형 목표면 그 번호를 돌려줍니다.
  private Long checkMyScoreGoal(Long userId, String goalId) {
    Goal goal = findMyGoal(userId, Long.valueOf(goalId));
    if (goal.getKind() != GoalKind.SCORE) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험은 점수형 목표에만 이을 수 있어요");
    }
    return goal.getId();
  }

  // 내 시험 중에서 번호로 찾습니다. 없는 번호이거나 남의 시험이면 똑같이 404 입니다.
  private Exam findMyExam(Long userId, Long id) {
    return examRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요"));
  }

  // 내 과목인지 확인합니다. 없는 과목과 남의 과목은 구별하지 않고 똑같이 404 입니다. (계획 API 와 같은 방식)
  private void checkMySubject(Long userId, Long subjectId) {
    if (subjectRepository.findByIdAndUserId(subjectId, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    }
  }

  // FE 가 보낸 글자("mock")를 종류로 바꿉니다. 네 가지가 아니면 400 입니다.
  private ExamType parseType(String value) {
    ExamType type = ExamType.fromValue(value);
    if (type == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "시험 종류는 mock, school, quiz, etc 중에서 골라 주세요");
    }
    return type;
  }
}
