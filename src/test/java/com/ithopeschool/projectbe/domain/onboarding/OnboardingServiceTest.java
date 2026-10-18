package com.ithopeschool.projectbe.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingSaveRequest;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingUpdateRequest;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

// Service 단위 테스트: DB(Repository)를 가짜로 바꿔서 저장·고치기·처음 자기 인식 찾기 로직만 확인합니다.
// 가짜 DB 는 아래 rows 목록입니다. save 하면 목록 끝에 붙고, 조회하면 목록에서 꺼냅니다.
class OnboardingServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-17 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
  // 요청 JSON 글자를 요청 DTO 로 바꾸는 도구 (칸이 많아서 생성자 대신 JSON 으로 만듭니다)
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final OnboardingAnswerRepository onboardingAnswerRepository =
      mock(OnboardingAnswerRepository.class);
  private final SubjectRepository subjectRepository = mock(SubjectRepository.class);

  // 가짜 DB: 저장된 내 설문 줄들 (처음 것부터)
  private final List<OnboardingAnswer> rows = new ArrayList<>();

  private OnboardingService serviceAt(Instant now) {
    return new OnboardingService(
        currentUserProvider,
        onboardingAnswerRepository,
        subjectRepository,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private final OnboardingService service = serviceAt(NOW);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(onboardingAnswerRepository.save(any()))
        .willAnswer(
            invocation -> {
              OnboardingAnswer answer = invocation.getArgument(0);
              ReflectionTestUtils.setField(answer, "id", (long) rows.size() + 1);
              rows.add(answer);
              return answer;
            });
    given(onboardingAnswerRepository.findFirstByUserIdOrderByAnsweredAtDescIdDesc(USER_ID))
        .willAnswer(invocation -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.getLast()));
    given(onboardingAnswerRepository.findByUserIdOrderByAnsweredAtAscIdAsc(USER_ID))
        .willAnswer(invocation -> List.copyOf(rows));
    // 내 과목은 3번(국어)과 5번(수학)
    given(subjectRepository.findByUserIdOrderById(USER_ID))
        .willReturn(List.of(subject(3L, "국어"), subject(5L, "수학")));
  }

  private static Subject subject(Long id, String name) {
    Subject subject = new Subject(USER_ID, name, SubjectColor.RED);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
  }

  // 꼭 있어야 하는 네 칸에 more(JSON 의 나머지 칸)를 붙인 PUT 요청
  private static OnboardingSaveRequest put(String more) {
    String json =
        "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\","
            + "\"dailyAvailableMinutes\":180"
            + (more.isEmpty() ? "" : "," + more)
            + "}";
    return JSON.readValue(json, OnboardingSaveRequest.class);
  }

  private static OnboardingUpdateRequest patch(String json) {
    return JSON.readValue(json, OnboardingUpdateRequest.class);
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 저장(PUT) ----

  @Test
  @DisplayName("학년 · 목표 시험 · 시험 날짜 · 하루 공부 시간만 보내도 저장되고, 나머지는 null · 목록은 [] 이다")
  void savesOnlyRequired() {
    OnboardingResponse response = service.save(put(""));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getUserId()).isEqualTo(USER_ID);
    assertThat(rows.getFirst().getAnsweredAt()).isEqualTo(NOW);
    assertThat(response.id()).isEqualTo("1");
    assertThat(response.answeredAt()).isEqualTo(NOW.toEpochMilli());
    assertThat(response.grade()).isEqualTo("고2");
    assertThat(response.targetExamDate()).isEqualTo(LocalDate.of(2027, 11, 18));
    assertThat(response.chronotype()).isNull();
    assertThat(response.wakeUpTime()).isNull();
    assertThat(response.subjectLevels()).isEmpty();
    assertThat(response.pastFailureCauses()).isEmpty();
    assertThat(response.firstSelfAwareness())
        .isEqualTo(new OnboardingResponse.SelfAwareness(null, null, null, 180));
  }

  @Test
  @DisplayName("새 온보딩처럼 목표 시험 · 시험 날짜 없이 학년 · 하루 공부 시간 · 취침/기상 · 아침형/저녁형만 보내도 저장된다")
  void savesNewOnboardingWithoutExam() {
    OnboardingResponse response =
        service.save(
            JSON.readValue(
                "{\"grade\":\"고2\",\"targetExam\":\" \",\"dailyAvailableMinutes\":120,"
                    + "\"wakeUpTime\":\"07:00\",\"sleepTime\":\"23:30\",\"chronotype\":\"night\"}",
                OnboardingSaveRequest.class));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getTargetExam()).isNull();
    assertThat(rows.getFirst().getTargetExamDate()).isNull();
    assertThat(response.targetExam()).isNull();
    assertThat(response.sleepTime()).isEqualTo("23:30");
    assertThat(response.chronotype()).isEqualTo("night");
  }

  @Test
  @DisplayName("모든 항목을 보내면 DB 에는 이름(LIBRARY)으로, 응답에는 FE 글자(독서실·도서관)로 나간다")
  void savesEverything() {
    OnboardingResponse response =
        service.save(
            put(
                """
                "targetUniversity":"한국대","targetScore":"1등급",
                "wakeUpTime":"07:00","sleepTime":"23:30","schoolEndTime":"16:30","academyEndTime":"22:00",
                "studyPlace":"독서실·도서관",
                "subjectLevels":[{"subjectId":"5","score":85,"confidence":3},{"subjectId":"3","grade":2,"confidence":4}],
                "pastFailureCauses":["phone","weekend"],
                "chronotype":"night","maxFocusMinutes":50,"biggestDistraction":"폰·SNS",
                "motivationReason":"부모님께 효도","goalReward":"여행 가기"
                """));

    OnboardingAnswer saved = rows.getFirst();
    assertThat(saved.getStudyPlace()).isEqualTo(StudyPlace.LIBRARY);
    assertThat(saved.getBiggestDistraction()).isEqualTo(Distraction.PHONE);
    assertThat(saved.getChronotype()).isEqualTo(Chronotype.NIGHT);

    assertThat(response.studyPlace()).isEqualTo("독서실·도서관");
    assertThat(response.biggestDistraction()).isEqualTo("폰·SNS");
    assertThat(response.sleepTime()).isEqualTo("23:30");
    // 무너진 때는 FE 의 보기 순서로, 과목별 수준은 과목 번호 순서로 나갑니다.
    assertThat(response.pastFailureCauses()).containsExactly("weekend", "phone");
    assertThat(response.subjectLevels())
        .containsExactly(
            new OnboardingResponse.SubjectLevelResponse("3", 2, null, 4),
            new OnboardingResponse.SubjectLevelResponse("5", null, 85, 3));
    assertThat(response.firstSelfAwareness())
        .isEqualTo(new OnboardingResponse.SelfAwareness("night", 50, "폰·SNS", 180));
  }

  @Test
  @DisplayName("선택지 항목에 정해진 값이 아닌 것을 보내면 400 이고 아무것도 저장하지 않는다")
  void rejectsUnknownChoices() {
    assertError(
        () -> service.save(putWith("고4")),
        ErrorCode.VALIDATION_ERROR,
        "학년은 고1, 고2, 고3, N수, 대학생 중에서 골라 주세요");
    assertError(
        () -> service.save(put("\"studyPlace\":\"도서관\"")),
        ErrorCode.VALIDATION_ERROR,
        "공부 장소는 집, 학교, 학원, 독서실·도서관, 카페, 기타 중에서 골라 주세요");
    // 대문자(MORNING)는 FE 와 약속한 모양이 아닙니다.
    assertError(
        () -> service.save(put("\"chronotype\":\"MORNING\"")),
        ErrorCode.VALIDATION_ERROR,
        "아침형/저녁형은 morning, night 중에서 골라 주세요");
    assertError(
        () -> service.save(put("\"biggestDistraction\":\"게임\"")),
        ErrorCode.VALIDATION_ERROR,
        "가장 큰 방해 요소는 폰·SNS, 졸림, 잡생각, 친구·가족, 소음·주변 환경, 기타 중에서 골라 주세요");
    assertError(
        () -> service.save(put("\"pastFailureCauses\":[\"weekend\",\"sunday\"]")),
        ErrorCode.VALIDATION_ERROR,
        "계획이 무너진 때는 afterExam, weekend, lateAcademy, phone, tooMuchPlan 중에서 골라 주세요");
    assertError(
        () -> service.save(put("\"maxFocusMinutes\":45")),
        ErrorCode.VALIDATION_ERROR,
        "한 번에 집중 가능한 시간은 30, 50, 90, 120 중에서 골라 주세요");
    assertError(
        () -> service.save(putWithMinutes(90)),
        ErrorCode.VALIDATION_ERROR,
        "하루 공부 가능 시간은 60, 120, 180, 240, 300 중에서 골라 주세요");

    assertThat(rows).isEmpty();
  }

  private static OnboardingSaveRequest putWith(String grade) {
    return JSON.readValue(
        "{\"grade\":\""
            + grade
            + "\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180}",
        OnboardingSaveRequest.class);
  }

  private static OnboardingSaveRequest putWithMinutes(int minutes) {
    return JSON.readValue(
        "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":"
            + minutes
            + "}",
        OnboardingSaveRequest.class);
  }

  @Test
  @DisplayName("시험 날짜가 어제면 400, 오늘이면 저장된다")
  void examDateMustNotBePast() {
    assertError(
        () -> service.save(putWithDate("2026-10-16")),
        ErrorCode.VALIDATION_ERROR,
        "시험 날짜는 오늘이나 그 뒤로 골라 주세요");

    assertThat(service.save(putWithDate("2026-10-17")).targetExamDate())
        .isEqualTo(LocalDate.of(2026, 10, 17));
  }

  @Test
  @DisplayName("새벽 1시 30분(한국 시간)에는 아직 전날이라, 달력의 어제 날짜도 '오늘' 로 받아 준다 (새벽 4시 기준)")
  void examDateUsesFourAmRule() {
    // 2026-10-18 01:30 (한국 시간) = 2026-10-17T16:30Z → 하루의 기준으로는 아직 10월 17일
    OnboardingService lateNight = serviceAt(Instant.parse("2026-10-17T16:30:00Z"));

    assertThat(lateNight.save(putWithDate("2026-10-17")).targetExamDate())
        .isEqualTo(LocalDate.of(2026, 10, 17));
  }

  private static OnboardingSaveRequest putWithDate(String date) {
    return JSON.readValue(
        "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\""
            + date
            + "\",\"dailyAvailableMinutes\":180}",
        OnboardingSaveRequest.class);
  }

  @Test
  @DisplayName("과목별 수준: 남의 과목·없는 과목은 404, 같은 과목 두 번 · 등급과 점수를 둘 다 · 둘 다 없음은 400")
  void subjectLevelRules() {
    assertError(
        () ->
            service.save(
                put("\"subjectLevels\":[{\"subjectId\":\"99\",\"grade\":2,\"confidence\":4}]")),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    assertError(
        () ->
            service.save(
                put(
                    "\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2,\"confidence\":4},"
                        + "{\"subjectId\":\"3\",\"grade\":5,\"confidence\":1}]")),
        ErrorCode.VALIDATION_ERROR,
        "같은 과목의 수준을 두 번 보냈어요");
    assertError(
        () ->
            service.save(
                put(
                    "\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2,\"score\":85,\"confidence\":4}]")),
        ErrorCode.VALIDATION_ERROR,
        "과목마다 등급과 점수 중 하나만 보내 주세요");
    assertError(
        () -> service.save(put("\"subjectLevels\":[{\"subjectId\":\"3\",\"confidence\":4}]")),
        ErrorCode.VALIDATION_ERROR,
        "과목마다 등급과 점수 중 하나만 보내 주세요");

    assertThat(rows).isEmpty();
  }

  @Test
  @DisplayName("같은 '무너진 때' 를 두 번 보내면 하나로 저장된다")
  void duplicateCausesBecomeOne() {
    OnboardingResponse response = service.save(put("\"pastFailureCauses\":[\"phone\",\"phone\"]"));

    assertThat(response.pastFailureCauses()).containsExactly("phone");
  }

  @Test
  @DisplayName("빈 글자로 보낸 선택 항목은 '안 보냄' 으로 보고 비어 있는 채로 저장한다")
  void blankMeansNotAnswered() {
    OnboardingResponse response =
        service.save(put("\"studyPlace\":\"\",\"wakeUpTime\":\"\",\"motivationReason\":\"   \""));

    assertThat(response.studyPlace()).isNull();
    assertThat(response.wakeUpTime()).isNull();
    assertThat(response.motivationReason()).isNull();
  }

  // ---- 일부만 고치기(PATCH) ----

  @Test
  @DisplayName("일부만 고치면 바로 전 답을 옮겨 적고 보낸 항목만 바꿔 새 줄로 저장한다 (전 줄은 그대로)")
  void patchCopiesAndChangesOnlySent() {
    service.save(put("\"chronotype\":\"morning\",\"pastFailureCauses\":[\"weekend\"]"));

    OnboardingResponse response = service.update(patch("{\"chronotype\":\"night\"}"));

    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().getChronotype()).isEqualTo(Chronotype.MORNING);
    assertThat(response.id()).isEqualTo("2");
    assertThat(response.chronotype()).isEqualTo("night");
    // 보내지 않은 것은 바로 전 답 그대로입니다.
    assertThat(response.grade()).isEqualTo("고2");
    assertThat(response.pastFailureCauses()).containsExactly("weekend");
    // 처음 답한 자기 인식은 아침형 그대로 남습니다. (완료 조건)
    assertThat(response.firstSelfAwareness().chronotype()).isEqualTo("morning");
  }

  @Test
  @DisplayName("일부만 고칠 때 빈 목록 [] 을 보내면 '없음' 으로 바뀐다")
  void patchEmptyListClears() {
    service.save(put("\"pastFailureCauses\":[\"weekend\",\"phone\"]"));

    OnboardingResponse response = service.update(patch("{\"pastFailureCauses\":[]}"));

    assertThat(response.pastFailureCauses()).isEmpty();
  }

  @Test
  @DisplayName("설문을 아직 안 했는데 일부만 고치려 하면 404, 아무것도 안 보내면 400")
  void patchErrors() {
    assertError(
        () -> service.update(patch("{\"chronotype\":\"night\"}")),
        ErrorCode.NOT_FOUND,
        "아직 온보딩 설문을 하지 않았어요");

    service.save(put(""));
    assertError(
        () -> service.update(patch("{}")), ErrorCode.VALIDATION_ERROR, "바꿀 답을 하나 이상 보내 주세요");
    // 빈 글자뿐인 요청도 바꿀 것이 없는 요청입니다.
    assertError(
        () -> service.update(patch("{\"studyPlace\":\"\"}")),
        ErrorCode.VALIDATION_ERROR,
        "바꿀 답을 하나 이상 보내 주세요");
    assertThat(rows).hasSize(1);
  }

  // ---- 조회 · 처음 자기 인식 ----

  @Test
  @DisplayName("처음 자기 인식은 항목마다 '처음으로 답이 있는 줄' 에서 꺼낸다")
  void firstSelfAwarenessPerItem() {
    // 1번째: 꼭 받는 것만 (하루 180분)
    service.save(put(""));
    // 2번째: 나중에 "내 정보 더 알려주기" 에서 아침형 · 50분 · 폰 이라고 처음 답함
    service.update(
        patch(
            "{\"chronotype\":\"morning\",\"maxFocusMinutes\":50,\"biggestDistraction\":\"폰·SNS\"}"));
    // 3번째: 생각이 바뀌어서 저녁형 · 하루 120분으로 고침
    service.update(patch("{\"chronotype\":\"night\",\"dailyAvailableMinutes\":120}"));

    OnboardingResponse response = service.get();

    assertThat(response.chronotype()).isEqualTo("night");
    assertThat(response.dailyAvailableMinutes()).isEqualTo(120);
    assertThat(response.firstSelfAwareness())
        .isEqualTo(new OnboardingResponse.SelfAwareness("morning", 50, "폰·SNS", 180));
  }

  @Test
  @DisplayName("설문을 다시 처음부터(PUT) 해서 자기 인식을 비워도, 처음 답은 남아 있다")
  void putAgainKeepsFirstAnswers() {
    service.save(put("\"chronotype\":\"morning\""));
    service.save(put(""));

    OnboardingResponse response = service.get();

    assertThat(response.chronotype()).isNull();
    assertThat(response.firstSelfAwareness().chronotype()).isEqualTo("morning");
  }

  @Test
  @DisplayName("설문 뒤에 지운 과목의 수준은 응답에 나오지 않는다")
  void deletedSubjectIsHidden() {
    service.save(
        put(
            "\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2,\"confidence\":4},"
                + "{\"subjectId\":\"5\",\"score\":85,\"confidence\":3}]"));
    // 5번(수학) 과목을 지웠습니다.
    given(subjectRepository.findByUserIdOrderById(USER_ID)).willReturn(List.of(subject(3L, "국어")));

    assertThat(service.get().subjectLevels())
        .extracting(OnboardingResponse.SubjectLevelResponse::subjectId)
        .containsExactly("3");
  }

  @Test
  @DisplayName("설문을 아직 안 했으면 조회는 404")
  void getBeforeSurvey() {
    assertError(service::get, ErrorCode.NOT_FOUND, "아직 온보딩 설문을 하지 않았어요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 셋 다 401 이고 아무것도 저장하지 않는다")
  void requiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> service.save(put("")), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> service.update(patch("{\"chronotype\":\"night\"}")),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
    assertError(service::get, ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertThat(rows).isEmpty();
  }

  // ---- 수면 보호 최소 6시간 (이슈 #223) ----

  @Test
  @DisplayName("취침 ~ 기상이 6시간보다 짧으면 400 이고 저장하지 않는다 (01:00 → 06:00 은 5시간), 6시간이면 저장된다")
  void sleepShorterThanSixHoursIsRejected() {
    assertError(
        () -> service.save(put("\"sleepTime\":\"01:00\",\"wakeUpTime\":\"06:00\"")),
        ErrorCode.VALIDATION_ERROR,
        "수면 보호 시간은 6시간 이상이어야 해요");
    assertThat(rows).isEmpty();

    assertThat(service.save(put("\"sleepTime\":\"00:00\",\"wakeUpTime\":\"06:00\"")).sleepTime())
        .isEqualTo("00:00");
  }

  @Test
  @DisplayName("PATCH 로 기상만 당겨서 6시간보다 짧아지면 400, 예전에 짧게 적어 둔 사람이 다른 답만 고치는 것은 된다")
  void sleepLengthOnPatch() {
    OnboardingAnswer.Values old = new OnboardingAnswer.Values();
    old.grade = Grade.HIGH_2;
    old.dailyAvailableMinutes = 180;
    old.sleepTime = LocalTime.of(0, 30);
    old.wakeUpTime = LocalTime.of(6, 20);
    rows.add(new OnboardingAnswer(USER_ID, Instant.parse("2026-10-01T00:00:00Z"), old));

    // 다른 답만 고치면 예전 수면 시간(5시간 50분)이 그대로여도 저장됩니다.
    assertThat(service.update(patch("{\"chronotype\":\"night\"}")).chronotype()).isEqualTo("night");
    // 기상 시각을 보냈는데 여전히 6시간이 안 되면 막습니다.
    assertError(
        () -> service.update(patch("{\"wakeUpTime\":\"06:10\"}")),
        ErrorCode.VALIDATION_ERROR,
        "수면 보호 시간은 6시간 이상이어야 해요");
    assertThat(service.update(patch("{\"wakeUpTime\":\"07:00\"}")).wakeUpTime()).isEqualTo("07:00");
  }
}
