package com.ithopeschool.projectbe.domain.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.exam.dto.ExamActualScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamCreateRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamExpectedScoreRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamGoalRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResponse;
import com.ithopeschool.projectbe.domain.exam.dto.ExamResultRequest;
import com.ithopeschool.projectbe.domain.exam.dto.ExamSubjectScoreResponse;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.goal.GoalRepository;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// 시험 로직의 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에서 내 것만 나오는지는 ExamRepositoryTest 가 확인합니다.
class ExamServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;
  // 다른 사람의 번호
  private static final Long OTHER = 2L;
  // 내 과목 번호 (가짜 DB 에 "내 것" 으로 들어 있음)
  private static final Long MATH = 3L;
  private static final Long ENGLISH = 4L;
  private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

  // 지금 시각: 2026-10-21 낮 12시(한국 시간). 하루의 기준(새벽 4시)으로 "오늘" 은 10월 21일입니다.
  private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");

  private CurrentUserProvider currentUserProvider;
  private ExamRepository examRepository;
  private ExamScoreRepository examScoreRepository;
  private SubjectRepository subjectRepository;
  private GoalRepository goalRepository;
  private ExamService examService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    examRepository = mock(ExamRepository.class);
    examScoreRepository = mock(ExamScoreRepository.class);
    subjectRepository = mock(SubjectRepository.class);
    goalRepository = mock(GoalRepository.class);
    examService = serviceAt(NOW);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 3번(수학)과 4번(영어)은 내 과목이고, 그 밖의 번호는 없는 과목이거나 남의 과목입니다.
    given(subjectRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(subjectRepository.findByIdAndUserId(MATH, ME))
        .willReturn(Optional.of(new Subject(ME, "수학", SubjectColor.BLUE)));
    given(subjectRepository.findByIdAndUserId(ENGLISH, ME))
        .willReturn(Optional.of(new Subject(ME, "영어", SubjectColor.PURPLE)));
    // 저장하면 DB 가 번호를 매겨 준 것처럼 돌려줍니다.
    given(examRepository.save(any()))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 100L));
    given(examScoreRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  // 정한 시각(now)에 부르는 Service 를 만듭니다. (시각을 바꿔 "오늘" 이 달라지게 할 때 씁니다)
  private ExamService serviceAt(Instant now) {
    return new ExamService(
        currentUserProvider,
        examRepository,
        examScoreRepository,
        subjectRepository,
        goalRepository,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static Exam withId(Exam exam, long id) {
    ReflectionTestUtils.setField(exam, "id", id);
    return exam;
  }

  // DB 에 이미 있는 내 시험
  private static Exam exam(long id, String name, ExamType type, LocalDate date) {
    return withId(new Exam(ME, name, type, date), id);
  }

  // DB 에 이미 있는 내 과목 점수 (실제 점수는 아직 없음)
  private static ExamScore score(long examId, long subjectId, int expectedScore) {
    return new ExamScore(ME, examId, subjectId, expectedScore);
  }

  private static ExamCreateRequest create(String type, ExamExpectedScoreRequest... subjectScores) {
    return new ExamCreateRequest(type, "10월 모의고사", OCT_20, List.of(subjectScores));
  }

  private static ExamExpectedScoreRequest expected(String subjectId, int expectedScore) {
    return new ExamExpectedScoreRequest(subjectId, BigDecimal.valueOf(expectedScore));
  }

  private static ExamResultRequest result(ExamActualScoreRequest... subjectScores) {
    return new ExamResultRequest(List.of(subjectScores));
  }

  private static ExamActualScoreRequest actual(String subjectId, int actualScore) {
    return new ExamActualScoreRequest(subjectId, BigDecimal.valueOf(actualScore));
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인하지 않았으면 세 가지 모두 401 이고, DB 는 건드리지 않는다")
  void everythingRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));
    String message = "로그인이 필요해요";

    assertError(() -> examService.getExams(), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () -> examService.create(create("mock", expected("3", 85))),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(
        () -> examService.enterActualScores(7L, result(actual("3", 80))),
        ErrorCode.UNAUTHORIZED,
        message);
    verifyNoInteractions(examRepository, examScoreRepository);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("내 번호로 묻고, 시험마다 자기 과목 점수를 붙여 FE 모양으로 돌려준다")
  void getExams() {
    Exam mock = exam(7, "10월 모의고사", ExamType.MOCK, OCT_20);
    Exam quiz = exam(8, "수학 쪽지시험", ExamType.QUIZ, LocalDate.of(2026, 10, 10));
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(ME)).willReturn(List.of(mock, quiz));
    ExamScore mockMath = score(7, MATH, 85);
    mockMath.changeActualScore(80);
    given(examScoreRepository.findByUserIdAndExamIdInOrderById(ME, List.of(7L, 8L)))
        .willReturn(List.of(mockMath, score(7, ENGLISH, 70), score(8, MATH, 95)));

    List<ExamResponse> result = examService.getExams();

    verify(examRepository).findByUserIdOrderByExamDateDescIdDesc(ME);
    assertThat(result)
        .containsExactly(
            new ExamResponse(
                "7",
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(
                    new ExamSubjectScoreResponse("3", 85, 80),
                    new ExamSubjectScoreResponse("4", 70, null))),
            new ExamResponse(
                "8",
                "quiz",
                "수학 쪽지시험",
                LocalDate.of(2026, 10, 10),
                List.of(new ExamSubjectScoreResponse("3", 95, null))));
  }

  @Test
  @DisplayName("시험이 없으면 빈 목록이고, 점수는 묻지도 않는다")
  void emptyList() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(ME)).willReturn(List.of());

    assertThat(examService.getExams()).isEmpty();
    verifyNoInteractions(examScoreRepository);
  }

  @Test
  @DisplayName("점수가 하나도 없는 시험은 subjectScores 가 빈 목록")
  void examWithoutScores() {
    given(examRepository.findByUserIdOrderByExamDateDescIdDesc(ME))
        .willReturn(List.of(exam(7, "기타 시험", ExamType.ETC, OCT_20)));
    given(examScoreRepository.findByUserIdAndExamIdInOrderById(ME, List.of(7L)))
        .willReturn(List.of());

    assertThat(examService.getExams())
        .containsExactly(new ExamResponse("7", "etc", "기타 시험", OCT_20, List.of()));
  }

  // ---- 등록 ----

  @Test
  @DisplayName("등록하면 내 번호로 시험 한 줄과 과목별 예상 점수를 저장하고, 실제 점수는 비워 둔다")
  void create() {
    ExamResponse response =
        examService.create(create("mock", expected("3", 85), expected("4", 70)));

    ArgumentCaptor<Exam> savedExam = ArgumentCaptor.forClass(Exam.class);
    verify(examRepository).save(savedExam.capture());
    assertThat(savedExam.getValue().getUserId()).isEqualTo(ME);
    assertThat(savedExam.getValue().getName()).isEqualTo("10월 모의고사");
    assertThat(savedExam.getValue().getType()).isEqualTo(ExamType.MOCK);
    assertThat(savedExam.getValue().getExamDate()).isEqualTo(OCT_20);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ExamScore>> savedScores = ArgumentCaptor.forClass(List.class);
    verify(examScoreRepository).saveAll(savedScores.capture());
    assertThat(savedScores.getValue())
        .extracting(
            ExamScore::getUserId,
            ExamScore::getExamId,
            ExamScore::getSubjectId,
            ExamScore::getExpectedScore,
            ExamScore::getActualScore)
        .containsExactly(tuple(ME, 100L, MATH, 85, null), tuple(ME, 100L, ENGLISH, 70, null));

    assertThat(response)
        .isEqualTo(
            new ExamResponse(
                "100",
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(
                    new ExamSubjectScoreResponse("3", 85, null),
                    new ExamSubjectScoreResponse("4", 70, null))));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({"mock, MOCK", "school, SCHOOL", "quiz, QUIZ", "etc, ETC"})
  @DisplayName("네 가지 종류 모두 등록된다")
  void createAcceptsAllTypes(String value, ExamType expectedType) {
    ExamResponse response = examService.create(create(value, expected("3", 85)));

    ArgumentCaptor<Exam> savedExam = ArgumentCaptor.forClass(Exam.class);
    verify(examRepository).save(savedExam.capture());
    assertThat(savedExam.getValue().getType()).isEqualTo(expectedType);
    assertThat(response.type()).isEqualTo(value);
  }

  @ParameterizedTest(name = "종류 \"{0}\" 은 400")
  @CsvSource({"midterm", "MOCK", "모의고사", "'  '"})
  @DisplayName("네 가지가 아닌 종류는 400 이고, 아무것도 저장하지 않는다")
  void createWithWrongType(String type) {
    assertError(
        () -> examService.create(create(type, expected("3", 85))),
        ErrorCode.VALIDATION_ERROR,
        "시험 종류는 mock, school, quiz, etc 중에서 골라 주세요");
    verify(examRepository, never()).save(any());
    verify(examScoreRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("같은 과목을 두 번 보내면 400 이고, 아무것도 저장하지 않는다")
  void createWithDuplicateSubject() {
    assertError(
        () -> examService.create(create("mock", expected("3", 85), expected("3", 90))),
        ErrorCode.VALIDATION_ERROR,
        "같은 과목이 두 번 들어 있어요");
    verify(examRepository, never()).save(any());
    verify(examScoreRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("없는 과목이나 남의 과목이 하나라도 있으면 404 이고, 아무것도 저장하지 않는다")
  void createWithOthersSubject() {
    // 99번은 없는 과목이거나 남의 과목입니다. 앞의 3번이 내 과목이어도 시험은 저장되지 않아야 합니다.
    assertError(
        () -> examService.create(create("mock", expected("3", 85), expected("99", 70))),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    verify(examRepository, never()).save(any());
    verify(examScoreRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("과목이 내 것인지 내 번호(userId)로 묻는다")
  void createChecksSubjectWithMyUserId() {
    examService.create(create("mock", expected("3", 85)));

    verify(subjectRepository).findByIdAndUserId(MATH, ME);
  }

  // ---- 실제 점수 입력 ----

  // 10월 20일에 본 시험 (지금은 10월 21일이라 날짜가 지났습니다) 과 그 점수 두 개를 가짜 DB 에 넣어 둡니다.
  private List<ExamScore> givenPastExam() {
    Exam exam = exam(7, "10월 모의고사", ExamType.MOCK, OCT_20);
    given(examRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(exam));
    List<ExamScore> scores = List.of(score(7, MATH, 85), score(7, ENGLISH, 70));
    given(examScoreRepository.findByUserIdAndExamIdOrderById(ME, 7L)).willReturn(scores);
    return scores;
  }

  @Test
  @DisplayName("시험 다음 날부터 실제 점수를 넣을 수 있다. 보낸 과목만 바뀌고 나머지는 그대로")
  void enterActualScores() {
    List<ExamScore> scores = givenPastExam();

    ExamResponse response = examService.enterActualScores(7L, result(actual("3", 80)));

    assertThat(scores.get(0).getActualScore()).isEqualTo(80);
    // 보내지 않은 영어는 그대로 비어 있습니다.
    assertThat(scores.get(1).getActualScore()).isNull();
    assertThat(response)
        .isEqualTo(
            new ExamResponse(
                "7",
                "mock",
                "10월 모의고사",
                OCT_20,
                List.of(
                    new ExamSubjectScoreResponse("3", 85, 80),
                    new ExamSubjectScoreResponse("4", 70, null))));
  }

  @Test
  @DisplayName("여러 과목을 한 번에 넣을 수 있고, 점수 0 과 100 도 된다")
  void enterSeveralActualScores() {
    List<ExamScore> scores = givenPastExam();

    examService.enterActualScores(7L, result(actual("3", 100), actual("4", 0)));

    assertThat(scores.get(0).getActualScore()).isEqualTo(100);
    assertThat(scores.get(1).getActualScore()).isEqualTo(0);
  }

  @Test
  @DisplayName("이미 넣은 실제 점수는 다시 보내서 고칠 수 있다")
  void actualScoreCanBeCorrected() {
    List<ExamScore> scores = givenPastExam();
    examService.enterActualScores(7L, result(actual("3", 80)));

    examService.enterActualScores(7L, result(actual("3", 88)));

    assertThat(scores.get(0).getActualScore()).isEqualTo(88);
  }

  @ParameterizedTest(name = "시험 {0} 이면 지금(10/21)은 400")
  @CsvSource({"2026-10-21", "2026-10-22", "2027-01-01"})
  @DisplayName("시험 당일이거나 시험 날짜 전이면 400 이고, 점수는 바뀌지 않는다")
  void enterActualScoresBeforeExamDayEnds(LocalDate examDate) {
    Exam exam = exam(7, "10월 모의고사", ExamType.MOCK, examDate);
    given(examRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(exam));
    ExamScore math = score(7, MATH, 85);
    given(examScoreRepository.findByUserIdAndExamIdOrderById(ME, 7L)).willReturn(List.of(math));

    assertError(
        () -> examService.enterActualScores(7L, result(actual("3", 80))),
        ErrorCode.VALIDATION_ERROR,
        "시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요");
    assertThat(math.getActualScore()).isNull();
  }

  @Test
  @DisplayName("날짜는 새벽 4시에 바뀐다: 시험 다음 날 새벽 3시 59분까지는 아직 안 되고, 4시부터 된다")
  void dayChangesAtFourAm() {
    // 한국 시간 10/21 03:59 = 10/20 18:59 UTC → 하루의 기준으로는 아직 10/20 (시험 당일)
    ExamService beforeFour = serviceAt(Instant.parse("2026-10-20T18:59:00Z"));
    // 한국 시간 10/21 04:00 = 10/20 19:00 UTC → 10/21 (시험 다음 날)
    ExamService afterFour = serviceAt(Instant.parse("2026-10-20T19:00:00Z"));
    Exam exam = exam(7, "10월 모의고사", ExamType.MOCK, OCT_20);
    given(examRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(exam));
    ExamScore math = score(7, MATH, 85);
    given(examScoreRepository.findByUserIdAndExamIdOrderById(ME, 7L)).willReturn(List.of(math));

    assertError(
        () -> beforeFour.enterActualScores(7L, result(actual("3", 80))),
        ErrorCode.VALIDATION_ERROR,
        "시험 날짜가 지난 뒤에 실제 점수를 넣을 수 있어요");
    afterFour.enterActualScores(7L, result(actual("3", 80)));

    assertThat(math.getActualScore()).isEqualTo(80);
  }

  @Test
  @DisplayName("없는 시험이거나 남의 시험이면 404 이고, 점수는 묻지도 않는다")
  void enterActualScoresOfOthersExam() {
    // 99번 시험은 내 것으로는 없습니다. (없는 번호이거나 남의 시험)
    given(examRepository.findByIdAndUserId(99L, ME)).willReturn(Optional.empty());

    assertError(
        () -> examService.enterActualScores(99L, result(actual("3", 80))),
        ErrorCode.NOT_FOUND,
        "시험을 찾을 수 없어요");
    verify(examRepository).findByIdAndUserId(99L, ME);
    verifyNoInteractions(examScoreRepository);
  }

  @Test
  @DisplayName("이 시험에 예상 점수가 없는 과목이 하나라도 있으면 400 이고, 앞의 과목도 바뀌지 않는다")
  void enterActualScoresOfSubjectNotInExam() {
    List<ExamScore> scores = givenPastExam();

    // 99번은 이 시험에 없는 과목입니다. 앞의 3번이 맞아도 아무것도 바뀌지 않아야 합니다.
    assertError(
        () -> examService.enterActualScores(7L, result(actual("3", 80), actual("99", 50))),
        ErrorCode.VALIDATION_ERROR,
        "이 시험에 예상 점수가 없는 과목이에요");
    assertThat(scores).extracting(ExamScore::getActualScore).containsOnlyNulls();
  }

  @Test
  @DisplayName("같은 과목을 두 번 보내면 400 이고, 아무것도 바뀌지 않는다")
  void enterActualScoresWithDuplicateSubject() {
    List<ExamScore> scores = givenPastExam();

    assertError(
        () -> examService.enterActualScores(7L, result(actual("3", 80), actual("3", 90))),
        ErrorCode.VALIDATION_ERROR,
        "같은 과목이 두 번 들어 있어요");
    assertThat(scores).extracting(ExamScore::getActualScore).containsOnlyNulls();
  }

  @Test
  @DisplayName("내 시험인지 내 번호(userId)로 묻는다")
  void enterActualScoresAsksWithMyUserId() {
    givenPastExam();

    examService.enterActualScores(7L, result(actual("3", 80)));

    verify(examRepository).findByIdAndUserId(7L, ME);
    verify(examScoreRepository).findByUserIdAndExamIdOrderById(ME, 7L);
    // 다른 사람의 번호로는 묻지 않습니다.
    verify(examRepository, never()).findByIdAndUserId(7L, OTHER);
  }

  // ---- 시험을 목표에 잇기 (이슈 #228) ----

  // DB 에 이미 있는 내 목표. 4번은 점수형, 5번은 완주형입니다.
  private void givenMyGoals() {
    Goal score =
        new Goal(
            ME,
            GoalKind.SCORE,
            new Goal.Target("11월 학평 수학 3등급", LocalDate.of(2026, 11, 20), "5등급", null, null),
            new Goal.Woop("좋은 점", "대가", "걸림돌", "방법"),
            NOW);
    ReflectionTestUtils.setField(score, "id", 4L);
    Goal completion =
        new Goal(
            ME,
            GoalKind.COMPLETION,
            new Goal.Target("영단어장 1회독", LocalDate.of(2026, 11, 7), null, 300, "쪽"),
            new Goal.Woop("좋은 점", "대가", "걸림돌", "방법"),
            NOW);
    ReflectionTestUtils.setField(completion, "id", 5L);
    given(goalRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());
    given(goalRepository.findByIdAndUserId(4L, ME)).willReturn(Optional.of(score));
    given(goalRepository.findByIdAndUserId(5L, ME)).willReturn(Optional.of(completion));
  }

  @Test
  @DisplayName("시험을 등록할 때 내 점수형 목표 번호를 보내면 그 목표에 이어서 저장하고, 응답에 goalId 가 있다")
  void createLinksTheGoal() {
    givenMyGoals();

    ExamResponse response =
        examService.create(
            new ExamCreateRequest(
                "mock",
                "11월 학평",
                OCT_20,
                List.of(new ExamExpectedScoreRequest("3", new BigDecimal(60))),
                "4"));

    assertThat(response.goalId()).isEqualTo("4");
    ArgumentCaptor<Exam> saved = ArgumentCaptor.forClass(Exam.class);
    verify(examRepository).save(saved.capture());
    assertThat(saved.getValue().getGoalId()).isEqualTo(4L);
  }

  @Test
  @DisplayName("완주형 목표에는 이을 수 없어 400, 남의 목표 · 없는 목표는 404 이고 시험을 저장하지 않는다")
  void createRejectsBadGoals() {
    givenMyGoals();
    ExamExpectedScoreRequest math = new ExamExpectedScoreRequest("3", new BigDecimal(60));

    assertThatThrownBy(
            () ->
                examService.create(
                    new ExamCreateRequest("mock", "11월 학평", OCT_20, List.of(math), "5")))
        .isInstanceOf(ApiException.class)
        .hasMessage("시험은 점수형 목표에만 이을 수 있어요");
    assertThatThrownBy(
            () ->
                examService.create(
                    new ExamCreateRequest("mock", "11월 학평", OCT_20, List.of(math), "99")))
        .isInstanceOf(ApiException.class)
        .hasMessage("목표를 찾을 수 없어요")
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(examRepository, never()).save(any());
  }

  @Test
  @DisplayName("이미 있는 시험을 목표에 잇고(goalId), 다시 떼어 낸다(null)")
  void linkAndUnlink() {
    givenMyGoals();
    Exam exam = exam(7, "11월 학평", ExamType.MOCK, OCT_20);
    given(examRepository.findByIdAndUserId(7L, ME)).willReturn(Optional.of(exam));

    assertThat(examService.linkGoal(7L, new ExamGoalRequest("4")).goalId()).isEqualTo("4");
    assertThat(exam.getGoalId()).isEqualTo(4L);

    assertThat(examService.linkGoal(7L, new ExamGoalRequest(null)).goalId()).isNull();
    assertThat(exam.getGoalId()).isNull();
  }

  @Test
  @DisplayName("goalId 로 목록을 부르면 그 목표에 이어진 내 시험만 찾고, 남의 목표면 404 이다")
  void examsOfGoal() {
    givenMyGoals();
    Exam linked = exam(7, "11월 학평", ExamType.MOCK, OCT_20);
    linked.linkGoal(4L);
    given(examRepository.findByUserIdAndGoalIdOrderByExamDateDescIdDesc(ME, 4L))
        .willReturn(List.of(linked));

    assertThat(examService.getExams(4L))
        .extracting(ExamResponse::id, ExamResponse::goalId)
        .containsExactly(tuple("7", "4"));
    assertThatThrownBy(() -> examService.getExams(99L))
        .isInstanceOf(ApiException.class)
        .hasMessage("목표를 찾을 수 없어요");
  }
}
