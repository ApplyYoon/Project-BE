package com.ithopeschool.projectbe.domain.care;

import com.ithopeschool.projectbe.domain.cardresult.CardResult;
import com.ithopeschool.projectbe.domain.cardresult.CardResultService;
import com.ithopeschool.projectbe.domain.cardresult.JudgedCardDay;
import com.ithopeschool.projectbe.domain.care.dto.CareStageResponse;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInRepository;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.psychtest.PsychScale;
import com.ithopeschool.projectbe.domain.psychtest.PsychScore;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.psychtest.PsychTestRepository;
import com.ithopeschool.projectbe.domain.restday.RestDay;
import com.ithopeschool.projectbe.domain.restday.RestDayRepository;
import com.ithopeschool.projectbe.domain.safety.LowMoodStreak;
import com.ithopeschool.projectbe.domain.safety.SafetyBrake;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.CareSettingRepository;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 하루 한 번 관리 단계(0 ~ 4)를 판정해 저장합니다. (이슈 #62, 최종 기획서 「5.4 관리 단계」)
//
// 판정 규칙은 CareStage.decide 에 있고, 여기서는 그 재료를 DB 에서 모으기만 합니다.
// FE 의 runDailyCareStage (src/lib/dailyCareRun.ts) 와 같은 재료입니다.
//   기준 단계   ← 의지력(카드 판정 + 심리검사) · 관리 선호(심리검사) · 시작한 지 며칠째(온보딩을 마친 날부터)
//   안전 조건   ← 보호 조치(SafetyService) · 최근 7일 체크인의 글 · 기분 (SafetyBrake)
//   회복 조건   ← 휴식·미실행만 이어진 날 · 부정 감정 태그만 이어진 날 · 꼭 해야 하는 카드를 놓쳤는지
//   재발 신호   ← 카드 하루치 판정 → RelapseSignal.judge
//   상한        ← 내가 정한 제재 최대 한도 (care_settings) · 시험 기간 (SafetyBrake)
//
// ■ 언제 판정하나요?
//   매일 새벽 예약 작업(care-stages)이 온보딩을 마친 모든 학생의 오늘 단계를 정해 둡니다.
//   작업이 안 돌았으면 앱이 오늘 단계를 처음 물을 때 정합니다. 하루에 한 줄만 저장하고, 이미 정했으면 그 줄을 돌려줍니다.
//
// ■ FE 와 다른 점: 카드 · 감정 기록은 "어제까지" 만 봅니다.
//   하루가 막 시작된 새벽에는 오늘 카드가 아직 판정 전입니다. 오늘을 넣으면 연속 일수가 늘 0 이 되고,
//   재발 신호의 "지난 판정 날부터" 규칙 때문에 어제 저녁에 놓친 카드가 영영 세어지지 않습니다.
//   그래서 오늘의 단계는 다 끝난 날(어제까지)로 정하고, 다음 판정은 이번 판정 날부터 새로 생긴 미실행을 봅니다.
//   (안전 조건은 오늘 쓴 글 · 기분까지 봅니다. 위기 표현은 하루라도 빨리 알아야 하기 때문입니다)
@Service
@RequiredArgsConstructor
public class CareStageService {

  // 연속 일수를 셀 때 거슬러 올라가는 최대 날 수 (회복 민감도가 최대 4일이라 넉넉하게, FE 와 같음)
  static final int STREAK_LOOKBACK_DAYS = 14;
  // 회복 모드 민감도(며칠 이어지면 회복 모드인지). 서버에는 아직 이 설정이 없어서 FE 의 처음 값(3일)을 씁니다.
  static final int RECOVERY_SENSITIVITY_DAYS = 3;
  // 안전 브레이크에 넘기는 최근 체크인 기간 (오늘 포함 7일, 통화의 위로 기간과 같음)
  static final int SAFETY_DAYS = 7;
  // 지난 기록을 한 번에 볼 수 있는 최대 일수 (석 달)
  static final int MAX_RANGE_DAYS = 92;
  // 부정 감정 태그 (회복 조건 "부정 감정 태그만 고른 날이 이어짐")
  static final Set<EmotionTag> NEGATIVE_EMOTIONS =
      EnumSet.of(EmotionTag.STUCK, EmotionTag.ANXIOUS, EmotionTag.TIRED, EmotionTag.ANNOYED);

  private final CurrentUserProvider currentUserProvider;
  private final DailyCareStageRepository dailyCareStageRepository;
  private final CardResultService cardResultService;
  private final RestDayRepository restDayRepository;
  private final CheckInRepository checkInRepository;
  private final ExamRepository examRepository;
  private final PsychTestRepository psychTestRepository;
  private final CareSettingRepository careSettingRepository;
  private final UserRepository userRepository;
  private final SafetyService safetyService;
  private final Clock clock;

  // 오늘(새벽 4시 기준)의 내 관리 단계. 아직 판정하지 않았으면 지금 판정해 저장합니다.
  @Transactional
  public CareStageResponse today() {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    Optional<DailyCareStage> saved =
        dailyCareStageRepository.findByUserIdAndStageDate(userId, today);
    if (saved.isPresent()) {
      return CareStageResponse.from(saved.get());
    }
    return CareStageResponse.from(dailyCareStageRepository.save(decide(userId, today)));
  }

  // 기간 안의 내 관리 단계 기록 (날짜 순서). 판정하지 않은 날은 빠져 있습니다.
  @Transactional(readOnly = true)
  public List<CareStageResponse> list(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (to.isBefore(from)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "to 는 from 과 같거나 뒤의 날짜로 보내 주세요");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "관리 단계 기록은 한 번에 " + MAX_RANGE_DAYS + "일까지 볼 수 있어요");
    }
    List<CareStageResponse> items = new ArrayList<>();
    for (DailyCareStage saved :
        dailyCareStageRepository.findByUserIdAndStageDateBetweenOrderByStageDateAsc(
            userId, from, to)) {
      items.add(CareStageResponse.from(saved));
    }
    return items;
  }

  // 예약 작업(care-stages)이 부릅니다. 온보딩을 마친 학생들의 번호입니다.
  @Transactional(readOnly = true)
  public List<Long> onboardedUserIds() {
    List<Long> userIds = new ArrayList<>();
    for (User user : userRepository.findByOnboardedAtIsNotNullOrderByIdAsc()) {
      userIds.add(user.getId());
    }
    return userIds;
  }

  // 예약 작업이 학생마다 부릅니다. 새로 판정했으면 true, 그날 것이 이미 있었으면 false 입니다.
  // userId 는 요청에서 받은 값이 아니라 위 onboardedUserIds 의 DB 목록입니다.
  @Transactional
  public boolean decideFor(Long userId, LocalDate today) {
    if (dailyCareStageRepository.findByUserIdAndStageDate(userId, today).isPresent()) {
      return false;
    }
    dailyCareStageRepository.save(decide(userId, today));
    return true;
  }

  // 재료를 모아 CareStage.decide 로 오늘의 단계를 정합니다. (저장은 부르는 쪽이 합니다)
  private DailyCareStage decide(Long userId, LocalDate today) {
    Instant now = clock.instant();
    LocalDate yesterday = today.minusDays(1);
    Optional<DailyCareStage> latest =
        dailyCareStageRepository.findFirstByUserIdAndStageDateBeforeOrderByStageDateDesc(
            userId, today);

    // 1) 카드 하루치 판정 (어제까지). 재발 신호가 기준선 앞의 기록까지 보므로 RelapseSignalService 와 같은 기간을 읽습니다.
    List<JudgedCardDay> days =
        cardResultService.getJudgedDays(
            userId, yesterday.minusDays(RelapseSignalService.LOOKBACK_DAYS - 1), yesterday);

    // 2) 재발 신호: 지난 판정 날부터 새로 생긴 미실행만 봅니다. 처음이면 어제까지 최근 7일
    LocalDate since =
        latest
            .map(DailyCareStage::getStageDate)
            .orElse(yesterday.minusDays(RelapseSignal.RECENT_DAYS - 1));
    RelapseSignal relapse = RelapseSignal.judge(days, yesterday, since);

    // 3) 기준 단계: 의지력(최근 14일 + 심리검사) · 관리 선호 · 시작한 지 며칠째
    int dayIndex = dayIndexOf(userId, today);
    Optional<Map<PsychScale, Double>> scores = psychScoresOf(userId);
    double willpower =
        Willpower.getWillpower(
            lastDays(days, yesterday, Willpower.DATA_PERIOD_DAYS),
            scores.map(CareStageService::testScoresOf).orElse(null));
    int baseStage =
        Willpower.getBaseStage(
            willpower, scores.map(CareStageService::carePreferenceOf).orElse(null), dayIndex);

    // 4) 안전 조건 · 시험 기간
    List<CheckIn> checkIns =
        checkInRepository.findWithEmotionsBetween(
            userId, today.minusDays(STREAK_LOOKBACK_DAYS), today);
    SafetyBrake brake = safetyBrakeOf(userId, checkIns, now, today);
    CareStage.Safety safety =
        new CareStage.Safety(
            safetyService.isActive(userId) || brake.stopWhip(),
            brake.recoveryMode() && !brake.stopWhip());

    // 5) 회복 조건 (어제까지)
    Set<LocalDate> restDates = new HashSet<>();
    for (RestDay rest :
        restDayRepository.findByUserIdAndRestDateBetweenOrderByRestDate(
            userId, yesterday.minusDays(STREAK_LOOKBACK_DAYS - 1), yesterday)) {
      restDates.add(rest.getRestDate());
    }
    CareStage.Recovery recovery =
        new CareStage.Recovery(
            countRestOrMissStreak(days, restDates, yesterday),
            countNegativeMoodStreak(emotionsByDate(checkIns), yesterday),
            relapse.reasons().contains(RelapseReason.ABSOLUTE_MISS),
            // 대화(통화·채팅)에서 힘들다는 표현은 아직 모으지 않아서 false 입니다. (FE 와 같음)
            false,
            RECOVERY_SENSITIVITY_DAYS);

    // 6) 내가 정한 제재 최대 한도. 설정을 바꾼 적이 없으면 처음 값(2)입니다.
    int maxLevel =
        careSettingRepository
            .findByUserId(userId)
            .map(setting -> setting.maxCareLevelOn(today))
            .orElse(CareSetting.DEFAULT_MAX_CARE_LEVEL);

    CareStage.Result result =
        CareStage.decide(
            new CareStage.Input(
                latest.map(DailyCareStage::getStage).orElse(null),
                baseStage,
                dayIndex,
                safety,
                recovery,
                relapse,
                maxLevel,
                brake.examPeriod()));
    return new DailyCareStage(userId, today, result, now);
  }

  // 관리를 시작한 지 며칠째인지 (0 = 첫날). 온보딩을 마친 날부터 셉니다. 아직 안 마쳤으면 0
  private int dayIndexOf(Long userId, LocalDate today) {
    Instant onboardedAt = userRepository.findById(userId).map(User::getOnboardedAt).orElse(null);
    if (onboardedAt == null) {
      return 0;
    }
    return (int) Math.max(0, ChronoUnit.DAYS.between(DayRule.dateOf(onboardedAt), today));
  }

  // 내 가장 최근 심리검사의 척도 점수. 안 했거나 답을 믿기 어려우면(PsychTest.isReliable) 빈 Optional 입니다.
  private Optional<Map<PsychScale, Double>> psychScoresOf(Long userId) {
    return psychTestRepository
        .findFirstByUserIdOrderByIdDesc(userId)
        .filter(PsychTest::isReliable)
        .map(test -> PsychScore.scoresOf(test.getAnswers()));
  }

  static Willpower.TestScores testScoresOf(Map<PsychScale, Double> scores) {
    return new Willpower.TestScores(
        scores.get(PsychScale.START_RESISTANCE),
        scores.get(PsychScale.DISTRACTION),
        scores.get(PsychScale.FAIL_SENSITIVITY));
  }

  // 관리 선호 척도의 높낮이 (FE 의 getScaleLevel(scores.carePreference))
  static Willpower.CarePreference carePreferenceOf(Map<PsychScale, Double> scores) {
    return switch (PsychScore.levelOf(scores.get(PsychScale.CARE_PREFERENCE))) {
      case HIGH -> Willpower.CarePreference.HIGH;
      case MID -> Willpower.CarePreference.MID;
      case LOW -> Willpower.CarePreference.LOW;
    };
  }

  // 최근 체크인의 글 · 기분과 시험 날짜로 안전 브레이크를 판정합니다. (CallService 의 careLevelFor 와 같은 재료)
  private SafetyBrake safetyBrakeOf(
      Long userId, List<CheckIn> checkIns, Instant now, LocalDate today) {
    LocalDate from = today.minusDays(SAFETY_DAYS - 1);
    List<String> texts = new ArrayList<>();
    List<LowMoodStreak.MoodRecord> moods = new ArrayList<>();
    for (CheckIn checkIn : checkIns) {
      if (checkIn.getCheckInDate().isBefore(from)) {
        continue;
      }
      texts.add(checkIn.getMissReasonNote());
      texts.add(checkIn.getPromiseIf());
      texts.add(checkIn.getPromiseThen());
      if (checkIn.getMood() != null) {
        moods.add(new LowMoodStreak.MoodRecord(checkIn.getCheckInDate(), checkIn.getMood()));
      }
    }
    List<LocalDate> examDates = new ArrayList<>();
    for (Exam exam : examRepository.findByUserIdOrderByExamDateDescIdDesc(userId)) {
      examDates.add(exam.getExamDate());
    }
    return SafetyBrake.check(now, texts, moods, examDates);
  }

  // lastDay 를 넣어 최근 count 일의 카드 하루치만 고릅니다.
  private static List<JudgedCardDay> lastDays(
      List<JudgedCardDay> days, LocalDate lastDay, int count) {
    LocalDate from = lastDay.minusDays(count - 1);
    List<JudgedCardDay> picked = new ArrayList<>();
    for (JudgedCardDay day : days) {
      if (!day.date().isBefore(from) && !day.date().isAfter(lastDay)) {
        picked.add(day);
      }
    }
    return picked;
  }

  // 날짜마다 고른 감정 태그 (같은 날 체크인이 여럿이면 합칩니다)
  private static Map<LocalDate, Set<EmotionTag>> emotionsByDate(List<CheckIn> checkIns) {
    Map<LocalDate, Set<EmotionTag>> emotions = new HashMap<>();
    for (CheckIn checkIn : checkIns) {
      emotions
          .computeIfAbsent(checkIn.getCheckInDate(), date -> EnumSet.noneOf(EmotionTag.class))
          .addAll(checkIn.getEmotions());
    }
    return emotions;
  }

  // lastDay 까지 "휴식이거나, 카드를 모두 못 한 날" 만 이어진 날 수. 카드가 없고 휴식도 아닌 날에서 멈춥니다.
  // (FE 의 countRestOrMissStreak 과 같은 규칙. 서버는 lastDay 에 어제를 넘깁니다)
  //
  //   10/17 미실행 · 10/16 휴식 · 10/15 실행          → 2
  //   10/17 카드 2개 중 1개 실행                      → 0
  static int countRestOrMissStreak(
      List<JudgedCardDay> days, Set<LocalDate> restDates, LocalDate lastDay) {
    int streak = 0;
    for (int back = 0; back < STREAK_LOOKBACK_DAYS; back++) {
      LocalDate date = lastDay.minusDays(back);
      if (restDates.contains(date)) {
        streak++;
        continue;
      }
      boolean hasCard = false;
      boolean allRestOrMiss = true;
      for (JudgedCardDay day : days) {
        if (day.date().equals(date)) {
          hasCard = true;
          if (day.result() != CardResult.REST && !day.result().isMiss()) {
            allRestOrMiss = false;
          }
        }
      }
      if (!hasCard || !allRestOrMiss) {
        break;
      }
      streak++;
    }
    return streak;
  }

  // lastDay 까지 "고른 감정 태그가 모두 부정 태그(막막함·불안함·피곤함·짜증남)" 인 날이 이어진 날 수.
  // 태그를 안 고른 날(건너뜀 · 체크인 없음)에서 멈춥니다. (FE 의 countNegativeMoodStreak 과 같은 규칙)
  //
  //   10/17 [불안함, 피곤함] · 10/16 [막막함] · 10/15 [평온함]   → 2
  //   10/17 태그 없음                                          → 0
  static int countNegativeMoodStreak(
      Map<LocalDate, Set<EmotionTag>> emotionsByDate, LocalDate lastDay) {
    int streak = 0;
    for (int back = 0; back < STREAK_LOOKBACK_DAYS; back++) {
      Set<EmotionTag> emotions = emotionsByDate.getOrDefault(lastDay.minusDays(back), Set.of());
      if (emotions.isEmpty() || !NEGATIVE_EMOTIONS.containsAll(emotions)) {
        break;
      }
      streak++;
    }
    return streak;
  }
}
