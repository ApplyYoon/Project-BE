package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.school.dto.SchoolTodayResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 오늘 학교 정보(우리 반 시간표 · 급식 · 다가오는 학사일정)를 한 번에 돌려주는 로직입니다.
// 홈의 "오늘" 칸과 AI 코치 채팅이 씁니다. (2026-10-18 "AI 코치 채팅 중심" 방향)
//
// ■ 급식은 DB 에 저장하지 않고 서버 메모리에만 잠깐 둡니다.
//   새 테이블을 만들지 않으려고 이렇게 했습니다. (테이블 설계는 학생이 합니다 — CLAUDE.md)
//   학교 · 날짜마다 한 번 받으면 6시간 동안 다시 부르지 않습니다.
//   서버가 다시 켜지면 비어 있고, 그다음 처음 묻는 사람이 다시 받습니다.
//   NEIS 가 멈추면 받아 둔 것을 쓰고, 그것도 없으면 그날 급식은 빠집니다. (에러로 막지 않음)
// ■ 시간표도 급식과 같은 방식으로 메모리에 둡니다. 학년 · 반은 저장하지 않고 FE 가 요청마다 보냅니다.
//   (반을 저장하려면 새 칸이 필요해서, 칸 설계는 학생 몫으로 남겼습니다)
//   ⚠️ 서버에 NEIS 키가 없으면 NEIS 가 5교시까지만 줍니다. 쪽 넘기기나 교시로 나눠 받아도 같은 5줄이 와서
//      그때는 timetablePartial 을 true 로 알려 줍니다. (2026-10-18 확인, 키는 BE#287)
// ■ 학사일정은 SchoolEventService 가 DB 에 받아 둔 것을 읽기만 합니다.
//   여기서 학사일정을 새로 받지 않는 이유: 키가 없으면 수십 번 불러야 해서 "오늘" 화면이 느려집니다.
//   (학사일정은 예약 작업 school-events 와 학사일정 화면이 새로 받습니다)
@Service
@RequiredArgsConstructor
public class SchoolTodayService {

  // 급식을 받은 지 이만큼 지났으면 다시 받습니다. (급식표는 거의 안 바뀌어서 넉넉하게)
  static final Duration MEAL_STALE_AFTER = Duration.ofHours(6);
  // 학사일정은 오늘부터 이만큼 안의 것만 보여 줍니다.
  static final int EVENT_DAYS = 30;
  // 학사일정은 앞에서부터 이만큼까지만
  static final int MAX_EVENTS = 10;
  // 키가 없을 때 NEIS 가 주는 시간표 줄 수. 이만큼 왔으면 뒤가 잘렸을 수 있습니다.
  static final int TIMETABLE_ROWS_WITHOUT_KEY = 5;
  // 반 번호로 받는 가장 큰 값 (주소의 값은 누구나 고칠 수 있어서 범위를 확인합니다)
  static final int MAX_CLASS_NO = 30;

  private final CurrentUserProvider currentUserProvider;
  private final AccountRepository accountRepository;
  private final SchoolEventRepository schoolEventRepository;
  private final SchoolEventFetchRepository schoolEventFetchRepository;
  private final NeisClient neisClient;
  private final Clock clock;
  // 홈페이지 가정통신문 제목을 대본에 넣을 때 씁니다. (받아 둔 것만 읽음, SchoolNoticeService)
  private final SchoolNoticeService schoolNoticeService;

  // 메모리에 잠깐 둔 급식. 열쇠는 '학교번호|날짜' (예: '7530560|2026-10-19')
  private final Map<String, SavedMeals> savedMeals = new ConcurrentHashMap<>();
  // 메모리에 잠깐 둔 시간표. 열쇠는 '학교번호|날짜|학년|반' (예: '7530560|2026-10-19|2|1')
  private final Map<String, SavedTimetable> savedTimetables = new ConcurrentHashMap<>();
  // 메모리에 둔 교육청 코드. 열쇠는 학교 번호. (학사일정을 한 번도 안 받은 학교에서만 씁니다)
  private final Map<String, String> officeCodes = new ConcurrentHashMap<>();

  // 내 학교의 오늘 정보. 학교를 안 골랐으면 school 이 null 입니다.
  // grade · classNo 를 둘 다 주면 그 반의 오늘 시간표도 넣습니다. 둘 다 안 주면 시간표는 null 입니다.
  public SchoolTodayResponse getMyToday(Integer grade, Integer classNo) {
    checkClass(grade, classNo);
    Long userId = currentUserProvider.getCurrentUserId();
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    Optional<Account> account = accountRepository.findByUserId(userId);
    String schoolCode = account.map(Account::getSchoolCode).orElse(null);
    if (schoolCode == null || schoolCode.isBlank()) {
      return SchoolTodayResponse.noSchool(today);
    }

    List<SchoolTodayResponse.Meal> meals = new ArrayList<>();
    List<SchoolTodayResponse.Period> timetable = grade == null ? null : new ArrayList<>();
    Optional<String> officeCode = findOfficeCode(schoolCode);
    if (officeCode.isPresent()) {
      // 지난 날의 급식 · 시간표는 메모리에서 지웁니다. (메모리가 계속 늘지 않게)
      savedMeals.values().removeIf(saved -> saved.day().isBefore(today));
      savedTimetables.values().removeIf(saved -> saved.day().isBefore(today));
      meals.addAll(mealsOf(officeCode.get(), schoolCode, today, now));
      meals.addAll(mealsOf(officeCode.get(), schoolCode, today.plusDays(1), now));
      if (grade != null) {
        timetable.addAll(timetableOf(officeCode.get(), schoolCode, grade, classNo, today, now));
      }
    }
    // 키가 없는데 5줄이 꽉 차서 왔으면 6 · 7교시가 잘렸을 수 있습니다.
    boolean timetablePartial =
        timetable != null
            && !neisClient.hasApiKey()
            && timetable.size() >= TIMETABLE_ROWS_WITHOUT_KEY;

    List<SchoolTodayResponse.Event> events = new ArrayList<>();
    for (SchoolEvent event :
        schoolEventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
            schoolCode, today, today.plusDays(EVENT_DAYS))) {
      if (events.size() >= MAX_EVENTS) {
        break;
      }
      events.add(
          new SchoolTodayResponse.Event(
              event.getEventDate(),
              event.getName(),
              SchoolEventService.gradesOf(event.getGrades()),
              SchoolEventService.isExam(event.getName()),
              ChronoUnit.DAYS.between(today, event.getEventDate())));
    }
    return SchoolTodayResponse.of(
        today,
        schoolCode,
        account.get().getSchoolName(),
        timetable,
        timetablePartial,
        meals,
        events);
  }

  // 예약 작업 school-daily 가 쓰는 "오늘 · 내일 급식 미리 받기"입니다. 로그인한 학생 없이 학교 번호로만 부릅니다.
  // 받아 둔 지 6시간이 안 된 날은 NEIS 를 부르지 않습니다. (아침에 처음 여는 학생이 기다리지 않게)
  public void prefetchMeals(String schoolCode) {
    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    Optional<String> officeCode = findOfficeCode(schoolCode);
    if (officeCode.isEmpty()) {
      return;
    }
    savedMeals.values().removeIf(saved -> saved.day().isBefore(today));
    mealsOf(officeCode.get(), schoolCode, today, now);
    mealsOf(officeCode.get(), schoolCode, today.plusDays(1), now);
  }

  // AI 코치 채팅의 대본에 넣을 "오늘 학교" 줄들입니다. 학교를 안 골랐거나 아무것도 없으면 빈 목록입니다. (ChatService)
  // 시간표 · 급식 · 학사일정에 더해, 받아 둔 홈페이지 가정통신문 제목(최근 7일, 3개까지)도 붙입니다. (2026-10-18)
  // - 학교 이름은 넣지 않습니다. (외부 AI 에 개인정보를 보내지 않는 규칙, AiQuestion)
  // - 학년 · 반이 이상하면 채팅을 막지 않고 시간표만 뺍니다.
  public List<String> scriptLines(Integer grade, Integer classNo) {
    boolean hasClass =
        grade != null
            && classNo != null
            && grade >= 1
            && grade <= 3
            && classNo >= 1
            && classNo <= MAX_CLASS_NO;
    SchoolTodayResponse today = hasClass ? getMyToday(grade, classNo) : getMyToday(null, null);
    List<String> lines = linesOf(today, hasClass ? grade : null);
    // 학교 홈페이지의 최근 가정통신문 제목 (예약 작업이 받아 둔 것만. 대본을 만들 때 홈페이지에 가지 않음)
    if (today.school() != null) {
      lines.addAll(schoolNoticeService.scriptLines(today.school().code(), today.date()));
    }
    return lines;
  }

  // 오늘 학교 정보 → 대본 줄. 예:
  //   - 오늘 시간표: 1교시 미적분Ⅰ, 2교시 문학 (5교시까지만 알아)
  //   - 오늘 중식: 흑미밥, 나주곰탕, 배추김치
  //   - 다가오는 학사일정: 11월 2일 2학기 2차 정기시험(시험, 14일 남음)
  static List<String> linesOf(SchoolTodayResponse today, Integer grade) {
    List<String> lines = new ArrayList<>();
    if (today.school() == null) {
      return lines;
    }
    if (today.timetable() != null && !today.timetable().isEmpty()) {
      List<String> periods = new ArrayList<>();
      for (SchoolTodayResponse.Period period : today.timetable()) {
        periods.add(period.period() + "교시 " + cut(period.subject(), 20));
      }
      lines.add(
          "- 오늘 시간표: "
              + String.join(", ", periods)
              + (today.timetablePartial() ? " (5교시까지만 알아)" : ""));
    }
    for (SchoolTodayResponse.Meal meal : today.meals()) {
      // 대본에는 오늘 급식만 넣습니다. 메뉴는 앞에서부터 5개까지
      if (!meal.date().equals(today.date())) {
        continue;
      }
      List<String> dishes = new ArrayList<>();
      for (String dish : meal.dishes()) {
        if (dishes.size() >= 5) {
          break;
        }
        dishes.add(cut(dish, 20));
      }
      lines.add("- 오늘 " + meal.kind() + ": " + String.join(", ", dishes));
    }
    List<String> events = new ArrayList<>();
    for (SchoolTodayResponse.Event event : today.events()) {
      // 학년을 알면 우리 학년 행사만 (학년 구분이 없는 행사는 모두)
      boolean forMe = grade == null || event.grades().isEmpty() || event.grades().contains(grade);
      if (!forMe || events.size() >= 3) {
        continue;
      }
      String left = event.dDay() == 0 ? "오늘" : event.dDay() + "일 남음";
      events.add(
          event.date().getMonthValue()
              + "월 "
              + event.date().getDayOfMonth()
              + "일 "
              + cut(event.name(), 30)
              + "("
              + (event.exam() ? "시험, " : "")
              + left
              + ")");
    }
    if (!events.isEmpty()) {
      lines.add("- 다가오는 학사일정: " + String.join(" / ", events));
    }
    return lines;
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }

  // 학년 · 반은 둘 다 주거나 둘 다 안 줘야 하고, 고등학교 1~3학년 · 1~30반이어야 합니다.
  private static void checkClass(Integer grade, Integer classNo) {
    if (grade == null && classNo == null) {
      return;
    }
    if (grade == null || classNo == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "학년과 반은 같이 보내 주세요");
    }
    if (grade < 1 || grade > 3 || classNo < 1 || classNo > MAX_CLASS_NO) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "학년은 1~3, 반은 1~30 이어야 해요");
    }
  }

  // 교육청 코드: 학사일정을 받을 때 저장해 둔 것을 먼저 쓰고, 없으면 NEIS 에 한 번 묻고 메모리에 둡니다.
  private Optional<String> findOfficeCode(String schoolCode) {
    Optional<String> saved =
        schoolEventFetchRepository.findById(schoolCode).map(SchoolEventFetch::getOfficeCode);
    if (saved.isPresent()) {
      return saved;
    }
    String remembered = officeCodes.get(schoolCode);
    if (remembered != null) {
      return Optional.of(remembered);
    }
    Optional<String> found = neisClient.findOfficeCode(schoolCode);
    found.ifPresent(code -> officeCodes.put(schoolCode, code));
    return found;
  }

  // 그날의 급식. 메모리에 6시간이 안 된 것이 있으면 그것을, 아니면 NEIS 에서 새로 받습니다.
  private List<SchoolTodayResponse.Meal> mealsOf(
      String officeCode, String schoolCode, LocalDate day, Instant now) {
    String key = schoolCode + "|" + day;
    SavedMeals saved = savedMeals.get(key);
    if (saved == null || saved.fetchedAt().isBefore(now.minus(MEAL_STALE_AFTER))) {
      Optional<List<NeisClient.NeisMeal>> fetched =
          neisClient.fetchMeals(officeCode, schoolCode, day);
      if (fetched.isPresent()) {
        saved = new SavedMeals(day, now, fetched.get());
        savedMeals.put(key, saved);
      }
    }
    // NEIS 가 멈췄고 받아 둔 것도 없으면 그날 급식은 빠집니다.
    if (saved == null) {
      return List.of();
    }
    List<SchoolTodayResponse.Meal> meals = new ArrayList<>();
    for (NeisClient.NeisMeal meal : saved.meals()) {
      meals.add(new SchoolTodayResponse.Meal(meal.date(), meal.kind(), meal.dishes()));
    }
    return meals;
  }

  // 그날 한 반의 시간표. 급식과 같은 방식(메모리 6시간, NEIS 가 멈추면 받아 둔 것)입니다.
  private List<SchoolTodayResponse.Period> timetableOf(
      String officeCode, String schoolCode, int grade, int classNo, LocalDate day, Instant now) {
    String key = schoolCode + "|" + day + "|" + grade + "|" + classNo;
    SavedTimetable saved = savedTimetables.get(key);
    if (saved == null || saved.fetchedAt().isBefore(now.minus(MEAL_STALE_AFTER))) {
      Optional<List<NeisClient.NeisPeriod>> fetched =
          neisClient.fetchTimetable(officeCode, schoolCode, grade, classNo, day);
      if (fetched.isPresent()) {
        saved = new SavedTimetable(day, now, fetched.get());
        savedTimetables.put(key, saved);
      }
    }
    if (saved == null) {
      return List.of();
    }
    List<SchoolTodayResponse.Period> periods = new ArrayList<>();
    for (NeisClient.NeisPeriod period : saved.periods()) {
      periods.add(new SchoolTodayResponse.Period(period.period(), period.subject()));
    }
    return periods;
  }

  // 메모리에 둔 하루치 급식과 받은 시각
  private record SavedMeals(LocalDate day, Instant fetchedAt, List<NeisClient.NeisMeal> meals) {}

  // 메모리에 둔 하루치 한 반 시간표와 받은 시각
  private record SavedTimetable(
      LocalDate day, Instant fetchedAt, List<NeisClient.NeisPeriod> periods) {}
}
