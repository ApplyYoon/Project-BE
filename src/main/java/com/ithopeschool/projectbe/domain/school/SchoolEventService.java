package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.school.dto.SchoolEventsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 내 학교의 학사일정을 돌려주는 로직입니다. (이슈 #282)
//
// ■ 저장 방식: "처음 필요할 때 받아서 학교별로 저장 + 하루 1번 새로 받기"
//   - 화면이 물을 때(GET) 받아 둔 지 24시간이 지났으면 NEIS 에서 새로 받아 저장한 뒤 돌려줍니다.
//   - [새로고침](POST refresh)은 받아 둔 지 10분이 지났으면 새로 받습니다. (NEIS 를 마구 부르지 않게)
//   - 예약 작업(SchoolEventsJob)이 하루 1번, 우리 학생이 있는 학교만 새로 받습니다.
//   - NEIS 가 멈추면 받아 둔 일정을 그대로 돌려줍니다. (에러로 막지 않음)
// ■ 받는 기간
//   - 인증키가 있으면 이번 학년도 전체(3월 1일 ~ 다음 해 2월 말)를 한 번에 받습니다.
//   - 키가 없으면 NEIS 가 한 번에 5건만 줘서 한 주씩 나눠 받아야 하므로, 오늘부터 120일만 받습니다.
// ■ 시험 판정은 코드가 합니다. (isExam)
@Service
@RequiredArgsConstructor
public class SchoolEventService {

  // 화면이 물을 때 이만큼 지났으면 새로 받습니다.
  static final Duration STALE_AFTER = Duration.ofHours(24);
  // [새로고침] 은 이만큼 지났을 때만 NEIS 를 부릅니다.
  static final Duration REFRESH_AFTER = Duration.ofMinutes(10);
  // 예약 작업은 이만큼 지났으면 새로 받습니다. (하루 1번 부르니 조금 짧게)
  static final Duration JOB_REFRESH_AFTER = Duration.ofHours(20);
  // 키 없이 받을 때 오늘부터 며칠
  static final int DAYS_WITHOUT_KEY = 120;
  // 화면에 기본으로 돌려주는 기간 (오늘부터)
  static final int DEFAULT_DAYS = 120;
  // 이 글자가 이름에 들어가면 시험으로 봅니다. (정기시험 = 학교의 중간 · 기말고사)
  static final List<String> EXAM_KEYWORDS =
      List.of("정기시험", "중간고사", "기말고사", "지필", "학력평가", "모의평가", "모의고사");
  // 저장하지 않는 행사 (매주 있어서 일정 목록만 길어짐)
  static final List<String> SKIPPED_EVENTS = List.of("토요휴업일");

  private final CurrentUserProvider currentUserProvider;
  private final AccountRepository accountRepository;
  private final SchoolEventRepository schoolEventRepository;
  private final SchoolEventFetchRepository schoolEventFetchRepository;
  private final NeisClient neisClient;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  // 내 학교의 from ~ to 일정. 날짜를 안 주면 오늘부터 120일입니다. 학교를 안 골랐으면 school 이 null 입니다.
  public SchoolEventsResponse getMyEvents(LocalDate from, LocalDate to) {
    return myEvents(from, to, STALE_AFTER);
  }

  // [새로고침]: 10분이 지났으면 NEIS 에서 새로 받고, 오늘부터 120일 일정을 돌려줍니다.
  public SchoolEventsResponse refreshMyEvents() {
    return myEvents(null, null, REFRESH_AFTER);
  }

  private SchoolEventsResponse myEvents(LocalDate from, LocalDate to, Duration maxAge) {
    Long userId = currentUserProvider.getCurrentUserId();
    Optional<Account> account = accountRepository.findByUserId(userId);
    String schoolCode = account.map(Account::getSchoolCode).orElse(null);
    if (schoolCode == null || schoolCode.isBlank()) {
      return SchoolEventsResponse.noSchool();
    }
    LocalDate today = DayRule.dateOf(clock.instant());
    LocalDate start = from == null ? today : from;
    LocalDate end = to == null ? today.plusDays(DEFAULT_DAYS) : to;

    Optional<Instant> fetchedAt = ensureFresh(schoolCode, maxAge);
    List<SchoolEventsResponse.Event> events = new ArrayList<>();
    for (SchoolEvent event :
        schoolEventRepository.findBySchoolCodeAndEventDateBetweenOrderByEventDateAscIdAsc(
            schoolCode, start, end)) {
      events.add(
          new SchoolEventsResponse.Event(
              event.getEventDate(),
              event.getName(),
              event.getContent(),
              gradesOf(event.getGrades()),
              isExam(event.getName())));
    }
    return SchoolEventsResponse.of(
        schoolCode, account.get().getSchoolName(), fetchedAt.orElse(null), events);
  }

  // 받아 둔 지 maxAge 가 지났으면 NEIS 에서 새로 받습니다. 마지막으로 잘 받은 시각을 돌려줍니다. (한 번도 못 받았으면 빈 값)
  // 예약 작업(SchoolEventsJob)도 이 메서드를 씁니다.
  Optional<Instant> ensureFresh(String schoolCode, Duration maxAge) {
    Instant now = clock.instant();
    Optional<SchoolEventFetch> saved = schoolEventFetchRepository.findById(schoolCode);
    if (saved.isPresent() && saved.get().getFetchedAt().isAfter(now.minus(maxAge))) {
      return Optional.of(saved.get().getFetchedAt());
    }

    // 교육청 코드는 한 번 찾으면 저장해 둡니다.
    Optional<String> officeCode =
        saved.map(SchoolEventFetch::getOfficeCode).or(() -> neisClient.findOfficeCode(schoolCode));
    if (officeCode.isEmpty()) {
      return saved.map(SchoolEventFetch::getFetchedAt);
    }

    LocalDate today = DayRule.dateOf(now);
    LocalDate from = neisClient.hasApiKey() ? schoolYearStart(today) : today;
    LocalDate to =
        neisClient.hasApiKey()
            ? schoolYearStart(today).plusYears(1).minusDays(1)
            : today.plusDays(DAYS_WITHOUT_KEY);
    Optional<List<NeisClient.NeisEvent>> fetched =
        neisClient.fetchSchedule(officeCode.get(), schoolCode, from, to);
    if (fetched.isEmpty()) {
      // NEIS 가 멈췄거나 이상한 답. 받아 둔 것을 그대로 씁니다.
      return saved.map(SchoolEventFetch::getFetchedAt);
    }

    // 그 기간의 일정을 받은 것으로 통째로 바꾸고, 받은 시각을 남깁니다. (중간에 실패하면 하나도 안 바뀜)
    transactionTemplate.executeWithoutResult(
        status -> {
          schoolEventRepository.deleteRange(schoolCode, from, to);
          schoolEventRepository.flush();
          List<SchoolEvent> rows = new ArrayList<>();
          List<String> seen = new ArrayList<>();
          for (NeisClient.NeisEvent event : fetched.get()) {
            String key = event.date() + "|" + event.name();
            if (SKIPPED_EVENTS.contains(event.name()) || seen.contains(key)) {
              continue;
            }
            seen.add(key);
            rows.add(
                new SchoolEvent(
                    schoolCode,
                    event.date(),
                    cut(event.name(), 100),
                    event.content() == null ? null : cut(event.content(), 200),
                    joinGrades(event.grades())));
          }
          schoolEventRepository.saveAll(rows);
          SchoolEventFetch fetch =
              saved.orElseGet(() -> new SchoolEventFetch(schoolCode, officeCode.get(), now));
          fetch.markFetched(now);
          schoolEventFetchRepository.save(fetch);
        });
    return Optional.of(now);
  }

  // 그 날짜가 속한 학년도의 첫날(3월 1일). 1 · 2월은 앞 해의 학년도입니다.
  //   2026-10-17 → 2026-03-01   /   2027-02-10 → 2026-03-01
  static LocalDate schoolYearStart(LocalDate date) {
    int year = date.getMonthValue() >= 3 ? date.getYear() : date.getYear() - 1;
    return LocalDate.of(year, 3, 1);
  }

  // 행사 이름으로 시험인지 정합니다.
  //   '2학기 2차 정기시험' → true · '3학년 대학수학능력 6월 모의평가' → true · '3학년 직업기초능력평가' → false
  static boolean isExam(String name) {
    for (String keyword : EXAM_KEYWORDS) {
      if (name.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  // [1, 2] → '1,2'
  static String joinGrades(List<Integer> grades) {
    StringBuilder text = new StringBuilder();
    for (Integer grade : grades) {
      if (!text.isEmpty()) {
        text.append(',');
      }
      text.append(grade);
    }
    return text.toString();
  }

  // '1,2' → [1, 2] · '' → []
  static List<Integer> gradesOf(String text) {
    List<Integer> grades = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return grades;
    }
    for (String part : text.split(",")) {
      grades.add(Integer.parseInt(part.trim()));
    }
    return grades;
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }
}
